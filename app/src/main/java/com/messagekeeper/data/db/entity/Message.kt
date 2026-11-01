package com.messagekeeper.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["notification_id"]),
        Index(value = ["conversation_id", "timestamp"])
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 关联的对话 ID */
    val conversation_id: Long,
    /** 发送者名称 */
    val sender_name: String,
    /** 消息内容 */
    val content: String,
    /** 消息时间戳（毫秒） */
    val timestamp: Long,
    /** 平台: "wechat" 或 "qq" */
    val platform: String,
    /** 系统通知 ID，用于关联 */
    val notification_id: String,
    /** 是否已撤回（标记用） */
    val is_recalled: Boolean = false,
)
