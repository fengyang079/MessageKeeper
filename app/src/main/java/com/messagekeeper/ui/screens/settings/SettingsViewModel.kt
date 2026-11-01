package com.messagekeeper.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.repository.MessageRepository
import com.messagekeeper.data.prefs.RetentionPreferences
import com.messagekeeper.export.HtmlExporter
import com.messagekeeper.export.TextExporter
import com.messagekeeper.export.JsonExporter
import com.messagekeeper.export.JsonImporter
import com.messagekeeper.network.PhoneConnectionServer
import com.messagekeeper.network.PhoneTransferClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

data class SettingsUiState(
    val isExporting: Boolean = false,
    val exportResult: String? = null,
    val exportedFile: File? = null,
    val isClearing: Boolean = false,
    val serverRunning: Boolean = false,
    val pairingCode: String? = null,
    val connectedDevice: String? = null,
    val isPushing: Boolean = false,
    val pushResult: String? = null,
    /** 「历史消息自动清理」天数：0 = 关闭，默认 30 */
    val retentionDays: Int = 30
)

class SettingsViewModel(
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var phoneServer: PhoneConnectionServer? = null

    /**
     * 检查通知监听权限是否已开启
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(context.packageName) == true
    }

    /**
     * 尝试跳转各大品牌手机的自启动管理页面
     */
    fun openAutoStartSettings(context: Context) {
        val intents = listOf(
            Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "打开设置失败", e)
        }
    }

    /**
     * 打开通知监听权限设置
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("SettingsViewModel", "打开设置失败", e2)
            }
        }
    }

    suspend fun getAllMessagesSync() = withContext(Dispatchers.IO) {
        repository.getAllMessagesGroupedByConversation()
    }
    
    suspend fun fixSenderNamesToMatchConversation() = withContext(Dispatchers.IO) {
        repository.fixSenderNamesToMatchConversation()
    }

    suspend fun getConversations() = withContext(Dispatchers.IO) {
        repository.getAllConversationsSync()
    }

    /**
     * Start the Phone Connection Server (Receiver mode)
     */
    fun startPhoneServer() {
        if (phoneServer != null) return
        val code = String.format("%06d", Random.nextInt(1000000))
        phoneServer = PhoneConnectionServer(
            port = 38472,
            expectedCode = code,
            repository = repository,
            onDevicePaired = { deviceName ->
                _uiState.value = _uiState.value.copy(connectedDevice = deviceName, exportResult = "已连接: $deviceName")
            },
            onMessageReceived = { count ->
                _uiState.value = _uiState.value.copy(exportResult = "已接收 $count 条消息")
            }
        )
        try {
            phoneServer?.start()
            _uiState.value = _uiState.value.copy(serverRunning = true, pairingCode = code, connectedDevice = null)
            // 注意：这里不再自动执行 cleanCorruptedData()。
            // 开启接收端只用于"合并"对端数据，不应顺带删除本机消息（清理请手动点「一键修复」）。
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(exportResult = "启动服务失败: ${e.message}")
        }
    }

    fun cleanCorruptedData() {
        viewModelScope.launch {
            repository.cleanCorruptedData()
            _uiState.value = _uiState.value.copy(exportResult = "已清理乱码数据，群消息发送者已回填")
        }
    }

    /**
     * Stop the Phone Connection Server
     */
    fun stopPhoneServer() {
        phoneServer?.stop()
        phoneServer = null
        _uiState.value = _uiState.value.copy(serverRunning = false, pairingCode = null, connectedDevice = null)
    }

    /**
     * Push messages to another phone (Sender mode)
     */
    fun pushMessagesToPhone(ip: String, code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPushing = true, pushResult = "配对中...")
            try {
                val result = PhoneTransferClient.pair(ip, 38472, code)
                if (result.token != null) {
                    _uiState.value = _uiState.value.copy(pushResult = "配对成功，正在推送全量消息...")
                    
                    val jsonStr = repository.exportBackupJson()
                    val backupObj = JSONObject(jsonStr)

                    val success = PhoneTransferClient.pushMessages(ip, 38472, result.token, backupObj)
                    _uiState.value = _uiState.value.copy(
                        isPushing = false,
                        pushResult = if (success) "推送成功！新手机消息已按对话精准还原" else "推送失败"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isPushing = false, pushResult = "配对失败: ${result.error}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPushing = false, pushResult = "错误: ${e.message}")
            }
        }
    }

    fun exportAsJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = "正在导出 JSON...")
            val result = JsonExporter.exportMessages(context, uri, repository)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "成功导出 ${result.getOrNull()} 条消息")
            } else {
                _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "导出失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun importFromJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = "正在导入 JSON...")
            val result = JsonImporter.importMessages(context, uri, repository)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "成功导入 ${result.getOrNull()} 条消息")
            } else {
                _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "导入失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun exportAllAsTxt(context: Context, selectedIds: List<Long>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = null, exportedFile = null)
            try {
                val messages = repository.getAllMessagesGroupedByConversation()
                val filteredMessages = messages.filter { it.conversation_id in selectedIds }
                if (filteredMessages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "暂无数据可导出", exportedFile = null)
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    val exportDir = File(downloadsDir, "MessageKeeper")
                    exportDir.mkdirs()
                    val txtFile = File(exportDir, "聊天记录_${System.currentTimeMillis()}.txt")
                    TextExporter.export(filteredMessages, txtFile)
                    txtFile
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "导出成功：${file.absolutePath}",
                    exportedFile = file
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "导出失败：${e.message}",
                    exportedFile = null
                )
            }
        }
    }

    fun exportAllAsHtml(context: Context, selectedIds: List<Long>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = null, exportedFile = null)
            try {
                val messages = repository.getAllMessagesGroupedByConversation()
                val filteredMessages = messages.filter { it.conversation_id in selectedIds }
                if (filteredMessages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isExporting = false, exportResult = "暂无数据可导出", exportedFile = null)
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    val convMap = repository.getAllConversationsSync().associateBy { it.id }
                    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    val exportDir = File(downloadsDir, "MessageKeeper")
                    exportDir.mkdirs()
                    val htmlFile = File(exportDir, "聊天记录_${System.currentTimeMillis()}.html")
                    HtmlExporter.export(filteredMessages, htmlFile, convMap)
                    htmlFile
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "导出成功：${file.absolutePath}",
                    exportedFile = file
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "导出失败：${e.message}",
                    exportedFile = null
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true)
            repository.deleteAll()
            _uiState.value = _uiState.value.copy(isClearing = false, exportResult = "所有数据已清除")
        }
    }

    /**
     * 读取「历史消息自动清理」天数（0 = 关闭，默认 30）
     */
    fun loadRetentionDays() {
        val days = try {
            RetentionPreferences.getRetentionDays()
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "读取自动清理天数失败", e)
            30
        }
        _uiState.value = _uiState.value.copy(retentionDays = days)
    }

    /**
     * 保存「历史消息自动清理」天数（0/7/30/90），数据层会在下一次每日清理任务中生效
     * @return 是否保存成功（失败时由 UI 弹 Sonner 错误提示）
     */
    fun setRetentionDays(days: Int): Boolean {
        return try {
            RetentionPreferences.setRetentionDays(days)
            // 回读校验：偏好层未初始化时会静默忽略写入，这里能如实告诉用户是否保存成功
            val stored = RetentionPreferences.getRetentionDays()
            _uiState.value = _uiState.value.copy(retentionDays = stored)
            stored == days
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "保存自动清理天数失败", e)
            false
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "请求电池优化豁免失败", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        phoneServer?.stop()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel() as T
            }
        }
    }
}
