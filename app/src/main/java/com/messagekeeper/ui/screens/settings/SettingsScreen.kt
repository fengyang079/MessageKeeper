package com.messagekeeper.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.prefs.ThemeMode
import com.messagekeeper.data.prefs.ThemePreferences
import com.messagekeeper.ui.components.ExportDialog
import com.messagekeeper.ui.components.Sonner
import com.messagekeeper.ui.theme.AccentBlue
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.AccentRed
import com.messagekeeper.ui.theme.AccentYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSpecialAlerts: () -> Unit,
    onNavigateToBackups: () -> Unit,
    onNavigateToDeletedHistory: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAutoStartDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showSleepScheduleDialog by remember { mutableStateOf(false) }
    val sleepSchedule by com.messagekeeper.service.SleepModeManager.scheduleConfig.collectAsState()

    // 读取「历史消息自动清理」当前设置（0 = 关闭）
    LaunchedEffect(Unit) { viewModel.loadRetentionDays() }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportAsJson(context, it) }
    }
    val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromJson(context, it) }
    }

    var showPairDialog by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }

    // Theme state
    val themePrefs = remember { ThemePreferences.getInstance(context) }
    val currentTheme by themePrefs.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    var conversationsList by remember { mutableStateOf<List<com.messagekeeper.data.db.entity.Conversation>>(emptyList()) }
    var showAppsDialog by remember { mutableStateOf(false) }

    var isListenerEnabled by remember { mutableStateOf(viewModel.isNotificationListenerEnabled(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = viewModel.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAutoStartDialog) {
        AutoStartGuideDialog(
            onOpenSettings = {
                viewModel.openAutoStartSettings(context)
                showAutoStartDialog = false
            },
            onDismiss = { showAutoStartDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 32.dp)
    ) {
        // ── 页面顶部标题 ──────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "个性化外观、消息提醒规则、数据备份与后台保活",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── 权限状态横幅卡片 ──────────────────────────────────
        PermissionCard(
            isGranted = isListenerEnabled,
            onCheckPermission = {
                viewModel.openNotificationSettings(context)
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ── 外观与主题 (直接展示精美三选一卡片) ────────────────
        SectionLabel("外观与主题")
        ThemeSelectorCard(
            currentTheme = currentTheme,
            onSelectTheme = { mode ->
                if (mode != currentTheme) {
                    themePrefs.setThemeMode(mode)
                    val label = when (mode) {
                        ThemeMode.LIGHT -> "明亮模式"
                        ThemeMode.DARK -> "暗色模式"
                        ThemeMode.SYSTEM -> "跟随系统"
                    }
                    Sonner.success("已切换到$label")
                }
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ── 通知与提醒设置 ────────────────────────────────────
        SectionLabel("通知与提醒")
        SettingsGroupCard {
            SettingsItem(
                icon = Icons.Default.NotificationsActive,
                iconTint = AccentOrange,
                key = "特别关心提醒",
                value = "管理规则",
                description = "自定义特定联系人的专属提示音与弹窗提醒",
                onClick = onNavigateToSpecialAlerts,
                valueColor = AccentOrange
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.Bedtime,
                iconTint = if (sleepSchedule.enabled) AccentGreen else AccentBlue,
                key = "我睡觉啦 (定时睡眠)",
                value = if (sleepSchedule.enabled) "定时 ${sleepSchedule.formatRange()}" else "手动 / 定时",
                description = "设置定时开启与关闭睡眠模式，支持自动发送状态通知",
                onClick = { showSleepScheduleDialog = true },
                valueColor = if (sleepSchedule.enabled) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.Apps,
                iconTint = AccentBlue,
                key = "监听应用管理",
                value = "选择应用",
                description = "选择需要捕获通知消息的应用（微信、QQ、抖音等）",
                onClick = { showAppsDialog = true },
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── 数据备份与管理 ────────────────────────────────────
        SectionLabel("数据管理与备份")
        SettingsGroupCard {
            SettingsItem(
                icon = Icons.Default.Restore,
                iconTint = AccentOrange,
                key = "历史删除恢复",
                value = "查看 / 恢复",
                description = "查看已删除的会话与消息，支持单条或按会话恢复",
                onClick = onNavigateToDeletedHistory,
                valueColor = AccentOrange
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.History,
                iconTint = AccentBlue,
                key = "每日自动备份",
                value = "查看 / 回退",
                description = "管理每日自动备份快照，支持一键合并或回退数据",
                onClick = onNavigateToBackups,
                valueColor = AccentBlue
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.AutoDelete,
                iconTint = AccentYellow,
                key = "备份自动清理",
                value = if (uiState.retentionDays <= 0) "永不清理" else "${uiState.retentionDays} 天",
                description = "自动清理超过设定天数的旧备份文件（不影响聊天数据库）",
                onClick = { showRetentionDialog = true },
                valueColor = if (uiState.retentionDays <= 0) MaterialTheme.colorScheme.onSurfaceVariant else AccentYellow
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.FileUpload,
                iconTint = AccentGreen,
                key = "导出全量备份 (JSON)",
                value = "立即备份",
                description = "将所有会话、消息及置顶状态完整导出为 JSON 文件",
                onClick = { exportJsonLauncher.launch("messages_backup.json") },
                valueColor = AccentGreen
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.FileDownload,
                iconTint = AccentOrange,
                key = "导入全量备份 (JSON)",
                value = "恢复数据",
                description = "从本地 JSON 备份文件中恢复聊天记录",
                onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
                valueColor = AccentOrange
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.IosShare,
                iconTint = AccentGreen,
                key = "导出聊天记录 (TXT / HTML)",
                value = "选择会话",
                description = "将选定会话导出为精美的交互式 HTML 网页或 TXT 文本",
                onClick = {
                    scope.launch {
                        conversationsList = viewModel.getConversations()
                        showExportDialog = true
                    }
                },
                valueColor = AccentGreen
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.CleaningServices,
                iconTint = AccentBlue,
                key = "清理乱码与修复发送者",
                value = "一键修复",
                description = "自动清理异常乱码记录，并修复群聊消息的发送者归属",
                onClick = { viewModel.cleanCorruptedData() },
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                iconTint = AccentRed,
                key = "清空所有数据",
                value = "危险操作",
                description = "永久删除应用内所有消息记录（不可撤销）",
                onClick = { showDeleteDialog = true },
                valueColor = AccentRed
            )
        }

        // ── 导出结果提示卡片 ──────────────────────────────────
        if (uiState.exportResult != null || uiState.isExporting) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.isExporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentOrange,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在导出聊天记录，请稍候...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (uiState.exportResult != null) {
                        Text(
                            text = uiState.exportResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.exportedFile != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                uiState.exportedFile!!
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                    uri,
                                                    if (uiState.exportedFile!!.extension == "html") "text/html" else "text/plain"
                                                )
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Sonner.error("无法打开文件: ${e.message}")
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                                ) {
                                    Text("立即打开", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                uiState.exportedFile!!
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = if (uiState.exportedFile!!.extension == "html") "text/html" else "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享文件"))
                                        } catch (e: Exception) {
                                            Sonner.error("无法分享文件: ${e.message}")
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("分享文件", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── 手机互传 ──────────────────────────────────────────
        val localIp = remember(uiState.serverRunning) { getDeviceLocalIp(context) }
        SectionLabel("局域网手机互传")
        SettingsGroupCard {
            if (uiState.serverRunning) {
                SettingsItem(
                    icon = Icons.Default.PhonelinkRing,
                    iconTint = AccentGreen,
                    key = "接收模式运行中 (新手机)",
                    value = "点击停止",
                    description = "本机 IP: $localIp  |  配对码: ${uiState.pairingCode}",
                    onClick = { viewModel.stopPhoneServer() },
                    valueColor = AccentGreen
                )
            } else {
                SettingsItem(
                    icon = Icons.Default.PhonelinkSetup,
                    iconTint = AccentBlue,
                    key = "作为接收端 (新手机)",
                    value = "启动服务",
                    description = "开启后显示本机局域网 IP 与配对码，等待旧手机发送数据",
                    onClick = { viewModel.startPhoneServer() },
                    valueColor = AccentBlue
                )
            }
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.PhonelinkRing,
                iconTint = AccentOrange,
                key = "作为发送端 (旧手机)",
                value = "连接发送",
                description = "输入新手机上显示的 IP 地址和 6 位配对码进行迁移",
                onClick = { showPairDialog = true },
                valueColor = AccentOrange
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── 后台保活与防杀 ────────────────────────────────────
        SectionLabel("后台保护与防杀")
        SettingsGroupCard {
            SettingsItem(
                icon = Icons.Default.AccessibilityNew,
                iconTint = AccentOrange,
                key = "无障碍防杀服务",
                value = "前往开启",
                description = "开启无障碍服务以提升进程优先级，防止后台被系统清理",
                onClick = {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                        Sonner.info("请在已下载的应用中找到并开启消息助手无障碍服务。")
                    } catch (e: Exception) {
                        Sonner.error("无法打开无障碍设置。")
                    }
                },
                valueColor = AccentOrange
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.BatteryChargingFull,
                iconTint = AccentGreen,
                key = "忽略电池优化",
                value = "设置白名单",
                description = "允许应用在后台不受限制运行，避免息屏后漏记消息",
                onClick = {
                    viewModel.requestBatteryOptimizationExemption(context)
                    Sonner.info("请在系统设置中允许忽略电池优化。")
                },
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.RocketLaunch,
                iconTint = AccentBlue,
                key = "厂商自启动与锁定指南",
                value = "查看教程",
                description = "针对华为、小米、OPPO、vivo 等机型的后台保活设置说明",
                onClick = { showAutoStartDialog = true },
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── 调试与系统工具 ────────────────────────────────────
        SectionLabel("工具与调试")
        SettingsGroupCard {
            SettingsItem(
                icon = Icons.Default.BugReport,
                iconTint = AccentGreen,
                key = "查看运行日志",
                value = "Logcat",
                description = "查看应用最近运行日志，方便排查问题或复制反馈",
                onClick = { showLogDialog = true },
                valueColor = AccentGreen
            )
            SettingsDivider()
            SettingsItem(
                icon = Icons.Default.Build,
                iconTint = AccentOrange,
                key = "测试全局通知弹窗",
                value = "点击预览",
                description = "预览顶部堆叠式提示条的三种状态（成功 / 失败 / 提示）",
                onClick = {
                    Sonner.success("操作已完成。")
                    Sonner.error("操作失败，请稍后重试")
                    Sonner.info("这是一条普通提示。")
                },
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── 底部关于信息 ──────────────────────────────────────
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2.6"
            } catch (e: Exception) {
                "1.2.6"
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "消息助手 MessageKeeper v$versionName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "所有聊天数据均加密存储于本地设备，绝不上传云端",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showExportDialog) {
        ExportDialog(
            conversations = conversationsList,
            onDismiss = { showExportDialog = false },
            onExportTxt = { selectedIds ->
                showExportDialog = false
                viewModel.exportAllAsTxt(context, selectedIds)
                Sonner.info("正在导出 TXT...")
            },
            onExportHtml = { selectedIds ->
                showExportDialog = false
                viewModel.exportAllAsHtml(context, selectedIds)
                Sonner.info("正在导出 HTML...")
            }
        )
    }

    var targetIp by remember { mutableStateOf("") }
    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isPushing) showPairDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "连接新手机并发送数据",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "请确保两台手机连接在同一 Wi-Fi 下，并输入新手机上显示的 IP 地址和 6 位配对码。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("接收端 IP 地址 (如 192.168.1.100)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 6) pairingCode = it },
                        label = { Text("6 位数字配对码") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange
                        )
                    )
                    if (uiState.pushResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(uiState.pushResult!!, color = AccentOrange, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pairingCode.length == 6 && targetIp.isNotBlank()) {
                            viewModel.pushMessagesToPhone(targetIp, pairingCode)
                        }
                    },
                    enabled = !uiState.isPushing && pairingCode.length == 6 && targetIp.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text(if (uiState.isPushing) "发送中…" else "连接并发送", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!uiState.isPushing) showPairDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "确认清空所有数据？",
                    color = AccentRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "此操作将永久删除当前应用数据库中的所有会话与聊天记录，且无法撤销。建议在清空前先导出 JSON 备份。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.clearAllData()
                        Sonner.success("所有数据已清空")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("确认清空", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("选择主题模式", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        val themeLabel = when (mode) {
                            ThemeMode.LIGHT -> "明亮模式"
                            ThemeMode.DARK -> "暗色模式"
                            ThemeMode.SYSTEM -> "跟随系统"
                        }
                        val isSelected = mode == currentTheme
                        val selectTheme: () -> Unit = {
                            themePrefs.setThemeMode(mode)
                            showThemeDialog = false
                            Sonner.success("已切换到$themeLabel")
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = isSelected, onClick = selectTheme),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentOrange.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = selectTheme,
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentOrange)
                                )
                                Text(
                                    text = themeLabel,
                                    modifier = Modifier.padding(start = 8.dp),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showRetentionDialog) {
        val retentionOptions = listOf(
            0 to "关闭（永不自动清理）",
            7 to "保留最近 7 天",
            30 to "保留最近 30 天",
            90 to "保留最近 90 天"
        )
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "历史备份自动清理",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "超过设定天数的历史备份文件会在每日后台任务中自动清除（仅清理备份文件，数据库内的聊天记录不受影响）。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        retentionOptions.forEach { (days, label) ->
                            val isSelected = uiState.retentionDays == days
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            val ok = viewModel.setRetentionDays(days)
                                            showRetentionDialog = false
                                            if (ok) {
                                                if (days <= 0) Sonner.success("已关闭自动清理")
                                                else Sonner.success("已设为保留 $days 天")
                                            } else {
                                                Sonner.error("保存自动清理设置失败")
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AccentOrange.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentOrange.copy(alpha = 0.5f) else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = AccentOrange)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRetentionDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showLogDialog) {
        LogViewerDialog(onDismiss = { showLogDialog = false })
    }

    if (showAppsDialog) {
        MonitoredAppsDialog(onDismiss = { showAppsDialog = false })
    }

    if (showSleepScheduleDialog) {
        com.messagekeeper.ui.components.SleepScheduleDialog(
            onDismiss = { showSleepScheduleDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        thickness = 0.8.dp
    )
}

@Composable
private fun ThemeSelectorCard(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentOrange.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "主题模式",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "实时切换明亮、暗黑或跟随系统外观",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val options = listOf(
                Triple(ThemeMode.SYSTEM, "跟随系统", Icons.Default.BrightnessAuto),
                Triple(ThemeMode.LIGHT, "明亮模式", Icons.Default.LightMode),
                Triple(ThemeMode.DARK, "暗色模式", Icons.Default.DarkMode)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { (mode, title, icon) ->
                    val selected = currentTheme == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTheme(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) AccentOrange.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) AccentOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (selected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AccentOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(isGranted: Boolean, onCheckPermission: () -> Unit) {
    val accent = if (isGranted) AccentGreen else AccentRed
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCheckPermission),
        shape = RoundedCornerShape(20.dp),
        color = if (isGranted) AccentGreen.copy(alpha = 0.10f) else AccentRed.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGranted) "通知监听服务运行中" else "通知监听服务未开启",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isGranted) "正在实时保护并记录您的通知消息"
                    else "点击前往系统设置授予通知读取权限，否则无法记录消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.18f)
            ) {
                Text(
                    text = if (isGranted) "已启用" else "去开启",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconTint: Color,
    key: String,
    value: String,
    description: String,
    onClick: () -> Unit,
    valueColor: Color = AccentOrange
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = key,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AutoStartGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "自启动与后台保活指南",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "为了防止系统杀死后台消息监听服务，请根据您的手机品牌开启对应权限：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GuideBrandCard(
                    brand = "华为 / 荣耀 (HarmonyOS / MagicOS)",
                    desc = "设置 → 应用和服务 → 应用启动管理 → 找到消息助手，关闭自动管理，手动开启「允许自启动」「允许关联启动」和「允许后台活动」。",
                    accent = AccentOrange
                )
                GuideBrandCard(
                    brand = "小米 / 红米 (HyperOS / MIUI)",
                    desc = "设置 → 应用设置 → 授权管理 → 自启动管理 → 开启消息助手；并在电池设置中将省电策略设为「无限制」。",
                    accent = AccentBlue
                )
                GuideBrandCard(
                    brand = "OPPO / vivo / 一加 / iQOO",
                    desc = "设置 → 电池 / 应用管理 → 后台耗电管理 / 应用自启动 → 允许消息助手后台高耗电运行及自启动。",
                    accent = AccentGreen
                )
                GuideBrandCard(
                    brand = "多任务卡片加锁（通用推荐）",
                    desc = "打开系统最近任务视图，长按或下拉「消息助手」卡片，点击锁定图标，避免一键清理后台时误杀。",
                    accent = AccentYellow
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("前往系统设置", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun GuideBrandCard(brand: String, desc: String, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = brand,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 获取设备在局域网内的 IPv4 地址
 */
private fun getDeviceLocalIp(context: Context): String {
    // 1. 尝试 WifiManager 获取 IP
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            val ip = String.format(
                java.util.Locale.getDefault(),
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            if (ip != "0.0.0.0" && ip != "127.0.0.1") return ip
        }
    } catch (_: Exception) {}

    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return "192.168.x.x (请检查Wi-Fi)"
        val candidateIps = mutableListOf<Pair<String, String>>()

        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (!intf.isUp || intf.isLoopback) continue
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (inetAddress is java.net.Inet4Address && !inetAddress.isLoopbackAddress) {
                    val host = inetAddress.hostAddress ?: continue
                    if (host.isNotBlank() && host != "127.0.0.1" && host != "0.0.0.0") {
                        candidateIps.add(Pair(intf.name.lowercase(), host))
                    }
                }
            }
        }

        // 优先匹配包含 wlan, eth, ap, rndis 的网卡 (Wi-Fi网卡通常为 wlan0)
        val wifiIp = candidateIps.find { it.first.contains("wlan") || it.first.contains("eth") || it.first.contains("ap") || it.first.contains("rndis") }
        if (wifiIp != null) return wifiIp.second

        // 退而求其次返回第一个有效 IPv4
        if (candidateIps.isNotEmpty()) return candidateIps.first().second

    } catch (e: Exception) {
        e.printStackTrace()
    }

    return "192.168.x.x (请检查Wi-Fi)"
}

@Composable
private fun MonitoredAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<android.content.pm.ApplicationInfo>>(emptyList()) }
    var monitoredApps by remember {
        mutableStateOf(com.messagekeeper.data.prefs.MonitoredAppsManager.getMonitoredApps(context).map { it.packageName }.toSet())
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString() }

            // 将已被监听的应用排在前面
            val sortedApps = apps.sortedByDescending { monitoredApps.contains(it.packageName) }
            installedApps = sortedApps
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            val label = pm.getApplicationLabel(it).toString()
            label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    "监听应用管理",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "已启用 ${monitoredApps.size} 个应用",
                    color = AccentOrange,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentOrange)
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索应用名称或包名...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps.size) { index ->
                            val app = filteredApps[index]
                            val isChecked = monitoredApps.contains(app.packageName)
                            val appName = pm.getApplicationLabel(app).toString()

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val newSet = monitoredApps.toMutableSet()
                                        if (isChecked) {
                                            newSet.remove(app.packageName)
                                            com.messagekeeper.data.prefs.MonitoredAppsManager.removeApp(context, app.packageName)
                                        } else {
                                            newSet.add(app.packageName)
                                            com.messagekeeper.data.prefs.MonitoredAppsManager.addApp(context, app.packageName, appName)
                                        }
                                        monitoredApps = newSet
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) AccentOrange.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null,
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = AccentOrange)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appName,
                                            fontSize = 14.sp,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("完成", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerDialog(onDismiss: () -> Unit) {
    var logText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        logText = kotlinx.coroutines.withContext(Dispatchers.IO) { readRecentLogText(context) }
        isLoading = false
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("运行日志", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "最多显示最近 1,000 行或 120 KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val logDir = java.io.File(context.getExternalFilesDir(null), "logs")
                                logDir.listFiles()?.filter { it.name.startsWith("log_start_") }?.forEach { it.delete() }
                                logText = "\u65e5\u5fd7\u5df2\u6e05\u7a7a"
                            } catch (_: Exception) {
                                logText = "\u6e05\u7406\u65e5\u5fd7\u5931\u8d25"
                            }
                        }
                    }
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MessageKeeper Log", logText))
                        Sonner.success("已复制当前显示的日志")
                    },
                    enabled = logText.isNotBlank() && !isLoading
                ) { Text("复制") }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 520.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_LOG_LINES = 1_000
private const val MAX_LOG_CHARACTERS = 120_000
private const val MAX_LOG_LINE_CHARACTERS = 2_000

private fun readRecentLogText(context: Context): String {
    val logDir = java.io.File(context.getExternalFilesDir(null), "logs")
    val recentLines = java.util.ArrayDeque<String>()
    var totalCharacters = 0

    for (index in 3 downTo 1) {
        val file = java.io.File(logDir, "log_start_$index.txt")
        if (!file.isFile) continue
        file.bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = "[$index] ${rawLine.take(MAX_LOG_LINE_CHARACTERS)}"
                recentLines.addLast(line)
                totalCharacters += line.length
                while (recentLines.size > MAX_LOG_LINES || totalCharacters > MAX_LOG_CHARACTERS) {
                    totalCharacters -= recentLines.removeFirst().length
                }
            }
        }
    }

    if (recentLines.isEmpty()) return "暂无日志"
    return buildString {
        append("显示最近 ${recentLines.size} 行（上限 1,000 行 / 120 KB）\n\n")
        recentLines.forEach { append(it).append('\n') }
    }
}
