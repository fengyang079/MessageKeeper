package com.messagekeeper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.ui.theme.AccentOrange

@Composable
fun ExportDialog(
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onExportTxt: (selectedIds: List<Long>) -> Unit,
    onExportHtml: (selectedIds: List<Long>) -> Unit
) {
    val allIds = remember(conversations) { conversations.map { it.id }.toSet() }
    var selectedIds by remember(conversations) { mutableStateOf(allIds) }
    val isAllSelected = selectedIds.size == allIds.size && allIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "导出聊天记录",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "已选 ${selectedIds.size} / ${conversations.size} 个会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontSize = 12.sp
                    )
                }
                TextButton(
                    onClick = {
                        selectedIds = if (isAllSelected) emptySet() else allIds
                    }
                ) {
                    Text(
                        text = if (isAllSelected) "取消全选" else "全选",
                        color = AccentOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isChecked = selectedIds.contains(conv.id)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedIds = if (isChecked) selectedIds - conv.id else selectedIds + conv.id
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChecked) AccentOrange.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isChecked) AccentOrange.copy(alpha = 0.4f) else Color.Transparent
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + conv.id else selectedIds - conv.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AccentOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = conv.display_name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onExportTxt(selectedIds.toList()) },
                    enabled = selectedIds.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导出 TXT", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onExportHtml(selectedIds.toList()) },
                    enabled = selectedIds.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("导出 HTML", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
