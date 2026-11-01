package com.messagekeeper.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.messagekeeper.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class SleepScheduleConfig(
    val enabled: Boolean = false,
    val startHour: Int = 23,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0
) {
    fun formatRange(): String = String.format("%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
}

/**
 * 睡眠模式管理器
 * - 用户点击 "我睡觉啦" 后进入睡眠模式
 * - 支持定时睡眠计划（到点自动开启 / 自动关闭，并发送系统通知提醒）
 * - 在睡眠模式下，如果特别关心的人发消息，会播放系统闹钟铃声持续响铃
 * - 用户点击 "我醒了" 后退出睡眠模式，停止响铃
 */
object SleepModeManager {

    private const val TAG = "SleepModeManager"
    private const val PREFS_NAME = "sleep_mode_prefs"
    private const val KEY_SLEEP_MODE = "is_sleep_mode"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_START_HOUR = "schedule_start_hour"
    private const val KEY_START_MINUTE = "schedule_start_minute"
    private const val KEY_END_HOUR = "schedule_end_hour"
    private const val KEY_END_MINUTE = "schedule_end_minute"

    const val ACTION_SLEEP_SCHEDULE_START = "com.messagekeeper.SLEEP_SCHEDULE_START"
    const val ACTION_SLEEP_SCHEDULE_END = "com.messagekeeper.SLEEP_SCHEDULE_END"

    private const val REQUEST_CODE_START = 8801
    private const val REQUEST_CODE_END = 8802
    private const val CHANNEL_SLEEP_MODE = "channel_sleep_mode_status"
    private const val NOTIFICATION_ID_SLEEP_STATUS = 8800

    private val _isSleepMode = MutableStateFlow(false)
    val isSleepMode: StateFlow<Boolean> = _isSleepMode.asStateFlow()

    private val _scheduleConfig = MutableStateFlow(SleepScheduleConfig())
    val scheduleConfig: StateFlow<SleepScheduleConfig> = _scheduleConfig.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /**
     * 初始化（从 SharedPreferences 恢复状态与定时配置）
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getBoolean(KEY_SLEEP_MODE, false)
        _isSleepMode.value = saved

        val config = SleepScheduleConfig(
            enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
            startHour = prefs.getInt(KEY_START_HOUR, 23),
            startMinute = prefs.getInt(KEY_START_MINUTE, 0),
            endHour = prefs.getInt(KEY_END_HOUR, 7),
            endMinute = prefs.getInt(KEY_END_MINUTE, 0)
        )
        _scheduleConfig.value = config
        createStatusNotificationChannel(context)

        if (config.enabled) {
            scheduleAlarms(context, config)
        }
        Log.d(TAG, "初始化睡眠模式状态: $saved, 定时配置: $config")
    }

    /**
     * 更新定时睡眠设置
     */
    fun updateSchedule(
        context: Context,
        enabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        val config = SleepScheduleConfig(
            enabled = enabled,
            startHour = startHour.coerceIn(0, 23),
            startMinute = startMinute.coerceIn(0, 59),
            endHour = endHour.coerceIn(0, 23),
            endMinute = endMinute.coerceIn(0, 59)
        )
        _scheduleConfig.value = config
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCHEDULE_ENABLED, config.enabled)
            .putInt(KEY_START_HOUR, config.startHour)
            .putInt(KEY_START_MINUTE, config.startMinute)
            .putInt(KEY_END_HOUR, config.endHour)
            .putInt(KEY_END_MINUTE, config.endMinute)
            .apply()

        if (config.enabled) {
            scheduleAlarms(context, config)
            // 如果当前时间已经在定时睡眠区间内且尚未开启，立即开启
            if (isCurrentlyInWindow(config) && !_isSleepMode.value) {
                enterSleepMode(context, sendNotification = true, isScheduled = true)
            }
        } else {
            cancelScheduledAlarms(context)
        }
    }

    /**
     * 判断当前时刻是否处于定时睡眠时间段内
     */
    fun isCurrentlyInWindow(config: SleepScheduleConfig): Boolean {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = config.startHour * 60 + config.startMinute
        val endMinutes = config.endHour * 60 + config.endMinute
        return if (startMinutes == endMinutes) {
            false
        } else if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // 跨午夜（例如 23:00 - 07:00）
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    /**
     * 调度下一次定时开启和定时关闭闹钟
     */
    fun scheduleAlarms(context: Context, config: SleepScheduleConfig = _scheduleConfig.value) {
        if (!config.enabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nextStartMs = computeNextTriggerMillis(config.startHour, config.startMinute)
        val nextEndMs = computeNextTriggerMillis(config.endHour, config.endMinute)

        scheduleExactAlarm(context, alarmManager, ACTION_SLEEP_SCHEDULE_START, REQUEST_CODE_START, nextStartMs)
        scheduleExactAlarm(context, alarmManager, ACTION_SLEEP_SCHEDULE_END, REQUEST_CODE_END, nextEndMs)
        Log.d(TAG, "已调度睡眠模式定时任务: 开启=${ java.util.Date(nextStartMs) }, 关闭=${ java.util.Date(nextEndMs) }")
    }

    private fun computeNextTriggerMillis(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now + 1000L) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, SleepScheduleReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "精确闹钟权限受限，回退到普通闹钟", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "调度睡眠定时闹钟失败: $action", e)
        }
    }

    private fun cancelScheduledAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            ACTION_SLEEP_SCHEDULE_START to REQUEST_CODE_START,
            ACTION_SLEEP_SCHEDULE_END to REQUEST_CODE_END
        ).forEach { (action, code) ->
            val intent = Intent(context, SleepScheduleReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    /**
     * 进入睡眠模式
     */
    fun enterSleepMode(context: Context, sendNotification: Boolean = true, isScheduled: Boolean = false) {
        val wasAlreadySleep = _isSleepMode.value
        _isSleepMode.value = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SLEEP_MODE, true).apply()
        Log.d(TAG, "💤 已进入睡眠模式 (scheduled=$isScheduled)")

        if (sendNotification && (!wasAlreadySleep || isScheduled)) {
            val title = if (isScheduled) "💤 睡眠模式已定时开启" else "💤 已进入睡眠模式"
            val text = "我睡觉啦已启用，特别关心联系人发来消息时将响起闹钟提醒。"
            sendSleepStatusNotification(context, title, text)
        }
    }

    /**
     * 退出睡眠模式 + 停止响铃
     */
    fun exitSleepMode(context: Context, sendNotification: Boolean = true, isScheduled: Boolean = false) {
        val wasSleep = _isSleepMode.value
        stopAlarm()
        _isSleepMode.value = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SLEEP_MODE, false).apply()
        Log.d(TAG, "☀️ 已退出睡眠模式 (scheduled=$isScheduled)")

        if (sendNotification && (wasSleep || isScheduled)) {
            val title = if (isScheduled) "☀️ 睡眠模式已定时关闭" else "☀️ 已退出睡眠模式"
            val text = "早安！我睡觉啦已关闭，通知提醒已恢复正常模式。"
            sendSleepStatusNotification(context, title, text)
        }
    }

    private fun createStatusNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_SLEEP_MODE,
                "睡眠模式状态通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "睡眠模式开启或关闭时的状态提醒通知"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendSleepStatusNotification(context: Context, title: String, content: String) {
        try {
            createStatusNotificationChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_SLEEP_STATUS,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_SLEEP_MODE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SLEEP_STATUS, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "缺少通知权限，无法发送睡眠模式状态通知", e)
        } catch (e: Exception) {
            Log.e(TAG, "发送睡眠模式状态通知失败", e)
        }
    }

    /**
     * 触发闹钟响铃（系统自带闹钟铃声 + 振动，持续播放直到用户手动关闭）
     */
    fun triggerAlarm(context: Context, senderName: String) {
        Log.d(TAG, "🔔 触发闹钟响铃: 来自 $senderName")

        // 如果已经在响了就不重复触发
        if (mediaPlayer?.isPlaying == true) {
            Log.d(TAG, "闹钟已在响铃中，跳过")
            return
        }

        try {
            // 获取系统闹钟铃声
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true  // 持续循环播放，直到用户关闭
                prepare()
                start()
            }
            Log.d(TAG, "闹钟铃声已开始播放")

            // 同时振动
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 长脉冲振动模式：振500ms停300ms循环
                    val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = 从索引0循环
                }
            } catch (e: Exception) {
                Log.e(TAG, "振动启动失败", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "闹钟播放失败", e)
        }
    }

    /**
     * 停止闹钟响铃
     */
    fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            Log.d(TAG, "闹钟已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止闹钟异常", e)
        }
    }

    /**
     * 当前是否正在响铃
     */
    fun isAlarmPlaying(): Boolean = mediaPlayer?.isPlaying == true
}

/**
 * 定时睡眠广播接收器：处理定时开启与定时关闭
 */
class SleepScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SleepModeManager.init(context.applicationContext)
        when (intent?.action) {
            SleepModeManager.ACTION_SLEEP_SCHEDULE_START -> {
                if (SleepModeManager.scheduleConfig.value.enabled) {
                    SleepModeManager.enterSleepMode(context.applicationContext, sendNotification = true, isScheduled = true)
                    SleepModeManager.scheduleAlarms(context.applicationContext)
                }
            }
            SleepModeManager.ACTION_SLEEP_SCHEDULE_END -> {
                if (SleepModeManager.scheduleConfig.value.enabled) {
                    SleepModeManager.exitSleepMode(context.applicationContext, sendNotification = true, isScheduled = true)
                    SleepModeManager.scheduleAlarms(context.applicationContext)
                }
            }
        }
    }
}
