package com.messagekeeper.ui.screens.trash

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.db.entity.DeletedMessage
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.ui.components.EmptyState
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.AccentRed
import com.messagekeeper.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DeletedConversationGroup(
    val conversationKey: String,
    val conversationName: String,
    val platform: String,
    val messages: List<DeletedMessage>,
    val latestDeletedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedHistoryScreen(
    onBack: () -> Unit,
    viewModel: DeletedHistoryViewModel = viewModel(factory = DeletedHistoryViewModel.Factory)
) {
    val allDeleted by viewModel.deletedMessages.collectAsState()
    val selectedConvKey by viewModel.selectedConversationKey.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showRestoreAllConfirm by remember { mutableStateOf(false) }

    val groups = remember(allDeleted) {
        allDeleted
            .groupBy { it.conversation_key.ifBlank { "${it.platform}_${it.conversation_name}" } }
            .map { (key, list) ->
                val latest = list.maxByOrNull { it.deleted_at } ?: list.first()
                DeletedConversationGroup(
                    conversationKey = key,
                    conversationName = latest.conversation_name.ifBlank { "未命名会话" },
                    platform = latest.platform,
                    messages = list,
                    latestDeletedAt = list.maxOf { it.deleted_at }
                )
            }
            .sortedByDescending { it.latestDeletedAt }
    }

    val selectedGroup = remember(groups, selectedConvKey) {
        selectedConvKey?.let { key -> groups.find { it.conversationKey == key } }
    }

    val handleBack: () -> Unit = {
        if (selectedConvKey != null) {
            viewModel.selectConversation(null)
        } else {
            onBack()
        }
    }

    BackHandler(enabled = selectedConvKey != null) {
        handleBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = if (selectedGroup == null) "历史删除.trash" else "历史删除 /${selectedGroup.conversationName}",
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedGroup == null) {
                            "// 已删除消息可在此恢复 · 共 ${allDeleted.size} 条"
                        } else {
                            "// 该会话共 ${selectedGroup.messages.size} 条已删除消息"
                        },
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

        if (allDeleted.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedGroup != null) {
                            viewModel.restoreMessages(selectedGroup.messages)
                        } else {
                            showRestoreAllConfirm = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = AccentGreen
                    )
                ) {
                    Text(
                        text = if (selectedGroup != null) "> 恢复该会话全部" else "> 全部恢复",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (selectedGroup == null) {
                    Button(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = AccentRed
                        )
                    ) {
                        Text(
                            text = "> 清空回收站",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                allDeleted.isEmpty() -> {
                    EmptyState(
                        title = "暂无历史删除记录",
                        subtitle = "删除的会话或单条消息会保存在这里，可随时恢复"
                    )
                }

                selectedGroup != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = selectedGroup.messages, key = { it.id }) { msg ->
                            DeletedMessageItemCard(
                                message = msg,
                                showConversationTag = false,
                                onRestore = { viewModel.restoreMessage(msg) },
                                onPermanentDelete = { viewModel.permanentlyDeleteMessage(msg) }
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "// 点击会话查看内部已删除消息，也可直接按会话或单条恢复",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        items(items = groups, key = { it.conversationKey }) { group ->
                            DeletedConversationGroupCard(
                                group = group,
                                onClick = { viewModel.selectConversation(group.conversationKey) },
                                onRestoreGroup = { viewModel.restoreMessages(group.messages) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRestoreAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreAllConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("恢复全部已删除消息？", color = AccentGreen, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "将把回收站中的 ${allDeleted.size} 条消息恢复回原对话。",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreAllConfirm = false
                        viewModel.restoreMessages(allDeleted)
                    }
                ) {
                    Text("确认恢复", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllConfirm = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("清空历史删除？", color = AccentRed, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "清空后，回收站中的 ${allDeleted.size} 条消息将被彻底删除，无法再次恢复。",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("彻底清空", color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun DeletedConversationGroupCard(
    group: DeletedConversationGroup,
    onClick: () -> Unit,
    onRestoreGroup: () -> Unit
) {
    val context = LocalContext.current
    val previewMsg = group.messages.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.conversationName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = Platform.displayName(context, group.platform),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${group.messages.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (previewMsg != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${previewMsg.sender_name}: ${previewMsg.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "删除于 ${formatTrashTime(group.latestDeletedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[查看明细]",
                style = MaterialTheme.typography.labelSmall,
                color = AccentOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "[全部恢复]",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onRestoreGroup)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DeletedMessageItemCard(
    message: DeletedMessage,
    showConversationTag: Boolean,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.sender_name.ifBlank { "未知发送者" },
                color = AccentOrange,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTrashTime(message.timestamp),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (showConversationTag) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "会话：${message.conversation_name}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = message.content.ifBlank { "(空消息)" },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "删除于 ${formatTrashTime(message.deleted_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[恢复]",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "[彻底删除]",
                style = MaterialTheme.typography.labelSmall,
                color = AccentRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onPermanentDelete)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

private val trashTimeFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

private fun formatTrashTime(timestamp: Long): String {
    if (timestamp <= 0L) return "--"
    return (trashTimeFormat.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()))
        .format(Date(timestamp))
}

