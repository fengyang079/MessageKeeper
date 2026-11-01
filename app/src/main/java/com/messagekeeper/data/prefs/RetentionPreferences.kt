package com.messagekeeper.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 历史消息保留策略偏好。
 *
 * 语义：
 * - 默认保留 30 天；值为 0 表示"永不自动清理"；
 * - 设置页只提供 0 / 7 / 30 / 90 四档；
 * - 这里只负责"用户选择"的存取，"每天最多执行一次"的去重标记由
 *   [com.messagekeeper.data.backup.BackupManager] 自己维护。
 */
object RetentionPreferences {

    private const val TAG = "RetentionPreferences"
    private const val PREFS_NAME = "message_keeper_retention_prefs"
    private const val KEY_RETENTION_DAYS = "retention_days"

    /** 默认保留天数 */
    const val DEFAULT_RETENTION_DAYS = 30

    /** 允许的保留天数档位；0 = 永不自动清理 */
    private val ALLOWED_DAYS = setOf(0, 7, 30, 90)

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 幂等初始化；通常在 MessageKeeperApp.onCreate 中调用 */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "初始化完成")
        }
    }

    /**
     * 当前保留天数。
     * 未初始化或存储值非法时回退到 [DEFAULT_RETENTION_DAYS]；0 表示永不自动清理。
     */
    fun getRetentionDays(): Int {
        val stored = prefs?.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
            ?: DEFAULT_RETENTION_DAYS
        return if (stored in ALLOWED_DAYS) stored else DEFAULT_RETENTION_DAYS
    }

    /**
     * 设置保留天数，只接受 0 / 7 / 30 / 90。
     * 未初始化或传入非法值时不写入并打日志，不会抛异常。
     */
    fun setRetentionDays(days: Int) {
        if (days !in ALLOWED_DAYS) {
            Log.w(TAG, "忽略非法保留天数: $days（仅支持 0/7/30/90）")
            return
        }
        val target = prefs
        if (target == null) {
            Log.w(TAG, "RetentionPreferences 未初始化，设置失败: $days")
            return
        }
        target.edit().putInt(KEY_RETENTION_DAYS, days).apply()
        Log.d(TAG, "消息保留天数已设置为 $days 天")
    }
}
