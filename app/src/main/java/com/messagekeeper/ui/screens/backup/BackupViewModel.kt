package com.messagekeeper.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.backup.BackupManager
import com.messagekeeper.ui.components.Sonner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 详情里每个会话最多常驻内存的消息条数（避免 5MB 级备份把整份数据塞进 State） */
internal const val MAX_PREVIEW_MESSAGES_PER_CONVERSATION = 500

/** 单条消息预览内容的最大字符数（防止异常超长记录拖垮列表渲染） */
private const val MAX_PREVIEW_CONTENT_CHARS = 2000

/** 备份详情里的一个会话（只保留展示所需字段） */
data class BackupConversation(
    val id: Long,
    val displayName: String,
    val platform: String,
    val messageCount: Int,
    val lastMessage: String,
    val lastMessageTime: Long
)

/** 备份详情里的一条只读消息 */
data class BackupMessage(
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isRecalled: Boolean
)

/** 解析后的备份详情：会话列表 + 每会话最多 [MAX_PREVIEW_MESSAGES_PER_CONVERSATION] 条消息 */
data class BackupDetail(
    val fileName: String,
    val createdAt: Long,
    val kind: String,
    val totalMessages: Int,
    val conversations: List<BackupConversation>,
    val messagesByConversation: Map<Long, List<BackupMessage>>
)

data class BackupUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val busyText: String = "",
    val isCreating: Boolean = false,
    val backups: List<BackupManager.BackupInfo> = emptyList(),
    val detail: BackupDetail? = null,
    val detailLoading: Boolean = false,
    val selectedConversationId: Long? = null
)

class BackupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    // ─────────────────────────── 列表 ───────────────────────────

    /** 拉取备份列表：读目录 / 读 meta 全在 IO，主线程只更新 StateFlow */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.backups.isEmpty())
            var failed = false
            val backups: List<BackupManager.BackupInfo> = try {
                withContext(Dispatchers.IO) { BackupManager.listBackups() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                emptyList()
            }
            _uiState.value = _uiState.value.copy(isLoading = false, backups = backups)
            if (failed) Sonner.error("读取备份列表失败，请查看运行日志")
        }
    }

    /** 「立即备份」：手动打包当前数据库为一份 manual 备份 */
    fun createManualBackup() {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            isCreating = true,
            busyText = "正在打包当前数据…"
        )
        viewModelScope.launch {
            val created = try {
                withContext(Dispatchers.IO) { BackupManager.createBackup("manual") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(isBusy = false, isCreating = false, busyText = "")
            if (created != null) {
                Sonner.success("备份完成：${created.messages} 条消息 / ${formatBackupSize(created.sizeBytes)}")
            } else {
                Sonner.error("备份失败，请查看运行日志")
            }
            refresh()
        }
    }

    // ─────────────────────────── 回退 / 合并 / 删除 ───────────────────────────

    /** 回退：数据层会先自动生成一份「回退前」备份，再整体替换当前数据 */
    fun restoreBackup(fileName: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, busyText = "正在回退（会先备份当前数据）…")
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { BackupManager.restoreFromBackup(fileName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
            finishBusy()
            result.fold(
                onSuccess = { count ->
                    Sonner.success("回退完成：已恢复 $count 条消息")
                    _uiState.value = _uiState.value.copy(detail = null, selectedConversationId = null)
                },
                onFailure = { e -> Sonner.error("回退失败：${e.message ?: "未知错误"}") }
            )
            refresh()
        }
    }

    /** 合并：只补充当前数据里没有的消息，不删除、不覆盖 */
    fun mergeBackup(fileName: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, busyText = "正在合并备份…")
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { BackupManager.mergeFromBackup(fileName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
            finishBusy()
            result.fold(
                onSuccess = { added -> Sonner.success("合并完成：新增 $added 条消息") },
                onFailure = { e -> Sonner.error("合并失败：${e.message ?: "未知错误"}") }
            )
            refresh()
        }
    }

    /** 删除备份文件（UI 侧已做二次确认） */
    fun deleteBackup(fileName: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, busyText = "正在删除备份文件…")
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { BackupManager.deleteBackup(fileName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            finishBusy()
            if (ok) Sonner.success("备份已删除") else Sonner.error("删除失败，请查看运行日志")
            if (ok && _uiState.value.detail?.fileName == fileName) {
                _uiState.value = _uiState.value.copy(detail = null, selectedConversationId = null)
            }
            refresh()
        }
    }

    // ─────────────────────────── 详情查看 ───────────────────────────

    /** 读取并解析某份备份（5MB 级 JSON 全程在 IO + 后台线程解析） */
    fun openDetail(fileName: String) {
        val info = _uiState.value.backups.find { it.fileName == fileName }
        if (info == null) {
            Sonner.error("备份文件不存在，请刷新列表")
            return
        }
        if (_uiState.value.detailLoading) return
        _uiState.value = _uiState.value.copy(detailLoading = true)
        viewModelScope.launch {
            val detail = try {
                withContext(Dispatchers.IO) {
                    val json = BackupManager.readBackupJson(fileName)
                    if (json.isNullOrBlank()) null else parseBackupDetail(info, json)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(
                detailLoading = false,
                detail = detail,
                selectedConversationId = null
            )
            if (detail == null) {
                Sonner.error("读取备份内容失败")
            } else if (detail.conversations.isEmpty()) {
                Sonner.info("该备份不包含任何会话")
            }
        }
    }

    /** 关闭详情，回到备份列表 */
    fun closeDetail() {
        _uiState.value = _uiState.value.copy(detail = null, selectedConversationId = null)
    }

    /** 选中某个会话查看消息；传 null 回到会话列表 */
    fun selectConversation(conversationId: Long?) {
        _uiState.value = _uiState.value.copy(selectedConversationId = conversationId)
    }

    // ─────────────────────────── 内部实现 ───────────────────────────

    private fun finishBusy() {
        _uiState.value = _uiState.value.copy(isBusy = false, busyText = "")
    }

    /**
     * 解析备份 JSON（与 [com.messagekeeper.data.repository.MessageRepository.exportBackupJson] 的结构一致）：
     *  - conversations: id / platform / conversation_key / display_name / last_message / last_message_time
     *  - messages: conversation_id / sender_name / content / timestamp / is_recalled
     * 只保留展示所需字段，且每会话消息最多 [MAX_PREVIEW_MESSAGES_PER_CONVERSATION] 条，避免内存膨胀。
     */
    private fun parseBackupDetail(info: BackupManager.BackupInfo, json: String): BackupDetail {
        val root = JSONObject(json)
        val conversationArray = root.optJSONArray("conversations") ?: JSONArray()
        val messageArray = root.optJSONArray("messages") ?: JSONArray()

        val conversations = LinkedHashMap<Long, BackupConversation>()
        for (index in 0 until conversationArray.length()) {
            val obj = conversationArray.optJSONObject(index) ?: continue
            val id = obj.optLong("id")
            if (id == 0L) continue
            val key = obj.optString("conversation_key")
            val displayName = obj.optString("display_name").ifBlank {
                key.substringAfter('_').ifBlank { "未命名会话" }
            }
            conversations[id] = BackupConversation(
                id = id,
                displayName = displayName,
                platform = obj.optString("platform"),
                messageCount = 0,
                lastMessage = obj.optString("last_message"),
                lastMessageTime = obj.optLong("last_message_time")
            )
        }

        val messageCounts = HashMap<Long, Int>()
        val previews = HashMap<Long, MutableList<BackupMessage>>()
        for (index in 0 until messageArray.length()) {
            val obj = messageArray.optJSONObject(index) ?: continue
            val conversationId = obj.optLong("conversation_id")
            messageCounts[conversationId] = (messageCounts[conversationId] ?: 0) + 1
            val bucket = previews.getOrPut(conversationId) {
                ArrayList(MAX_PREVIEW_MESSAGES_PER_CONVERSATION)
            }
            if (bucket.size < MAX_PREVIEW_MESSAGES_PER_CONVERSATION) {
                bucket.add(
                    BackupMessage(
                        senderName = obj.optString("sender_name"),
                        content = obj.optString("content").take(MAX_PREVIEW_CONTENT_CHARS),
                        timestamp = obj.optLong("timestamp"),
                        isRecalled = obj.optBoolean("is_recalled", false)
                    )
                )
            }
        }
        // 备份按 id 导出，消息基本已按时间升序；这里再排一次防止合并过的备份乱序
        previews.values.forEach { list -> list.sortBy { it.timestamp } }

        val sortedConversations = conversations.values
            .map { it.copy(messageCount = messageCounts[it.id] ?: 0) }
            .sortedByDescending { it.lastMessageTime }

        val parsedCount = messageCounts.values.sum()
        return BackupDetail(
            fileName = info.fileName,
            createdAt = info.createdAt,
            kind = info.kind,
            totalMessages = maxOf(parsedCount, info.messages),
            conversations = sortedConversations,
            messagesByConversation = previews
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackupViewModel() as T
            }
        }
    }
}

// ─────────────────────────── 展示层格式化工具（UI 主线程也会用，故放在包级） ───────────────────────────

private val BACKUP_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
private val BACKUP_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())

/** 例如 2026-11-01 11:26 */
internal fun formatBackupDate(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    return try {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(BACKUP_DATE_FORMATTER)
    } catch (e: Exception) {
        "时间未知"
    }
}

/** 消息列表里的短时间，例如 11-01 11:26 */
internal fun formatBackupMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return "--:--"
    return try {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(BACKUP_TIME_FORMATTER)
    } catch (e: Exception) {
        "--:--"
    }
}

/** 文件大小：B / KB / MB */
internal fun formatBackupSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
        else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
    }
}
