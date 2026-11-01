package com.messagekeeper.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.MainActivity
import com.messagekeeper.MessageKeeperApp
import com.messagekeeper.data.db.entity.SoundEffects

object SpecialAlertManager {

    private const val TAG = "SpecialAlertManager"
    private const val NOTIFICATION_ID_BASE = 1000
    private const val TEST_NOTIFICATION_ID = 9999

    private var previewPlayer: MediaPlayer? = null

    /**
     * 发送特别提醒通知（Android 标准聊天样式，悬浮展示）
     */
    fun sendAlert(
        context: Context,
        platform: String,
        senderName: String,
        content: String,
        soundName: String = "default"
    ) {
        Log.d(TAG, "发送特别提醒: sender=$senderName, sound=$soundName")

        try {
            val targetPkg = when (platform) {
                "wechat" -> "com.tencent.mm"
                "qq" -> "com.tencent.mobileqq"
                "douyin" -> "com.ss.android.ugc.aweme"
                "kuaishou" -> "com.smile.gifmaker"
                "duoshan" -> "my.maya.android"
                "qqmusic" -> "com.tencent.qqmusic"
                else -> null
            }
            val launchIntent = targetPkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
            val intent = launchIntent ?: Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 点亮屏幕（针对息屏状态）
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "MessageKeeper::SpecialAlertWakeLock"
                )
                wakeLock.acquire(3000L) // 点亮 3 秒钟
            } catch (e: Exception) {
                Log.w(TAG, "唤醒屏幕失败", e)
            }

            val platformLabel = Platform.displayName(platform)
            val channelId = MessageKeeperApp.getChannelIdForSound(soundName)

            // 使用 Android 标准的聊天通知样式（Person + MessagingStyle）
            val sender = Person.Builder()
                .setName("$senderName（$platformLabel）")
                .build()

            val style = NotificationCompat.MessagingStyle(sender)
                .setConversationTitle("特别提醒")
                .addMessage(content, System.currentTimeMillis(), sender)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("$senderName（$platformLabel）")
                .setContentText(content)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // 允许在息屏时直接弹窗或唤醒屏幕
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                .build()

            val notificationId = NOTIFICATION_ID_BASE + senderName.hashCode()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "特别提醒通知已发送: id=$notificationId, channel=$channelId")

        } catch (e: SecurityException) {
            Log.w(TAG, "没有通知权限，无法发送特别提醒")
        } catch (e: Exception) {
            Log.e(TAG, "发送特别提醒失败", e)
        }
    }

    /**
     * 发送测试通知
     */
    fun sendTestAlert(
        context: Context,
        senderName: String,
        platform: String = "wechat",
        soundName: String = "default"
    ) {
        Log.d(TAG, "发送测试通知: sender=$senderName, sound=$soundName")

        try {
            val platformLabel = Platform.displayName(platform)
            val testContent = "这是一条测试消息 —— 来自 $senderName"

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = MessageKeeperApp.getChannelIdForSound(soundName)

            val sender = Person.Builder()
                .setName("$senderName（$platformLabel）")
                .build()

            val style = NotificationCompat.MessagingStyle(sender)
                .setConversationTitle("测试通知")
                .addMessage(testContent, System.currentTimeMillis(), sender)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("🔔 测试通知 — $senderName")
                .setContentText(testContent)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
                .build()

            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            Log.d(TAG, "测试通知已发送")
        } catch (e: SecurityException) {
            Log.w(TAG, "没有通知权限，无法发送测试通知")
        } catch (e: Exception) {
            Log.e(TAG, "发送测试通知失败", e)
        }
    }

    fun playPreview(context: Context, soundName: String): MediaPlayer? {
        stopPreview()
        Log.d(TAG, "试听声音: $soundName")

        if (soundName == "default" || soundName == "silent") {
            Log.d(TAG, "默认/静音没有可试听的声音")
            return null
        }

        if (soundName.startsWith("custom_")) {
            val customSounds = CustomSoundManager.getCustomSounds(context)
            val sound = customSounds.find { it.id == soundName }
            if (sound != null && java.io.File(sound.filePath).exists()) {
                return try {
                    val player = MediaPlayer().apply {
                        setDataSource(sound.filePath)
                        prepare()
                        setOnCompletionListener {
                            Log.d(TAG, "试听播放完成: ${sound.displayName}")
                            previewPlayer = null
                        }
                        start()
                    }
                    previewPlayer = player
                    player
                } catch (e: Exception) {
                    Log.e(TAG, "播放自定义试听音频失败: ${sound.filePath}", e)
                    null
                }
            }
        }

        val resId = SoundEffects.getRawResId(soundName)
        if (resId == null || resId == 0) {
            Log.w(TAG, "声音资源不存在: $soundName")
            return null
        }

        return try {
            val player = MediaPlayer.create(context, resId).apply {
                setOnCompletionListener {
                    Log.d(TAG, "试听播放完成: $soundName")
                    previewPlayer = null
                }
                start()
                Log.d(TAG, "开始试听: $soundName")
            }
            previewPlayer = player
            player
        } catch (e: Exception) {
            Log.e(TAG, "试听播放失败: $soundName", e)
            null
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.apply {
                if (isPlaying) {
                    Log.d(TAG, "停止试听")
                    stop()
                }
                release()
            }
            previewPlayer = null
        } catch (e: Exception) {
            previewPlayer = null
        }
    }
}
