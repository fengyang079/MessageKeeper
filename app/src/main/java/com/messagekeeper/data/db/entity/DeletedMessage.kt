package com.messagekeeper.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史删除消息实体（回收站）
 */
@Entity(
    tableName = "deleted_messages",
    indices = [
        Index(value = ["deleted_at"]),
        Index(value = ["conversation_key"])
    ]
)
data class DeletedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 原所属对话唯一标识，如 "wechat_张三"，用于恢复时定位或重建会话 */
    val conversation_key: String,
    /** 原所属对话显示名称（联系人或群名） */
    val conversation_name: String,
    /** 原对话会话类型：0=自动识别，1=群聊，2=私信 */
    val chat_type: Int = Conversation.CHAT_TYPE_AUTO,
    /** 原对话是否为软件消息 */
    val is_system: Boolean = false,
    /** 发送者名称 */
    val sender_name: String,
    /** 消息内容 */
    val content: String,
    /** 原消息时间戳（毫秒） */
    val timestamp: Long,
    /** 平台 */
    val platform: String,
    /** 系统通知 ID */
    val notification_id: String,
    /** 是否已撤回 */
    val is_recalled: Boolean = false,
    /** 删除时间戳（毫秒） */
    val deleted_at: Long = System.currentTimeMillis()
)

