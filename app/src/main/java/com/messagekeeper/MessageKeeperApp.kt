package com.messagekeeper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import com.messagekeeper.data.backup.BackupManager
import com.messagekeeper.data.db.AppDatabase
import com.messagekeeper.data.db.entity.SoundEffects
import com.messagekeeper.data.prefs.RetentionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessageKeeperApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var prefs: SharedPreferences
        private set

    /**
     * 应用级后台作用域：只用于备份 / 自动清理这类低优先级任务，
     * 独立 SupervisorJob 保证单个任务失败不会影响后续任务，也绝不阻塞启动。
     */
    private val appBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== App onCreate 开始 ===")
        instance = this

        try {
            // Fw 保活框架初始化：必须在最前面同步调用，以确保 config 初始化，
            // 否则系统自动拉起 FwAccessibilityService 等组件时会报 lateinit 未初始化异常。
            com.service.framework.Fw.init(this)
            Log.d(TAG, "Fw 守护框架初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "Fw 初始化异常", e)
        }

        try {
            prefs = getSharedPreferences("message_keeper_prefs", MODE_PRIVATE)
            Log.d(TAG, "SharedPreferences 初始化成功")
            loadThemeMode()
        } catch (e: Exception) {
            Log.e(TAG, "SharedPreferences 初始化失败", e)
            throw e
        }

        try {
            database = AppDatabase.getInstance(this)
            Log.d(TAG, "数据库初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "数据库初始化失败", e)
            throw e
        }

        try {
            createNotificationChannels()
            Log.d(TAG, "通知渠道创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "通知渠道创建失败", e)
        }

        // 初始化睡眠模式管理器（恢复上次的睡眠状态）
        try {
            com.messagekeeper.service.SleepModeManager.init(this)
            Log.d(TAG, "睡眠模式管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "睡眠模式管理器初始化失败", e)
        }

        // 全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "=== 未捕获异常！线程: ${thread.name} ===")
            Log.e(TAG, "异常信息: ${throwable.message}")
            Log.e(TAG, "异常类型: ${throwable.javaClass.simpleName}")
            for ((i, element) in throwable.stackTrace.take(20).withIndex()) {
                Log.e(TAG, "  at $element")
            }
            throwable.cause?.let {
                Log.e(TAG, "Caused by: ${it.message}")
                for ((i, element) in it.stackTrace.take(10).withIndex()) {
                    Log.e(TAG, "  at $element")
                }
            }
            
            // Show custom closable toast for severe error
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                com.messagekeeper.ui.components.Sonner.error("致命错误: ${throwable.message ?: "未知异常"}")
            }
            
            // Let the toast display for a short moment before crash
            try { Thread.sleep(2000) } catch (ignored: Exception) {}
            
            // Let the default handler take over
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        setupLogcatFile()

        // ==================== 备份 / 自动清理（全部异步 + 吞异常，绝不影响启动） ====================
        // ⚠️ 保活框架会让本 App 出现多个进程（主进程 + :daemon 等），只有主进程可以执行备份/清理：
        //    两个进程同时导出备份 + 删历史，会产生 CursorWindow 竞态（实测备份报
        //    "Couldn't read row ... from CursorWindow"），也可能让备份错过清理前的完整数据。
        if (!isMainProcess()) {
            Log.d(TAG, "当前不是主进程，跳过备份 / 自动清理任务")
            Log.d(TAG, "=== App onCreate 完成 ===")
            return
        }

        try {
            BackupManager.init(this)
            RetentionPreferences.init(this)
            com.messagekeeper.data.prefs.PinnedConversationsManager.init(this)
            Log.d(TAG, "备份管理器 / 消息保留策略 / 置顶持久化初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "备份管理器 / 消息保留策略初始化失败", e)
        }

        try {
            // 顺序很重要：**先生成当天的全量备份，再执行 30 天历史清理**。
            // 这样清理前的完整数据一定在备份里，用户随时可以在「历史备份」页回退。
            appBackgroundScope.launch {
                try {
                    BackupManager.ensureDailyBackupIfDue()
                } catch (e: Exception) {
                    Log.e(TAG, "启动时每日自动备份异常", e)
                }
                try {
                    BackupManager.runRetentionIfDue()
                } catch (e: Exception) {
                    Log.e(TAG, "启动时历史消息自动清理异常", e)
                }
            }
            Log.d(TAG, "启动时每日自动备份 / 自动清理任务已提交到后台")
        } catch (e: Exception) {
            Log.e(TAG, "提交每日自动备份 / 自动清理任务失败", e)
        }

        Log.d(TAG, "=== App onCreate 完成 ===")
    }

    /** 是否运行在主进程（保活框架的 :daemon 等子进程返回 false） */
    private fun isMainProcess(): Boolean {
        val processName = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.app.Application.getProcessName()
            } else {
                java.io.File("/proc/self/cmdline").readText().trim('\u0000', ' ')
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取当前进程名失败，按主进程处理", e)
            null
        } ?: return true
        return processName == packageName
    }

    private fun setupLogcatFile() {
        try {
            val logDir = java.io.File(getExternalFilesDir(null), "logs")
            if (!logDir.exists()) logDir.mkdirs()
            
            // 轮转日志，保留最近5次
            // Keep the current session and two prior startup logs; each session rotates at 256 KB.
            for (i in 5 downTo 4) {
                java.io.File(logDir, "log_start_$i.txt").delete()
            }
            for (i in 3 downTo 2) {
                val oldFile = java.io.File(logDir, "log_start_${i-1}.txt")
                val newFile = java.io.File(logDir, "log_start_$i.txt")
                if (newFile.exists()) newFile.delete()
                if (oldFile.exists()) oldFile.renameTo(newFile)
            }
            val currentLogFile = java.io.File(logDir, "log_start_1.txt")
            if (currentLogFile.exists()) currentLogFile.delete()
            
            // 启动独立线程收集 logcat 输出到文件
            Thread {
                try {
                    Runtime.getRuntime().exec(arrayOf("logcat", "-f", currentLogFile.absolutePath, "-r", "256", "-n", "1", "*:W", "--pid=${android.os.Process.myPid()}"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "设置日志文件失败", e)
        }
    }

    val isFirstLaunch: Boolean
        get() = prefs.getBoolean("first_launch", true)

    fun markFirstLaunchDone() {
        prefs.edit().putBoolean("first_launch", false).apply()
    }

    /** 恢复主题模式 */
    fun loadThemeMode() {
        val saved = prefs.getString("theme_mode", "system")
        com.messagekeeper.ui.theme.ThemeManager.mode = when (saved) {
            "light" -> com.messagekeeper.ui.theme.ThemeMode.LIGHT
            "dark" -> com.messagekeeper.ui.theme.ThemeMode.DARK
            else -> com.messagekeeper.ui.theme.ThemeMode.SYSTEM
        }
    }

    /** 保存主题模式 */
    fun saveThemeMode(mode: com.messagekeeper.ui.theme.ThemeMode) {
        val key = when (mode) {
            com.messagekeeper.ui.theme.ThemeMode.LIGHT -> "light"
            com.messagekeeper.ui.theme.ThemeMode.DARK -> "dark"
            else -> "system"
        }
        prefs.edit().putString("theme_mode", key).apply()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // 前台服务通知渠道
        val foregroundServiceChannel = NotificationChannel(
            CHANNEL_FOREGROUND_SERVICE,
            "消息监听服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台消息监听服务的运行状态"
        }
        notificationManager.createNotificationChannel(foregroundServiceChannel)
        Log.d(TAG, "前台服务通知渠道已创建")

        // 为每种声音创建独立通知渠道
        var successCount = 0
        var failCount = 0
        for (sound in SoundEffects.sounds) {
            try {
                val channelId = getChannelIdForSound(sound.name)
                when (sound.name) {
                    "silent" -> {
                        val channel = NotificationChannel(
                            channelId,
                            "特别提醒（${sound.displayName}）",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = "静音特别提醒"
                            enableVibration(false)
                            setSound(null, null)
                        }
                        notificationManager.createNotificationChannel(channel)
                        Log.d(TAG, "静音渠道已创建: $channelId")
                    }
                    "default" -> {
                        val channel = NotificationChannel(
                            channelId,
                            "特别提醒（${sound.displayName}）",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "特别提醒通知（默认声音）"
                            enableVibration(true)
                        }
                        notificationManager.createNotificationChannel(channel)
                        Log.d(TAG, "默认渠道已创建: $channelId")
                    }
                    else -> {
                        val resId = SoundEffects.getRawResId(sound.name)
                        if (resId != null && resId != 0) {
                            val soundUri = Uri.parse(
                                "android.resource://$packageName/raw/${sound.name}"
                            )
                            val audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()

                            val channel = NotificationChannel(
                                channelId,
                                "特别提醒（${sound.displayName}）",
                                NotificationManager.IMPORTANCE_HIGH
                            )
                            channel.setSound(soundUri, audioAttributes)
                            channel.enableVibration(true)
                            channel.enableLights(true)
                            notificationManager.createNotificationChannel(channel)
                            Log.d(TAG, "自定义声音渠道已创建: $channelId (resId=$resId)")
                        } else {
                            Log.w(TAG, "跳过声音 ${sound.name}: 资源不存在")
                        }
                    }
                }
                successCount++
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "创建通知渠道失败: ${sound.name}", e)
            }
        }
        Log.d(TAG, "通知渠道创建完成: 成功=$successCount, 失败=$failCount")
    }

    companion object {
        private const val TAG = "MessageKeeperApp"
        const val CHANNEL_FOREGROUND_SERVICE = "channel_foreground_service"

        fun getChannelIdForSound(soundName: String): String {
            return "channel_special_alert_$soundName"
        }

        lateinit var instance: MessageKeeperApp
            private set
    }
}
