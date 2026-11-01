package com.messagekeeper.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.messagekeeper.MessageKeeperApp

class ForegroundKeepAliveService : Service() {

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_ID = 1
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var silentAudioPlayer: SilentAudioPlayer? = null
    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private val HEARTBEAT_CHECK_INTERVAL = 30_000L

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== 前台保活服务 创建 ===")
        try {
            startForeground()
            Log.d(TAG, "前台通知已显示")
            
            // 1. 获取永久 WakeLock 阻止 CPU 休眠
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MessageKeeper::PermanentKeepAlive")
            wakeLock?.acquire()
            Log.d(TAG, "永久 WakeLock 已持有")

            // 2. 启动无声音乐播放器，欺骗系统正在播放媒体，彻底免疫各种休眠杀后台策略
            silentAudioPlayer = SilentAudioPlayer()
            silentAudioPlayer?.start()
            Log.d(TAG, "无声保活音乐已启动")

            // 3. 启动周期性心跳检测（每 30 秒检查 NotificationListenerService 是否存活）
            startHeartbeatCheck()
            Log.d(TAG, "心跳检测已启动")

        } catch (e: Exception) {
            Log.e(TAG, "启动前台失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: flags=$flags, startId=$startId")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== 前台保活服务 销毁 ===")
        try {
            heartbeatRunnable?.let { heartbeatHandler?.removeCallbacks(it) }
            heartbeatHandler = null
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                wakeLock = null
            }
            silentAudioPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "释放资源异常", e)
        }
    }

    private fun startHeartbeatCheck() {
        heartbeatHandler = Handler(Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                try {
                    val flat = android.provider.Settings.Secure.getString(
                        contentResolver, "enabled_notification_listeners"
                    ) ?: ""
                    val cn = ComponentName(this@ForegroundKeepAliveService, MessageNotificationListener::class.java).flattenToString()
                    val isAlive = flat.contains(cn)
                    
                    if (!isAlive) {
                        Log.w(TAG, "心跳检测: NotificationListener 已死亡，执行复活")
                        val pm = packageManager
                        val component = ComponentName(this@ForegroundKeepAliveService, MessageNotificationListener::class.java)
                        pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                        pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            NotificationListenerService.requestRebind(component)
                        }
                    } else {
                        Log.d(TAG, "心跳检测: NotificationListener 存活")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "心跳检测异常", e)
                }
                heartbeatHandler?.postDelayed(this, HEARTBEAT_CHECK_INTERVAL)
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, HEARTBEAT_CHECK_INTERVAL)
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(
            this,
            MessageKeeperApp.CHANNEL_FOREGROUND_SERVICE
        )
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("消息助手正在运行")
            .setContentText("正在监听微信和QQ消息")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 无声音乐播放器
     * 利用 AudioTrack 持续向音频通道写入 0 (无声)，
     * 让底层 Linux 内核认为该进程正在播放音频，从而彻底阻止 CPU 挂起和进程回收。
     */
    private inner class SilentAudioPlayer {
        private var audioTrack: AudioTrack? = null
        private var isPlaying = false
        private var playThread: Thread? = null

        fun start() {
            if (isPlaying) return
            isPlaying = true
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            try {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize,
                    AudioTrack.MODE_STREAM
                )
                
                audioTrack?.play()
                
                playThread = Thread {
                    val silence = ByteArray(minBufferSize)
                    try {
                        while (isPlaying) {
                            audioTrack?.write(silence, 0, silence.size)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "无声保活写入异常", e)
                    }
                }.apply { start() }
            } catch (e: Exception) {
                Log.e(TAG, "无声保活初始化异常", e)
            }
        }

        fun stop() {
            isPlaying = false
            try {
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {}
            
            try {
                playThread?.interrupt()
                playThread = null
            } catch (e: Exception) {}
        }
    }
}
