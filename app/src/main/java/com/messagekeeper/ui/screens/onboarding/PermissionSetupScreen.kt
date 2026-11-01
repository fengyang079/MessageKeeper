package com.messagekeeper.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionSetupScreen(
    notificationEnabled: Boolean,
    appNotificationsEnabled: Boolean,
    accessibilityEnabled: Boolean,
    batteryOptimizationExempt: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onRequestAppNotifications: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onContinueWithoutSetup: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "完成基础设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "开启通知访问后，MessageKeeper 才能整理你收到的消息。你可以随时在系统设置中关闭权限。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            PermissionStepCard(
                title = "通知访问",
                description = "用于读取通知并保存本地消息记录。",
                enabled = notificationEnabled,
                required = true,
                actionLabel = "前往系统设置",
                onOpenSettings = onOpenNotificationSettings
            )
            Spacer(Modifier.height(12.dp))
            PermissionStepCard(
                title = "后台守护",
                description = "使用无障碍服务帮助应用在后台保持运行；不需要时可以跳过。",
                enabled = accessibilityEnabled,
                required = false,
                actionLabel = "开启后台守护",
                onOpenSettings = onOpenAccessibilitySettings
            )
            Spacer(Modifier.height(12.dp))
            PermissionStepCard(
                title = "电池优化",
                description = "允许后台运行可减少系统暂停消息监听的情况；这是可选设置。",
                enabled = batteryOptimizationExempt,
                required = false,
                actionLabel = "允许后台运行",
                onOpenSettings = onOpenBatterySettings
            )
            Spacer(Modifier.height(12.dp))
            PermissionStepCard(
                title = "提醒通知",
                description = "用于显示特别提醒和后台状态；不开启也能保存消息。",
                enabled = appNotificationsEnabled,
                required = false,
                actionLabel = "允许提醒通知",
                onOpenSettings = onRequestAppNotifications
            )

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onContinueWithoutSetup) {
                Text("暂不设置，继续使用")
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    description: String,
    enabled: Boolean,
    required: Boolean,
    actionLabel: String,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        enabled -> "已开启"
                        required -> "必需"
                        else -> "可选"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!enabled) {
                Spacer(Modifier.height(12.dp))
                if (required) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(actionLabel) }
                } else {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}
