package com.messagekeeper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.media.SoundPool
import android.content.ComponentName
import com.messagekeeper.data.parser.NotificationParser
import com.messagekeeper.data.repository.MessageRepository
import com.messagekeeper.notification.CustomSoundManager
import com.messagekeeper.notification.SpecialAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
class MessageNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MsgNotificationListener"
        const val ACTION_SYNC_NOTIFICATIONS = "com.messagekeeper.SYNC_NOTIFICATIONS"
    }

    private lateinit var soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: MessageRepository by lazy {
        Log.d(TAG, "Repository 懒加载初始化")
        try {
            MessageRepository.getInstance().also {
                Log.d(TAG, "Repository 初始化成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Repository 初始化失败", e)
            throw e
        }
    }

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SYNC_NOTIFICATIONS) {
                Log.d(TAG, "收到同步通知列表的广播")
                syncActiveNotifications()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== 服务创建 ===")
        
        // 1. 初始化 SoundPool
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
            
        // 2. 预加载所有自定义音频文件
        val customSounds = CustomSoundManager.getCustomSounds(this)
        for (sound in customSounds) {
            soundMap[sound.id] = soundPool.load(sound.filePath, 1)
        }
        
        val filter = IntentFilter(ACTION_SYNC_NOTIFICATIONS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(syncReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(syncReceiver)
        soundPool.release()
        scope.cancel()
        Log.d(TAG, "=== 服务销毁 ===")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "收到通知: pkg=${sbn.packageName}, key=${sbn.key}, postTime=${sbn.postTime}")

        if (!NotificationParser.isSupported(this, sbn.packageName)) {
            Log.d(TAG, "不支持的平台: ${sbn.packageName}")
            return
        }

        val parsed = try {
            NotificationParser.parse(this, sbn)
        } catch (e: Exception) {
            Log.e(TAG, "解析通知异常", e)
            null
        }

        if (parsed == null) {
            Log.w(TAG, "通知解析结果为空")
            return
        }

        Log.d(TAG, "解析结果: platform=${parsed.platform}, sender=${parsed.senderName}, recall=${parsed.isRecall}")

        // 获取 WakeLock，防止在息屏状态（Doze模式）下系统休眠导致协程挂起，从而发生吞消息的情况
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MessageKeeper::NotificationProcessLock")
        wakeLock.acquire(10_000L) // 最多持有 10 秒

        scope.launch(Dispatchers.IO) {
            try {
                if (parsed.isRecall) {
                    Log.d(TAG, "处理撤回通知: conv=${parsed.conversationName}, sender=${parsed.senderName}")
                    repository.handleRecallNotification(
                        platform = parsed.platform,
                        conversationName = parsed.conversationName,
                        senderName = parsed.senderName,
                        timestamp = parsed.timestamp,
                        notificationId = parsed.notificationId
                    )
                    Log.d(TAG, "撤回处理完成")
                } else {
                    if (parsed.senderName.contains("#") || parsed.conversationName.contains("#")) {
                        Log.d(TAG, "跳过联系人包含#的消息: sender=${parsed.senderName}, conv=${parsed.conversationName}")
                        return@launch
                    }
                    Log.d(TAG, "保存消息: conv=${parsed.conversationName}, sender=${parsed.senderName}: ${parsed.content.take(50)}")
                    val saved = repository.saveIncomingMessage(
                        platform = parsed.platform,
                        conversationName = parsed.conversationName,
                        senderName = parsed.senderName,
                        content = parsed.content,
                        timestamp = parsed.timestamp,
                        notificationId = parsed.notificationId
                    )

                    // Notice that in the user's code, they changed `val isNew` to `val saved`, and `saved.isNew`.
                    // So I must preserve this `saved` logic!
                    if (saved.isNew) {
                        Log.d(TAG, "新消息保存成功，发送者=${saved.senderName}，准备触发提醒")

                        // 检查特别提醒
                        try {
                            val matchingAlert = repository.findMatchingAlert(
                                platform = parsed.platform,
                                senderName = saved.senderName,
                                conversationTitle = parsed.conversationName
                            )
                            if (matchingAlert != null) {
                                Log.d(TAG, "触发特别提醒: ${saved.senderName}, 声音=${matchingAlert.sound_name}")

                                // 取消系统原有的通知（例如微信原生的通知），防止它顶掉我们的特别提醒
                                try {
                                    cancelNotification(sbn.key)
                                } catch (e: Exception) {
                                    Log.w(TAG, "取消原通知失败", e)
                                }

                                // ★★★ 睡眠模式：特别关心的人发消息 → 触发闹钟铃声 ★★★
                                if (SleepModeManager.isSleepMode.value) {
                                    Log.d(TAG, "💤 睡眠模式下收到特别关心的人消息，触发闹钟: ${saved.senderName}")
                                    SleepModeManager.triggerAlarm(this@MessageNotificationListener, saved.senderName)
                                }

                                SpecialAlertManager.sendAlert(
                                    context = this@MessageNotificationListener,
                                    platform = parsed.platform,
                                    senderName = saved.senderName,
                                    content = saved.content,
                                    soundName = matchingAlert.sound_name
                                )
                                Log.d(TAG, "特别提醒通知已发送")
                                
                                // 直接播放 SoundPool（极快，不阻塞）— 非睡眠模式才播放普通提示音
                                if (!SleepModeManager.isSleepMode.value && matchingAlert.sound_name.startsWith("custom_")) {
                                    val soundId = soundMap[matchingAlert.sound_name]
                                    if (soundId != null) {
                                        soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "特别提醒处理失败", e)
                        }
                    } else {
                        Log.d(TAG, "由于是重复消息，跳过提醒和推送")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== 处理通知时崩溃 ===")
                Log.e(TAG, "异常: ${e.javaClass.simpleName}: ${e.message}")
                for (element in e.stackTrace.take(15)) {
                    Log.e(TAG, "  at $element")
                }
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "通知移除: key=${sbn.key}")
    }

    override fun onListenerConnected() {
        Log.d(TAG, "=== 通知监听服务已连接 ===")
        // 启动心跳检测闹钟，周期性确保服务存活
        HeartbeatReceiver.scheduleHeartbeat(this)
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "=== 通知监听服务已断开，执行多重复活策略 ===")
        
        // 策略 1: 立即尝试 requestRebind
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    requestRebind(ComponentName(this, MessageNotificationListener::class.java))
                    Log.d(TAG, "已发送 requestRebind")
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind 失败", e)
            }
        }, 3000)

        // 策略 2: 10秒后通过精确闹钟再次触发复活（Doze 模式下仍生效）
        HeartbeatReceiver.scheduleHeartbeat(this)
    }

    private fun syncActiveNotifications() {
        try {
            val notifications = activeNotifications
            if (notifications != null) {
                Log.d(TAG, "开始同步现有的 ${notifications.size} 条通知")

                var processed = 0
                for (sbn in notifications) {
                    val pkg = sbn.packageName
                    if (NotificationParser.isSupported(this, pkg)) {
                        processed++
                        onNotificationPosted(sbn)
                    }
                }
                Log.d(TAG, "同步完成，可处理消息 $processed 条")
            } else {
                Log.d(TAG, "当前没有现有通知可以同步")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步现有通知失败", e)
        }
    }
}
