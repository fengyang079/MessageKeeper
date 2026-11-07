package com.messagekeeper.data.models

data class RemoteStatus(
    val label: String = "未设置",
    val category: String = "日常",
    val description: String = "",
    val color: String = "#DCE9F5",
    val isOnline: Boolean = true,
    val publicVisible: Boolean = true,
    val updatedAt: String = "",
    val autoRevertAt: String? = null
)

data class StatusPreset(
    val id: Long = 0,
    val label: String = "",
    val category: String = "",
    val description: String = "",
    val color: String = "#DCE9F5"
)

data class StatusHistory(
    val id: Long = 0,
    val label: String = "",
    val category: String = "",
    val description: String = "",
    val color: String = "#DCE9F5",
    val createdAt: String = ""
)

data class StatusDashboardData(
    val status: RemoteStatus = RemoteStatus(),
    val presets: List<StatusPreset> = emptyList(),
    val history: List<StatusHistory> = emptyList(),
    val serverNow: String = ""
)

data class EventAttachment(
    val name: String = "",
    val url: String = "",
    val size: Long = 0L,
    val isImage: Boolean = false
)

data class EventTimelineLog(
    val id: Long = 0L,
    val eventId: Long = 0L,
    val logType: String = "跟进",
    val content: String = "",
    val attachments: List<EventAttachment> = emptyList(),
    val createdAt: String = ""
)

data class StateEvent(
    val id: Long = 0L,
    val title: String = "",
    val type: String = "通用",
    val urgency: Int = 3,
    val visibility: Int = 1,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val description: String = "",
    val logs: List<EventTimelineLog> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val archivedAt: String? = null
)

data class SystemAlert(
    val id: Long = 0L,
    val message: String = "",
    val level: String = "blue",
    val updatedAt: String = ""
)

data class StateCategory(
    val id: Long = 0L,
    val name: String = "",
    val color: String = "#3b82f6"
)

data class StateUser(
    val id: Long = 0L,
    val username: String = "",
    val level: Int = 1,
    val status: String = "active",
    val apiKey: String = "",
    val note: String = "",
    val lastIp: String = "",
    val lastDeviceId: String = "",
    val lastUserAgent: String = "",
    val lastSeenAt: String = "",
    val createdAt: String = ""
)

data class UserSession(
    val id: Long = 0L,
    val ip: String = "",
    val deviceId: String = "",
    val userAgent: String = "",
    val action: String = "login",
    val createdAt: String = ""
)

data class StatusStats(
    val active: Int = 0,
    val urgent: Int = 0,
    val archived: Int = 0,
    val weekLogs: Int = 0
)

data class RecycleBinItem(
    val id: Long = 0L,
    val itemType: String = "event", // "event" | "status_history" | "event_log"
    val originalId: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
    val deletedBy: String = "admin",
    val deletedAt: String = ""
)
