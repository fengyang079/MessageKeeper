package com.messagekeeper.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import com.messagekeeper.data.backup.BackupManager
import com.messagekeeper.data.prefs.RetentionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 心跳检测广播接收器
 * 由 AlarmManager 精确闹钟周期性触发，检测 NotificationListenerService 是否存活，
 * 若已死亡则通过 requestRebind + 组件开关大法强制复活。
 *
 * 另外每次心跳顺带触发「每天最多一次」的历史消息自动清理与每日自动备份
 * （goAsync + 后台协程，不阻塞心跳主流程）。
 */
class HeartbeatReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeartbeatReceiver"
        const val ACTION_HEARTBEAT = "com.messagekeeper.HEARTBEAT_CHECK"
        private const val HEARTBEAT_INTERVAL_MS = 60_000L // 60 秒一次心跳

        /**
         * 备份 / 自动清理专用后台作用域：随进程存活，
         * 独立 SupervisorJob 保证一次失败不影响后续心跳触发的任务。
         */
        private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 防止上一次维护任务（备份可能耗时）尚未结束时重叠执行 */
        private val maintenanceRunning = AtomicBoolean(false)

        /**
         * 调度下一次心跳闹钟（精确闹钟，Doze 模式下仍可触发）
         */
        fun scheduleHeartbeat(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                }
                Log.d(TAG, "下一次心跳已调度: ${HEARTBEAT_INTERVAL_MS / 1000}秒后")
            } catch (e: Exception) {
                Log.e(TAG, "调度心跳闹钟失败", e)
            }
        }

        /**
         * 取消心跳闹钟
         */
        fun cancelHeartbeat(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeartbeatReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT) return
        Log.d(TAG, "💓 心跳检测触发")

        // 检查 NotificationListenerService 是否已连接
        val isListenerAlive = isNotificationListenerAlive(context)
        Log.d(TAG, "NotificationListener 状态: ${if (isListenerAlive) "存活" else "已死亡"}")

        if (!isListenerAlive) {
            Log.w(TAG, "⚠️ 检测到 NotificationListener 已断开，执行强制复活！")
            reviveNotificationListener(context)
        }

        // 无论如何，调度下一次心跳
        scheduleHeartbeat(context)

        // 顺带执行每日维护：历史消息自动清理 + 每日自动备份（每天最多一次，异步执行）
        runPeriodicMaintenance(context)
    }

    /**
     * 心跳顺带执行的每日维护：
     * 1) [BackupManager.runRetentionIfDue]：按设置的天数清理历史消息（每天最多一次）；
     * 2) [BackupManager.ensureDailyBackupIfDue]：生成当天唯一的一份 auto 备份。
     *
     * 用 goAsync() 把广播的存活时间延长到后台任务结束，并在 finally 里 finish()；
     * 全部 try/catch，任何异常都不会影响心跳主流程。
     */
    private fun runPeriodicMaintenance(context: Context) {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            Log.d(TAG, "上一次自动清理/备份任务仍在执行，跳过本次")
            return
        }
        // 必须在主线程同步获取 PendingResult，延后到后台任务结束时 finish()
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        try {
            maintenanceScope.launch {
                try {
                    try {
                        BackupManager.init(appContext)
                        RetentionPreferences.init(appContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "备份/清理模块初始化失败", e)
                    }
                    try {
                        // 顺序很重要：先保证当天有全量备份，再执行 30 天历史清理（可回退）
                        BackupManager.ensureDailyBackupIfDue()
                    } catch (e: Exception) {
                        Log.e(TAG, "心跳触发的每日自动备份异常", e)
                    }
                    try {
                        BackupManager.runRetentionIfDue()
                    } catch (e: Exception) {
                        Log.e(TAG, "心跳触发的历史消息自动清理异常", e)
                    }
                } finally {
                    maintenanceRunning.set(false)
                    try {
                        pendingResult.finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "finish 心跳维护 PendingResult 失败", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提交心跳维护任务失败", e)
            maintenanceRunning.set(false)
            try {
                pendingResult.finish()
            } catch (ignored: Exception) {
                // 已 finish 或广播已结束时可能抛异常，忽略
            }
        }
    }

    /**
     * 检测 NotificationListenerService 是否处于启用状态
     */
    private fun isNotificationListenerAlive(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(context, MessageNotificationListener::class.java).flattenToString()
        return flat.contains(componentName)
    }

    /**
     * 强制复活 NotificationListenerService
     * 使用组件开关大法 + requestRebind 双保险
     */
    private fun reviveNotificationListener(context: Context) {
        try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MessageNotificationListener::class.java)

            // 1. 组件开关大法：先禁用再启用，强制系统重新绑定
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )

            // 2. requestRebind 请求系统重新绑定
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(componentName)
            }

            Log.d(TAG, "✅ 强制复活完成")
        } catch (e: Exception) {
            Log.e(TAG, "强制复活失败", e)
        }
    }
}
