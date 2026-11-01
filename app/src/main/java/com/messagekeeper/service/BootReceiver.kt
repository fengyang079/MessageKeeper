package com.messagekeeper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启广播接收器
 * 手机重启后自动启动前台保活服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "系统启动完成，执行开机自启逻辑")
            
            // 1. 暴力重置 NotificationListener，防止部分系统开机不自动绑定的问题
            try {
                val pm = context.packageManager
                val componentName = android.content.ComponentName(context, com.messagekeeper.service.MessageNotificationListener::class.java)
                
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
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.service.notification.NotificationListenerService.requestRebind(componentName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "开机重置监听服务失败", e)
            }

            // 2. 启动前台保活服务（必须在系统设置中允许自启动，否则这步会被拦截）
            try {
                val serviceIntent = Intent(context, ForegroundKeepAliveService::class.java)
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "启动服务失败", e)
            }
            // 3. 重新调度睡眠模式的定时开关闹钟
            try {
                SleepModeManager.init(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "开机初始化睡眠模式定时失败", e)
            }
        }
    }
}
