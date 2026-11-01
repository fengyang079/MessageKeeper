package com.messagekeeper.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.messagekeeper.data.parser.NotificationParser
import com.messagekeeper.data.repository.MessageRepository
import com.messagekeeper.notification.SpecialAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * 无障碍服务保活
 * 该服务不处理任何实际的无障碍事件，仅仅是为了利用 Android 系统的无障碍服务高优先级特性，
 * 防止 App 进程在后台被系统或者第三方清理软件杀掉（类似李跳跳等软件的保活机制）。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeepAliveService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: MessageRepository by lazy { MessageRepository.getInstance() }

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d(TAG, "无障碍保活服务创建，进程优先级提升")
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate 异常", e)
        }
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            Log.d(TAG, "无障碍保活服务已连接！")
        } catch (e: Throwable) {
            Log.e(TAG, "onServiceConnected 异常", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 兜底方案：如果普通的 NotificationListener 被杀，无障碍服务也能抓取到通知！
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            if (!NotificationParser.isSupported(this, packageName)) return

            val parcelable = event.parcelableData
            if (parcelable is Notification) {
                Log.d(TAG, "无障碍服务抓取到通知: $packageName")
                
                // 由于无障碍服务拿不到标准的 sbn.key 和 postTime，这里自己生成一下
                val uniqueKey = "a11y_${System.currentTimeMillis()}"
                
                val parsed = try {
                    NotificationParser.parse(this, packageName, parcelable, 0L, uniqueKey)
                } catch (e: Exception) {
                    null
                } ?: return

                Log.d(TAG, "无障碍解析成功: sender=${parsed.senderName}")
                processParsedNotification(parsed)
            }
        }
    }

    private fun processParsedNotification(parsed: com.messagekeeper.data.parser.ParsedNotification) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MessageKeeper::A11yProcessLock")
        wakeLock.acquire(10_000L) 

        scope.launch {
            try {
                if (parsed.isRecall) {
                    repository.handleRecallNotification(
                        platform = parsed.platform,
                        conversationName = parsed.conversationName,
                        senderName = parsed.senderName,
                        timestamp = parsed.timestamp,
                        notificationId = parsed.notificationId
                    )
                } else {
                    val saved = repository.saveIncomingMessage(
                        platform = parsed.platform,
                        conversationName = parsed.conversationName,
                        senderName = parsed.senderName,
                        content = parsed.content,
                        timestamp = parsed.timestamp,
                        notificationId = parsed.notificationId
                    )

                    if (saved.isNew) {
                        try {
                            val matchingAlert = repository.findMatchingAlert(
                                parsed.platform,
                                saved.senderName,
                                parsed.conversationName
                            )
                            if (matchingAlert != null) {
                                // ★ 睡眠模式：特别关心的人发消息 → 触发闹钟铃声
                                if (SleepModeManager.isSleepMode.value) {
                                    SleepModeManager.triggerAlarm(this@KeepAliveAccessibilityService, saved.senderName)
                                }

                                SpecialAlertManager.sendAlert(
                                    context = this@KeepAliveAccessibilityService,
                                    platform = parsed.platform,
                                    senderName = saved.senderName,
                                    content = saved.content,
                                    soundName = matchingAlert.sound_name
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "无障碍特别提醒失败", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "无障碍处理通知失败", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun onInterrupt() {
        try {
            Log.d(TAG, "无障碍保活服务被中断")
        } catch (e: Throwable) {
            Log.e(TAG, "onInterrupt 异常", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            Log.d(TAG, "无障碍保活服务解绑")
        } catch (e: Throwable) {
            Log.e(TAG, "onUnbind 异常", e)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        try {
            scope.cancel()
        } catch (e: Throwable) {
            Log.e(TAG, "scope cancel 异常", e)
        }
        super.onDestroy()
        Log.d(TAG, "无障碍保活服务被销毁")
    }
}



