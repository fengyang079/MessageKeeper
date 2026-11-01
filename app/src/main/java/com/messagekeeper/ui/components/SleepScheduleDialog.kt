package com.messagekeeper.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagekeeper.service.SleepModeManager
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange

@Composable
fun SleepScheduleDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSleepMode by SleepModeManager.isSleepMode.collectAsState()
    val savedConfig by SleepModeManager.scheduleConfig.collectAsState()

    var scheduleEnabled by remember(savedConfig) { mutableStateOf(savedConfig.enabled) }
    var startHour by remember(savedConfig) { mutableIntStateOf(savedConfig.startHour) }
    var startMinute by remember(savedConfig) { mutableIntStateOf(savedConfig.startMinute) }
    var endHour by remember(savedConfig) { mutableIntStateOf(savedConfig.endHour) }
    var endMinute by remember(savedConfig) { mutableIntStateOf(savedConfig.endMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = "我睡觉啦 · 睡眠与定时设置",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开启后，特别关心联系人发消息时将持续响铃唤醒",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 当前睡眠状态即时开关
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (currentSleepMode) AccentOrange.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (currentSleepMode) AccentOrange.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentSleepMode) "当前状态：睡眠模式开启中 💤" else "当前状态：已清醒 ☀️",
                                color = if (currentSleepMode) AccentOrange else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (currentSleepMode) "点击右侧开关可立即退出睡眠模式" else "点击右侧开关可立即进入睡眠模式",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = currentSleepMode,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    SleepModeManager.enterSleepMode(context, sendNotification = true)
                                    Sonner.info("已进入睡眠模式")
                                } else {
                                    SleepModeManager.exitSleepMode(context, sendNotification = true)
                                    Sonner.info("已退出睡眠模式")
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentOrange)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(14.dp))

                // 定时开启开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "定时自动启用",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "按设定时间自动开启与关闭，并发送系统通知提醒",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = scheduleEnabled,
                        onCheckedChange = { scheduleEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 开始时间 & 结束时间选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimePickerCard(
                        label = "定时开启 (入睡)",
                        hour = startHour,
                        minute = startMinute,
                        enabled = scheduleEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                                startHour,
                                startMinute,
                                true
                            ).show()
                        }
                    )
                    TimePickerCard(
                        label = "定时关闭 (醒来)",
                        hour = endHour,
                        minute = endMinute,
                        enabled = scheduleEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                                endHour,
                                endMinute,
                                true
                            ).show()
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SleepModeManager.updateSchedule(
                        context = context,
                        enabled = scheduleEnabled,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute
                    )
                    onDismiss()
                    if (scheduleEnabled) {
                        Sonner.success(
                            String.format(
                                "已设定定时睡眠：%02d:%02d - %02d:%02d",
                                startHour, startMinute, endHour, endMinute
                            )
                        )
                    } else {
                        Sonner.info("已关闭定时睡眠")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("保存设置", color = Color.White, fontWeight = FontWeight.SemiBold)
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
private fun TimePickerCard(
    label: String,
    hour: Int,
    minute: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) AccentOrange.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (enabled) AccentOrange.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%02d:%02d", hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (enabled) AccentOrange else MaterialTheme.colorScheme.outline,
                fontSize = 21.sp
            )
        }
    }
}
