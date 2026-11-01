package com.messagekeeper.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 特别提醒实体
 */
@Entity(tableName = "special_alerts")
data class SpecialAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 平台: "wechat" 或 "qq" */
    val platform: String,
    /** 要监听的联系人名称 */
    val sender_name: String,
    /** 是否启用 */
    val is_enabled: Boolean = true,
    /** 创建时间 */
    val created_at: Long = System.currentTimeMillis(),
    /** 提醒声音名称: "ganlu", "default", "silent", "dingdong", "warnning" 等 */
    val sound_name: String = "ganlu",
)

/**
 * 内置声音列表
 */
object SoundEffects {
    val sounds = listOf(
        SoundItem("default", "🔔 默认通知"),
        SoundItem("silent", "🔇 静音"),
        SoundItem("dingdong", "叮咚"),
        SoundItem("ganlu", "甘露"),
        SoundItem("warnning", "警告音"),
    )

    data class SoundItem(
        val name: String,
        val displayName: String,
    )

    /** 根据声音名称获取 R.raw 资源 ID */
    fun getRawResId(name: String): Int? = when (name) {
        "dingdong" -> com.messagekeeper.R.raw.dingdong
        "ganlu" -> com.messagekeeper.R.raw.ganlu
        "warnning" -> com.messagekeeper.R.raw.warnning
        else -> null
    }
}
