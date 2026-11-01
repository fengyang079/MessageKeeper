package com.messagekeeper.ui.screens.specialalert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.data.db.entity.SoundEffects
import com.messagekeeper.data.db.entity.SpecialAlert
import com.messagekeeper.notification.SpecialAlertManager
import com.messagekeeper.ui.components.EmptyState
import com.messagekeeper.ui.components.Sonner
import com.messagekeeper.ui.components.SoundPickerDialog
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.AccentRed
import com.messagekeeper.ui.theme.getAvatarColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpecialAlertScreen(
    onBack: () -> Unit,
    viewModel: SpecialAlertViewModel = viewModel(factory = SpecialAlertViewModel.Factory)
) {
    val alerts by viewModel.alerts.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf("wechat") }
    var alertName by remember { mutableStateOf("") }

    var showSoundPicker by remember { mutableStateOf(false) }
    var soundPickerTargetId by remember { mutableStateOf<Long?>(null) }
    var previewingId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部栏 ──────────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Text(
                        "特别关心提醒",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "为指定联系人或群聊设置专属提示音与睡眠唤醒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── 顶部新增规则按钮 ──────────────────────────────────
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "新增特别提醒规则",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }

        if (alerts.isEmpty()) {
            EmptyState(
                title = "暂未添加特别提醒规则",
                subtitle = "点击上方按钮添加联系人或群聊，即可定制专属提示音"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = alerts, key = { it.id }) { alert ->
                    SpecialAlertCard(
                        alert = alert,
                        isPreviewing = previewingId == alert.id,
                        onToggle = { enabled ->
                            viewModel.toggleAlert(alert.id, enabled)
                            Sonner.success(if (enabled) "已启用规则" else "已停用规则")
                        },
                        onPreview = {
                            if (previewingId == alert.id) {
                                SpecialAlertManager.stopPreview()
                                previewingId = null
                            } else {
                                SpecialAlertManager.stopPreview()
                                previewingId = null
                                val player = SpecialAlertManager.playPreview(context, alert.sound_name)
                                if (player != null) {
                                    previewingId = alert.id
                                    player.setOnCompletionListener { previewingId = null }
                                } else {
                                    Sonner.info("当前声音不支持试听")
                                }
                            }
                        },
                        onSoundPick = {
                            soundPickerTargetId = alert.id
                            showSoundPicker = true
                        },
                        onTest = {
                            viewModel.sendTestAlert(context, alert)
                            Sonner.success("已发送测试通知")
                        },
                        onDelete = {
                            SpecialAlertManager.stopPreview()
                            previewingId = null
                            viewModel.deleteAlert(alert.id)
                            Sonner.success("规则已删除")
                        }
                    )
                }
            }
        }
    }

    // ── 新增规则对话框（现代化圆角与平台胶囊选择） ────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Column {
                    Text(
                        "新增特别提醒规则",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当收到匹配的联系人或群聊消息时，将触发专属提示音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "选择来源应用",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentContext = LocalContext.current
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Platform.builtinPlatforms.filter { it != Platform.QQMUSIC }.forEach { p ->
                            val isSelected = selectedPlatform == p
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPlatform = p },
                                label = {
                                    Text(
                                        text = Platform.displayName(currentContext, p),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentOrange.copy(alpha = 0.16f),
                                    selectedLabelColor = AccentOrange
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderColor = AccentOrange,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "联系人昵称 / 备注名 / 群聊名称",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = alertName,
                        onValueChange = { alertName = it },
                        placeholder = { Text("输入需要匹配的准确名称（如：张三）") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (alertName.isNotBlank()) {
                            viewModel.addAlert(selectedPlatform, alertName.trim())
                            alertName = ""
                            showAddDialog = false
                            Sonner.success("规则已保存")
                        }
                    },
                    enabled = alertName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text("保存规则", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── 声音选择器 ────────────────────────────────────────────
    if (showSoundPicker && soundPickerTargetId != null) {
        val targetAlert = alerts.find { it.id == soundPickerTargetId }
        if (targetAlert != null) {
            SoundPickerDialog(
                currentSound = targetAlert.sound_name,
                onDismiss = { showSoundPicker = false; soundPickerTargetId = null },
                onSoundSelected = { soundName ->
                    viewModel.updateSound(targetAlert.id, soundName)
                    showSoundPicker = false
                    soundPickerTargetId = null
                    Sonner.success("已更新提示音")
                }
            )
        }
    }
}

@Composable
private fun SpecialAlertCard(
    alert: SpecialAlert,
    isPreviewing: Boolean,
    onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onSoundPick: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val soundDisplayName = if (alert.sound_name.startsWith("custom_")) {
        com.messagekeeper.notification.CustomSoundManager.getCustomSounds(context)
            .find { it.id == alert.sound_name }?.displayName ?: "自定义音频"
    } else {
        SoundEffects.sounds.find { it.name == alert.sound_name }?.displayName ?: "默认通知"
    }
    val avatarColor = getAvatarColor(alert.sender_name.hashCode())
    val platformName = Platform.displayName(context, alert.platform)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 第一行：圆形首字头像 + 联系人名 + 平台标签 + 启用开关 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = alert.sender_name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = alert.sender_name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = platformName,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (alert.is_enabled) "规则已启用 · 实时监听中" else "规则已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alert.is_enabled) MaterialTheme.colorScheme.onSurfaceVariant else AccentRed
                    )
                }

                Switch(
                    checked = alert.is_enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentOrange
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // ── 第二行：提示音选择芯片 + 试听 / 测试 / 删除按钮 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSoundPick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = AccentOrange
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = soundDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 试听按钮
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPreviewing) "停止试听" else "试听提示音",
                        tint = if (isPreviewing) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 测试通知按钮
                IconButton(
                    onClick = onTest,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "发送测试通知",
                        modifier = Modifier.size(19.dp),
                        tint = AccentOrange
                    )
                }

                // 删除按钮
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除规则",
                        modifier = Modifier.size(20.dp),
                        tint = AccentRed
                    )
                }
            }
        }
    }
}
