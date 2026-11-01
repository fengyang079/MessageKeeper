package com.messagekeeper.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话实体
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["conversation_key"], unique = true)]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 平台: "wechat", "qq", "douyin", "kuaishou", "duoshan", "qqmusic" */
    val platform: String,
    /** 对话唯一标识，如 "wechat_张三" */
    val conversation_key: String,
    /** 显示名称（联系人或群名） */
    val display_name: String,
    /** 最后一条消息预览 */
    val last_message: String = "",
    /** 最后消息时间戳 */
    val last_message_time: Long = 0L,
    /** 未读计数 */
    val unread_count: Int = 1,
    /** 是否置顶 */
    val is_pinned: Boolean = false,
    /** 是否为软件消息（软件通知不计入聊天统计） */
    val is_system: Boolean = false,
    /** 会话类型：0=自动识别，1=群聊，2=私信（长按会话手动指定） */
    val chat_type: Int = CHAT_TYPE_AUTO,
) {
    companion object {
        /** 自动识别：按正文 "发送者: 内容" 前缀启发式判断 */
        const val CHAT_TYPE_AUTO = 0
        /** 手动指定为群聊：发送者始终取正文前缀 */
        const val CHAT_TYPE_GROUP = 1
        /** 手动指定为私信：发送者始终为联系人名，正文不拆分 */
        const val CHAT_TYPE_PRIVATE = 2
    }
}

/**
 * 平台常量
 */
object Platform {
    const val WECHAT = "wechat"
    const val QQ = "qq"
    const val DOUYIN = "douyin"
    const val KUAISHOU = "kuaishou"
    const val DUOSHAN = "duoshan"
    const val QQMUSIC = "qqmusic"

    /** 内置平台列表 */
    val builtinPlatforms = listOf(WECHAT, QQ, DOUYIN, KUAISHOU, DUOSHAN, QQMUSIC)

    /** 内置平台显示名 */
    private val builtinNames = mapOf(
        WECHAT to "微信",
        QQ to "QQ",
        DOUYIN to "抖音",
        KUAISHOU to "快手",
        DUOSHAN to "多闪",
        QQMUSIC to "QQ音乐"
    )

    fun displayName(platform: String): String {
        return builtinNames[platform] ?: platform
    }

    /** 动态获取显示名称（优先从用户配置读取） */
    fun displayName(context: android.content.Context, platform: String): String {
        val fromManager = com.messagekeeper.data.prefs.MonitoredAppsManager.getDisplayName(context, platform)
        if (fromManager != platform) return fromManager
        return builtinNames[platform] ?: platform
    }
}

