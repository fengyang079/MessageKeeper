package com.messagekeeper.ui.screens.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.backup.BackupManager
import com.messagekeeper.ui.components.EmptyState
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.AccentRed
import com.messagekeeper.ui.theme.AccentYellow
import com.messagekeeper.ui.theme.TextMuted

/** 行内操作 / 二次确认的三种破坏性动作 */
private sealed interface ConfirmAction {
    val backup: BackupManager.BackupInfo

    data class Restore(override val backup: BackupManager.BackupInfo) : ConfirmAction
    data class Merge(override val backup: BackupManager.BackupInfo) : ConfirmAction
    data class Delete(override val backup: BackupManager.BackupInfo) : ConfirmAction
}

/**
 * 历史备份页：备份列表 →「立即备份 / 回退 / 合并 / 删除」→ 详情（会话列表 → 消息只读预览）。
 *
 * 所有落盘 / 解析都在 [BackupViewModel] 的 IO 线程完成，这里只负责渲染 StateFlow。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // 首次进入拉取列表（返回本页时 composable 重建，会重新拉一次，保证新增的「回退前」备份可见）
    LaunchedEffect(Unit) { viewModel.refresh() }

    val detail = uiState.detail
    val selectedConversation = detail?.let { current ->
        uiState.selectedConversationId?.let { id -> current.conversations.find { it.id == id } }
    }

    // 详情 > 会话消息 > 备份列表 > 退出页面
    val handleBack: () -> Unit = {
        when {
            uiState.selectedConversationId != null -> viewModel.selectConversation(null)
            detail != null -> viewModel.closeDetail()
            else -> onBack()
        }
    }

    // 标题 / 副标题：用 if-else 链（避免 when 分支里的智能转换歧义）
    val titleText: String
    val subtitleText: String
    if (detail == null) {
        titleText = "备份.db"
        subtitleText = "// 每日自动备份 · 共 ${uiState.backups.size} 份"
    } else if (selectedConversation == null) {
        titleText = "查看 /${shortBackupName(detail.fileName)}"
        subtitleText =
            "// ${detail.conversations.size} 个会话 · ${detail.totalMessages} 条消息 · ${formatBackupDate(detail.createdAt)}"
    } else {
        titleText = "查看 /${selectedConversation.displayName}"
        subtitleText = "// ${selectedConversation.messageCount} 条消息 · 只读预览"
    }

    // 系统返回键：详情内部先逐级返回，不到列表层级不离开页面
    BackHandler(enabled = detail != null) { handleBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部栏（纯黑终端风格）────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = titleText,
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // ── 全局进度条（备份 / 回退 / 合并 / 删除 / 解析中）──────────
        if (uiState.isBusy || uiState.detailLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AccentOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = if (uiState.detailLoading) "// 正在解析备份内容…" else "// ${uiState.busyText}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = AccentYellow,
                fontSize = 11.sp
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (detail != null) {
                BackupDetailContent(
                    detail = detail,
                    selectedConversation = selectedConversation,
                    onSelectConversation = { id -> viewModel.selectConversation(id) }
                )
            } else {
                BackupListContent(
                    uiState = uiState,
                    onCreateBackup = { viewModel.createManualBackup() },
                    onView = { info -> viewModel.openDetail(info.fileName) },
                    onRestore = { info -> confirmAction = ConfirmAction.Restore(info) },
                    onMerge = { info -> confirmAction = ConfirmAction.Merge(info) },
                    onDelete = { info -> confirmAction = ConfirmAction.Delete(info) }
                )
            }
        }
    }

    // ── 二次确认弹窗：回退 / 合并 / 删除 ─────────────────────────
    val action = confirmAction
    if (action != null) {
        when (action) {
            is ConfirmAction.Restore -> {
                val backup = action.backup
                ConfirmDialog(
                    title = "回退到该备份？",
                    titleColor = AccentOrange,
                    message = buildString {
                        append("// 回退前会先自动备份当前数据（生成一份「回退前」备份），再整体替换为所选备份。\n\n")
                        append("备份时间：${formatBackupDate(backup.createdAt)}\n")
                        append("包含：${backup.conversations} 个会话 / ${backup.messages} 条消息\n\n")
                        append("当前数据库中的消息与会话会被覆盖；特别提醒等设置不受影响。")
                    },
                    confirmText = "确认回退",
                    confirmColor = AccentRed,
                    onDismiss = { confirmAction = null },
                    onConfirm = {
                        confirmAction = null
                        viewModel.restoreBackup(backup.fileName)
                    }
                )
            }

            is ConfirmAction.Merge -> {
                val backup = action.backup
                ConfirmDialog(
                    title = "合并该备份？",
                    titleColor = AccentGreen,
                    message = buildString {
                        append("// 只补充当前数据里没有的消息，不会删除或覆盖已有记录；重复消息会自动去重。\n\n")
                        append("备份时间：${formatBackupDate(backup.createdAt)}\n")
                        append("包含：${backup.conversations} 个会话 / ${backup.messages} 条消息")
                    },
                    confirmText = "确认合并",
                    confirmColor = AccentGreen,
                    onDismiss = { confirmAction = null },
                    onConfirm = {
                        confirmAction = null
                        viewModel.mergeBackup(backup.fileName)
                    }
                )
            }

            is ConfirmAction.Delete -> {
                val backup = action.backup
                ConfirmDialog(
                    title = "删除该备份文件？",
                    titleColor = AccentRed,
                    message = buildString {
                        append("// 备份文件删除后不可恢复（不影响当前数据库）。\n\n")
                        append("文件：${backup.fileName}\n")
                        append("时间：${formatBackupDate(backup.createdAt)}")
                    },
                    confirmText = "确认删除",
                    confirmColor = AccentRed,
                    onDismiss = { confirmAction = null },
                    onConfirm = {
                        confirmAction = null
                        viewModel.deleteBackup(backup.fileName)
                    }
                )
            }
        }
    }
}

// ═════════════════════════════ 列表 ═════════════════════════════

@Composable
private fun BackupListContent(
    uiState: BackupUiState,
    onCreateBackup: () -> Unit,
    onView: (BackupManager.BackupInfo) -> Unit,
    onRestore: (BackupManager.BackupInfo) -> Unit,
    onMerge: (BackupManager.BackupInfo) -> Unit,
    onDelete: (BackupManager.BackupInfo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── 立即备份 ──────────────────────────────────────────
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = onCreateBackup,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = AccentOrange,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    disabledContentColor = AccentOrange.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = if (uiState.isCreating) "> 正在打包当前数据…" else "> 立即备份.sh",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "// 备份为 GZIP 压缩的本地存档 · 最多保留 20 份 / 30 天",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentOrange, strokeWidth = 2.dp)
                }

                uiState.backups.isEmpty() -> EmptyState(
                    title = "暂无备份",
                    subtitle = "每天会自动生成一份备份，也可手动立即备份"
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = uiState.backups, key = { it.fileName }) { backup ->
                        BackupRow(
                            backup = backup,
                            enabled = !uiState.isBusy,
                            onView = { onView(backup) },
                            onRestore = { onRestore(backup) },
                            onMerge = { onMerge(backup) },
                            onDelete = { onDelete(backup) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: BackupManager.BackupInfo,
    enabled: Boolean,
    onView: () -> Unit,
    onRestore: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onView)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatBackupDate(backup.createdAt),
                color = AccentOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            BackupKindBadge(backup.kind)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "[查看]",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.outline else TextMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = "会话 ${backup.conversations} · 消息 ${backup.messages} · ${formatBackupSize(backup.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(7.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BackupActionText("[回退]", AccentOrange, enabled, onRestore)
            BackupActionText("[合并]", AccentGreen, enabled, onMerge)
            BackupActionText("[删除]", AccentRed, enabled, onDelete)
        }
    }
}

@Composable
private fun BackupKindBadge(kind: String) {
    val label = when (kind) {
        "auto" -> "自动"
        "manual" -> "手动"
        "prerestore" -> "回退前"
        else -> kind.ifBlank { "未知" }
    }
    val color = when (kind) {
        "auto" -> AccentGreen
        "manual" -> AccentOrange
        "prerestore" -> AccentYellow
        else -> TextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun BackupActionText(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) color else TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

// ═════════════════════════════ 详情 ═════════════════════════════

@Composable
private fun BackupDetailContent(
    detail: BackupDetail,
    selectedConversation: BackupConversation?,
    onSelectConversation: (Long) -> Unit
) {
    if (selectedConversation != null) {
        val messages = detail.messagesByConversation[selectedConversation.id].orEmpty()
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "// 最后一条：${selectedConversation.lastMessage.ifBlank { "(无消息预览)" }}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (selectedConversation.messageCount > messages.size) {
                Text(
                    text = "// 已截断：仅显示前 ${messages.size} 条（共 ${selectedConversation.messageCount} 条）",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentYellow,
                    fontSize = 10.sp
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyState(title = "该会话没有消息", subtitle = "备份中该会话的消息记录为空")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
                    ) {
                        items(items = messages) { message ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                BackupMessageRow(message)
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        if (detail.conversations.isEmpty()) {
            EmptyState(title = "备份内没有会话", subtitle = "这份备份只包含空数据")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "// 只读预览 · 点击会话查看消息（每会话最多 $MAX_PREVIEW_MESSAGES_PER_CONVERSATION 条）",
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                items(items = detail.conversations, key = { it.id }) { conversation ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BackupConversationRow(
                            conversation = conversation,
                            onClick = { onSelectConversation(conversation.id) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupConversationRow(
    conversation: BackupConversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "消息 ${conversation.messageCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = conversation.lastMessage.ifBlank { "(无消息预览)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formatBackupDate(conversation.lastMessageTime),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "[查看]",
            style = MaterialTheme.typography.labelSmall,
            color = AccentOrange,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BackupMessageRow(message: BackupMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.senderName.ifBlank { "未知发送者" },
                color = if (message.isRecalled) TextMuted else AccentGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatBackupMessageTime(message.timestamp),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = message.content.ifBlank { "(空消息)" },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        if (message.isRecalled) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "[已撤回]",
                color = AccentRed,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ═════════════════════════════ 通用 ═════════════════════════════

@Composable
private fun ConfirmDialog(
    title: String,
    titleColor: Color,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = confirmColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/** backup_manual_20261101_112600.json.gz → manual_20261101_112600 */
private fun shortBackupName(fileName: String): String =
    fileName.removePrefix("backup_").removeSuffix(".json.gz").removeSuffix(".gz")
