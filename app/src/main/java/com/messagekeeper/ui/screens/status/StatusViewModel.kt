package com.messagekeeper.ui.screens.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.models.EventAttachment
import com.messagekeeper.data.models.RecycleBinItem
import com.messagekeeper.data.models.StateCategory
import com.messagekeeper.data.models.StateEvent
import com.messagekeeper.data.models.StateUser
import com.messagekeeper.data.models.StatusDashboardData
import com.messagekeeper.data.models.StatusPreset
import com.messagekeeper.data.models.StatusStats
import com.messagekeeper.data.models.SystemAlert
import com.messagekeeper.data.models.UserSession
import com.messagekeeper.data.prefs.StatusPreferences
import com.messagekeeper.data.repository.StatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusSubTab {
    STATUS,     // 状态大屏
    EVENTS,     // 事件跟踪脉络
    AUDIT       // 账户与终端审计
}

data class ConfirmDeleteData(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

data class StatusUiState(
    val selectedTab: StatusSubTab = StatusSubTab.STATUS,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
    val serverUrl: String = "",
    val apiKey: String = "",

    // 状态数据
    val statusData: StatusDashboardData = StatusDashboardData(),
    val alert: SystemAlert? = null,
    val stats: StatusStats = StatusStats(),

    // 事件数据
    val events: List<StateEvent> = emptyList(),
    val categories: List<StateCategory> = emptyList(),
    val eventFilterType: String = "all",
    val eventFilterUrgency: String = "all",
    val eventFilterArchived: String = "0",
    val eventSearchQuery: String = "",
    val isEventsLoading: Boolean = false,

    // 账户数据
    val currentUser: StateUser? = null,
    val isAuthenticating: Boolean = false,
    val users: List<StateUser> = emptyList(),
    val activeSessionUser: StateUser? = null,
    val activeUserSessions: List<UserSession> = emptyList(),
    val isLoadingSessions: Boolean = false,

    // 附件与上传
    val isUploadingAttachments: Boolean = false,
    val pendingAttachments: List<EventAttachment> = emptyList(),

    // 最近删除 / 回收站
    val recycleBinItems: List<RecycleBinItem> = emptyList(),
    val isLoadingRecycleBin: Boolean = false,
    val showRecycleBinDialog: Boolean = false,

    // 删除前确认弹窗
    val pendingDeleteConfirm: ConfirmDeleteData? = null,

    // 弹窗状态控制
    val showConfigDialog: Boolean = false,
    val showCustomUpdateDialog: Boolean = false,
    val showCreateEventDialog: Boolean = false,
    val showAddLogDialogForEvent: StateEvent? = null,
    val showPublishAlertDialog: Boolean = false,
    val showCategoryManageDialog: Boolean = false,
    val showExportMdDialog: String? = null // Markdown 文本
)

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatusRepository.getInstance(application)
    private val prefs = StatusPreferences.getInstance(application)

    private val _uiState = MutableStateFlow(
        StatusUiState(
            serverUrl = prefs.serverUrl.value,
            apiKey = prefs.apiKey.value
        )
    )
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun switchTab(tab: StatusSubTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            StatusSubTab.STATUS -> loadStatusDashboard()
            StatusSubTab.EVENTS -> loadEvents()
            StatusSubTab.AUDIT -> loadUsers()
        }
    }

    fun refreshAll() {
        if (prefs.serverUrl.value.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEventsLoading = false,
                    isLoadingRecycleBin = false,
                    errorMessage = "尚未配置服务器地址，请点击右上角「登录/配置」填写您的服务端域名"
                )
            }
            return
        }
        loadCurrentUser()
        loadStatusDashboard()
        loadEvents()
        loadCategories()
        loadAlert()
        loadStats()
        loadRecycleBin()
        if (_uiState.value.selectedTab == StatusSubTab.AUDIT) {
            loadUsers()
        }
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            val res = repository.fetchCurrentUser()
            res.fold(
                onSuccess = { user ->
                    val isAdmin = user != null && user.level >= 4
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            selectedTab = if (!isAdmin && it.selectedTab == StatusSubTab.AUDIT) StatusSubTab.STATUS else it.selectedTab,
                            recycleBinItems = if (isAdmin) it.recycleBinItems else emptyList(),
                            showRecycleBinDialog = if (isAdmin) it.showRecycleBinDialog else false
                        )
                    }
                    if (isAdmin) {
                        loadRecycleBin()
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            selectedTab = if (it.selectedTab == StatusSubTab.AUDIT) StatusSubTab.STATUS else it.selectedTab,
                            recycleBinItems = emptyList()
                        )
                    }
                }
            )
        }
    }

    fun loginOrRegister(
        isRegister: Boolean,
        baseUrl: String,
        username: String,
        password: String,
        note: String = ""
    ) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        if (cleanUrl.isBlank()) {
            com.messagekeeper.ui.components.Sonner.error("请先填写服务器地址")
            return
        }
        if (username.isBlank() || password.isBlank()) {
            com.messagekeeper.ui.components.Sonner.error("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true) }
            val res = if (isRegister) {
                repository.registerAccount(cleanUrl, username, password, note)
            } else {
                repository.loginWithPassword(cleanUrl, username, password)
            }
            res.fold(
                onSuccess = { (msg, user) ->
                    // 自动保存 Base URL 与服务端返回的专属 API Key
                    prefs.updateConfig(cleanUrl, user.apiKey)
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            serverUrl = cleanUrl,
                            apiKey = user.apiKey,
                            currentUser = user
                        )
                    }
                    com.messagekeeper.ui.components.Sonner.success("${msg}，已自动填入 API Key")
                    refreshAll()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAuthenticating = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: if (isRegister) "注册失败" else "登录失败")
                }
            )
        }
    }

    fun logoutAccount() {
        val curUrl = prefs.serverUrl.value
        prefs.updateConfig(curUrl, "")
        _uiState.update {
            it.copy(
                apiKey = "",
                currentUser = null,
                selectedTab = if (it.selectedTab == StatusSubTab.AUDIT) StatusSubTab.STATUS else it.selectedTab,
                recycleBinItems = emptyList(),
                showRecycleBinDialog = false
            )
        }
        com.messagekeeper.ui.components.Sonner.info("已退出当前账户并清空 API Key")
        refreshAll()
    }

    // ── 1. 状态相关 ──
    fun loadStatusDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = repository.fetchStatusDashboard()
            res.fold(
                onSuccess = { data ->
                    _uiState.update { it.copy(isLoading = false, statusData = data, errorMessage = null) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "连接失败") }
                }
            )
        }
    }

    fun applyPreset(preset: StatusPreset) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val res = repository.updateStatus(
                label = preset.label,
                category = preset.category,
                description = preset.description,
                color = preset.color,
                durationMinutes = 0
            )
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("状态已切换：${preset.label}")
                    loadStatusDashboard()
                },
                onFailure = { err ->
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "切换状态失败")
                }
            )
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun submitCustomStatus(label: String, category: String, desc: String, color: String, durationMinutes: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, showCustomUpdateDialog = false) }
            val res = repository.updateStatus(label, category, desc, color, durationMinutes)
            res.fold(
                onSuccess = { msg ->
                    com.messagekeeper.ui.components.Sonner.success(msg)
                    loadStatusDashboard()
                },
                onFailure = { err ->
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "更新失败")
                }
            )
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            val res = repository.deleteHistory(id)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("已移至「最近删除」（保留30天）")
                    loadStatusDashboard()
                    loadRecycleBin()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "删除失败") }
            )
        }
    }

    // ── 2. 公告与统计 ──
    fun loadAlert() {
        viewModelScope.launch {
            val res = repository.fetchAlert()
            res.onSuccess { alert -> _uiState.update { it.copy(alert = alert) } }
        }
    }

    fun publishAlert(message: String, level: String) {
        viewModelScope.launch {
            val res = repository.publishAlert(message, level)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("公告已发布")
                    _uiState.update { it.copy(showPublishAlertDialog = false) }
                    loadAlert()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "发布失败") }
            )
        }
    }

    fun deleteAlert() {
        viewModelScope.launch {
            val res = repository.deleteAlert()
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("公告已清除")
                    _uiState.update { it.copy(alert = null) }
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "清除失败") }
            )
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val res = repository.fetchStats()
            res.onSuccess { s -> _uiState.update { it.copy(stats = s) } }
        }
    }

    // ── 3. 事件跟踪 ──
    fun loadEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEventsLoading = true) }
            val s = _uiState.value
            val res = repository.fetchEvents(
                type = s.eventFilterType,
                urgency = s.eventFilterUrgency,
                archived = s.eventFilterArchived,
                search = s.eventSearchQuery
            )
            res.fold(
                onSuccess = { list -> _uiState.update { it.copy(events = list, isEventsLoading = false) } },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message, isEventsLoading = false) } }
            )
        }
    }

    fun setEventFilters(type: String? = null, urgency: String? = null, archived: String? = null, query: String? = null) {
        _uiState.update {
            it.copy(
                eventFilterType = type ?: it.eventFilterType,
                eventFilterUrgency = urgency ?: it.eventFilterUrgency,
                eventFilterArchived = archived ?: it.eventFilterArchived,
                eventSearchQuery = query ?: it.eventSearchQuery,
                isEventsLoading = true
            )
        }
        loadEvents()
    }

    fun createEvent(title: String, type: String, urgency: Int, visibility: Int, desc: String) {
        viewModelScope.launch {
            val res = repository.createEvent(title, type, urgency, visibility, desc)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("事件已创建")
                    _uiState.update { it.copy(showCreateEventDialog = false) }
                    loadEvents()
                    loadStats()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "创建失败") }
            )
        }
    }

    fun uploadAttachments(context: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachments = true) }
            val res = repository.uploadFiles(context, uris)
            res.fold(
                onSuccess = { list ->
                    _uiState.update { current ->
                        current.copy(
                            isUploadingAttachments = false,
                            pendingAttachments = current.pendingAttachments + list
                        )
                    }
                    com.messagekeeper.ui.components.Sonner.success("已上传 ${list.size} 个附件")
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUploadingAttachments = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "上传文件失败")
                }
            )
        }
    }

    fun removePendingAttachment(attachment: EventAttachment) {
        _uiState.update { current ->
            current.copy(pendingAttachments = current.pendingAttachments.filter { it != attachment })
        }
    }

    fun clearPendingAttachments() {
        _uiState.update { it.copy(pendingAttachments = emptyList()) }
    }

    fun addEventLog(
        eventId: Long,
        logType: String,
        content: String,
        attachments: List<EventAttachment> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val res = repository.addEventLog(eventId, logType, content, attachments)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("跟进记录已追加")
                    _uiState.update { it.copy(showAddLogDialogForEvent = null, pendingAttachments = emptyList(), isUpdating = false) }
                    loadEvents()
                    loadStats()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUpdating = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "追加失败")
                }
            )
        }
    }

    fun deleteEventLog(eventId: Long, logId: Long) {
        viewModelScope.launch {
            val res = repository.deleteEventLog(eventId, logId)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("已移至「最近删除」（保留30天）")
                    loadEvents()
                    loadStats()
                    loadRecycleBin()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "删除失败") }
            )
        }
    }

    fun togglePinEvent(eventId: Long) {
        viewModelScope.launch {
            val res = repository.togglePinEvent(eventId)
            res.fold(
                onSuccess = { loadEvents() },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "操作失败") }
            )
        }
    }

    fun toggleArchiveEvent(eventId: Long) {
        viewModelScope.launch {
            val res = repository.toggleArchiveEvent(eventId)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("已切换归档状态")
                    loadEvents()
                    loadStats()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "操作失败") }
            )
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            val res = repository.deleteEvent(eventId)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("事件已移至「最近删除」（保留30天）")
                    loadEvents()
                    loadStats()
                    loadRecycleBin()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "删除失败") }
            )
        }
    }

    fun exportEventMarkdown(eventId: Long) {
        viewModelScope.launch {
            val res = repository.exportEventMarkdown(eventId)
            res.fold(
                onSuccess = { md ->
                    _uiState.update { it.copy(showExportMdDialog = md) }
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "导出失败") }
            )
        }
    }

    // ── 4. 自定义类目 ──
    fun loadCategories() {
        viewModelScope.launch {
            val res = repository.fetchCategories()
            res.onSuccess { list -> _uiState.update { it.copy(categories = list) } }
        }
    }

    fun createCategory(name: String, color: String) {
        viewModelScope.launch {
            val res = repository.createCategory(name, color)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("类目已添加")
                    loadCategories()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "添加失败") }
            )
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            val res = repository.deleteCategory(id)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("类目已删除")
                    loadCategories()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "删除失败") }
            )
        }
    }

    // ── 5. 账户与终端审计 ──
    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.fetchUsers()
            res.fold(
                onSuccess = { list -> _uiState.update { it.copy(isLoading = false, users = list) } },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "获取用户列表失败（需管理员 Key）")
                }
            )
        }
    }

    fun openUserSessions(user: StateUser) {
        _uiState.update { it.copy(activeSessionUser = user, activeUserSessions = emptyList(), isLoadingSessions = true) }
        viewModelScope.launch {
            val res = repository.fetchUserSessions(user.id)
            res.fold(
                onSuccess = { sessions ->
                    _uiState.update { it.copy(activeUserSessions = sessions, isLoadingSessions = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoadingSessions = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "获取登录审计失败")
                }
            )
        }
    }

    fun closeUserSessions() {
        _uiState.update { it.copy(activeSessionUser = null, activeUserSessions = emptyList()) }
    }

    fun updateUserLevel(userId: Long, newLevel: Int) {
        viewModelScope.launch {
            val res = repository.updateUserLevel(userId, newLevel)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("已更新账户权限等级")
                    loadUsers()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "提权更新失败") }
            )
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            val res = repository.deleteUser(userId)
            res.fold(
                onSuccess = {
                    com.messagekeeper.ui.components.Sonner.success("账户已注销")
                    loadUsers()
                },
                onFailure = { err -> com.messagekeeper.ui.components.Sonner.error(err.message ?: "删除失败") }
            )
        }
    }

    // ── 弹窗控制 ──
    fun openConfigDialog() = _uiState.update { it.copy(showConfigDialog = true, serverUrl = prefs.serverUrl.value, apiKey = prefs.apiKey.value) }
    fun closeConfigDialog() = _uiState.update { it.copy(showConfigDialog = false) }
    fun saveConfig(url: String, key: String) {
        prefs.updateConfig(url, key)
        _uiState.update { it.copy(showConfigDialog = false, serverUrl = url.trim().removeSuffix("/"), apiKey = key.trim()) }
        refreshAll()
    }

    fun openCustomUpdateDialog() = _uiState.update { it.copy(showCustomUpdateDialog = true) }
    fun closeCustomUpdateDialog() = _uiState.update { it.copy(showCustomUpdateDialog = false) }

    fun openCreateEventDialog() = _uiState.update { it.copy(showCreateEventDialog = true) }
    fun closeCreateEventDialog() = _uiState.update { it.copy(showCreateEventDialog = false) }

    fun openAddLogDialog(event: StateEvent) = _uiState.update { it.copy(showAddLogDialogForEvent = event, pendingAttachments = emptyList()) }
    fun closeAddLogDialog() = _uiState.update { it.copy(showAddLogDialogForEvent = null, pendingAttachments = emptyList()) }

    fun openPublishAlertDialog() = _uiState.update { it.copy(showPublishAlertDialog = true) }
    fun closePublishAlertDialog() = _uiState.update { it.copy(showPublishAlertDialog = false) }

    fun openCategoryManageDialog() = _uiState.update { it.copy(showCategoryManageDialog = true) }
    fun closeCategoryManageDialog() = _uiState.update { it.copy(showCategoryManageDialog = false) }

    fun closeExportMdDialog() = _uiState.update { it.copy(showExportMdDialog = null) }

    // ── 6. 最近删除 / 回收站管理 ──
    fun loadRecycleBin() {
        if (prefs.apiKey.value.isBlank()) {
            _uiState.update { it.copy(isLoadingRecycleBin = false, recycleBinItems = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecycleBin = true) }
            val res = repository.fetchRecycleBin()
            res.fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(isLoadingRecycleBin = false, recycleBinItems = list) }
                },
                onFailure = { _ ->
                    _uiState.update { it.copy(isLoadingRecycleBin = false, recycleBinItems = emptyList()) }
                }
            )
        }
    }

    fun restoreRecycleItem(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val res = repository.restoreRecycleBinItem(id)
            res.fold(
                onSuccess = { msg ->
                    com.messagekeeper.ui.components.Sonner.success(msg)
                    _uiState.update { it.copy(isUpdating = false) }
                    loadRecycleBin()
                    loadStatusDashboard()
                    loadEvents()
                    loadStats()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUpdating = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "恢复失败")
                }
            )
        }
    }

    fun deleteRecycleItem(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val res = repository.deleteRecycleBinItem(id)
            res.fold(
                onSuccess = { msg ->
                    com.messagekeeper.ui.components.Sonner.success(msg)
                    _uiState.update { it.copy(isUpdating = false) }
                    loadRecycleBin()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUpdating = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "彻底删除失败")
                }
            )
        }
    }

    fun clearRecycleBin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val res = repository.clearRecycleBin()
            res.fold(
                onSuccess = { msg ->
                    com.messagekeeper.ui.components.Sonner.success(msg)
                    _uiState.update { it.copy(isUpdating = false, recycleBinItems = emptyList()) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUpdating = false) }
                    com.messagekeeper.ui.components.Sonner.error(err.message ?: "清空失败")
                }
            )
        }
    }

    fun openRecycleBinDialog() {
        loadRecycleBin()
        _uiState.update { it.copy(showRecycleBinDialog = true) }
    }

    fun closeRecycleBinDialog() = _uiState.update { it.copy(showRecycleBinDialog = false) }

    // ── 确认删除对话框调度 ──
    fun requestConfirmDelete(title: String, message: String, onConfirm: () -> Unit) {
        _uiState.update {
            it.copy(
                pendingDeleteConfirm = ConfirmDeleteData(
                    title = title,
                    message = message,
                    onConfirm = {
                        _uiState.update { current -> current.copy(pendingDeleteConfirm = null) }
                        onConfirm()
                    }
                )
            )
        }
    }

    fun dismissConfirmDelete() {
        _uiState.update { it.copy(pendingDeleteConfirm = null) }
    }
}
