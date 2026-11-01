package com.messagekeeper.data.prefs

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 监听应用管理器
 * 用户可以在设置中自行选择要监听通知的应用，而不是硬编码。
 */
object MonitoredAppsManager {

    private const val TAG = "MonitoredAppsManager"
    private const val PREF_NAME = "monitored_apps_pref"
    private const val KEY_APPS = "monitored_apps_json"

    /**
     * 预置的默认监听应用列表
     * key = 包名, value = 平台标识和显示名称
     */
    data class AppEntry(
        val packageName: String,
        val platformId: String,
        val displayName: String,
        val isDefault: Boolean = false
    )

    private val DEFAULT_APPS = listOf(
        AppEntry("com.tencent.mm", "wechat", "微信", true),
        AppEntry("com.tencent.mobileqq", "qq", "QQ", true),
        AppEntry("com.ss.android.ugc.aweme", "douyin", "抖音", true),
        AppEntry("com.ss.android.ugc.aweme.lite", "douyin", "抖音极速版", true),
        AppEntry("com.smile.gifmaker", "kuaishou", "快手", true),
        AppEntry("com.kuaishou.nebula", "kuaishou", "快手极速版", true),
        AppEntry("my.maya.android", "duoshan", "多闪", true),
        AppEntry("com.tencent.qqmusic", "qqmusic", "QQ音乐", true),
    )

    /**
     * 获取当前已启用的监听应用列表
     */
    fun getMonitoredApps(context: Context): List<AppEntry> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = sp.getString(KEY_APPS, null)

        if (jsonStr == null) {
            // 首次启动，返回所有默认应用
            saveMonitoredApps(context, DEFAULT_APPS)
            return DEFAULT_APPS
        }

        val list = mutableListOf<AppEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AppEntry(
                        packageName = obj.getString("packageName"),
                        platformId = obj.getString("platformId"),
                        displayName = obj.getString("displayName"),
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析监听应用列表失败", e)
            return DEFAULT_APPS
        }
        return list
    }

    /**
     * 保存监听应用列表
     */
    fun saveMonitoredApps(context: Context, apps: List<AppEntry>) {
        val array = JSONArray()
        for (app in apps) {
            array.put(JSONObject().apply {
                put("packageName", app.packageName)
                put("platformId", app.platformId)
                put("displayName", app.displayName)
                put("isDefault", app.isDefault)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPS, array.toString())
            .apply()
    }

    /**
     * 判断某个包名是否在监听列表中
     */
    fun isMonitored(context: Context, packageName: String): Boolean {
        return getMonitoredApps(context).any { it.packageName == packageName }
    }

    /**
     * 通过包名获取平台标识
     */
    fun getPlatformId(context: Context, packageName: String): String {
        return getMonitoredApps(context).find { it.packageName == packageName }?.platformId ?: packageName
    }

    /**
     * 通过平台标识获取显示名称
     */
    fun getDisplayName(context: Context, platformId: String): String {
        return getMonitoredApps(context).find { it.platformId == platformId }?.displayName ?: platformId
    }

    /**
     * 添加一个新的监听应用
     */
    fun addApp(context: Context, packageName: String, displayName: String) {
        val current = getMonitoredApps(context).toMutableList()
        if (current.any { it.packageName == packageName }) return // 已存在
        val platformId = packageName.substringAfterLast(".").lowercase()
        current.add(AppEntry(packageName, platformId, displayName, false))
        saveMonitoredApps(context, current)
    }

    /**
     * 移除一个监听应用
     */
    fun removeApp(context: Context, packageName: String) {
        val current = getMonitoredApps(context).toMutableList()
        current.removeAll { it.packageName == packageName }
        saveMonitoredApps(context, current)
    }

    /**
     * 获取所有默认应用（用于UI显示）
     */
    fun getDefaultApps(): List<AppEntry> = DEFAULT_APPS
}
