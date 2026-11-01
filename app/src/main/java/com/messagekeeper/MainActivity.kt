package com.messagekeeper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.messagekeeper.data.prefs.ThemeMode
import com.messagekeeper.data.prefs.ThemePreferences
import com.messagekeeper.service.ForegroundKeepAliveService
import com.messagekeeper.service.MessageNotificationListener
import com.messagekeeper.ui.navigation.MessageKeeperNavHost
import com.messagekeeper.ui.theme.MessageKeeperTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sendBroadcast(Intent(ACTION_SYNC_NOTIFICATIONS).setPackage(packageName))
        if (isNotificationListenerEnabled()) startKeepAliveService()

        setContent {
            val themePreferences = remember { ThemePreferences.getInstance(this) }
            val themeMode by themePreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MessageKeeperTheme(darkTheme = darkTheme) {
                var missingNotification by remember { mutableStateOf(!isNotificationListenerEnabled()) }
                var missingAccessibility by remember { mutableStateOf(!isAccessibilityServiceEnabled()) }
                var batteryOptimizationExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations()) }
                var appNotificationsEnabled by remember { mutableStateOf(hasPostNotificationPermission()) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            missingNotification = !isNotificationListenerEnabled()
                            missingAccessibility = !isAccessibilityServiceEnabled()
                            batteryOptimizationExempt = isIgnoringBatteryOptimizations()
                            appNotificationsEnabled = hasPostNotificationPermission()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val prefs = remember {
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                }
                var permissionSetupSkipped by rememberSaveable {
                    mutableStateOf(prefs.getBoolean(PERMISSION_SETUP_DONE_KEY, false))
                }
                val permissionSetupVisible = !permissionSetupSkipped &&
                    (missingNotification || missingAccessibility)

                if (!permissionSetupVisible && !permissionSetupSkipped) {
                    prefs.edit().putBoolean(PERMISSION_SETUP_DONE_KEY, true).apply()
                    permissionSetupSkipped = true
                }

                if (!permissionSetupVisible) {
                    var showUpdateNotes by remember {
                        mutableStateOf(!prefs.getBoolean(UPDATE_NOTES_KEY, false))
                    }
                    if (showUpdateNotes) {
                        UpdateNotesSheet {
                            prefs.edit().putBoolean(UPDATE_NOTES_KEY, true).apply()
                            showUpdateNotes = false
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (permissionSetupVisible) {
                        com.messagekeeper.ui.screens.onboarding.PermissionSetupScreen(
                            notificationEnabled = !missingNotification,
                            appNotificationsEnabled = appNotificationsEnabled,
                            accessibilityEnabled = !missingAccessibility,
                            batteryOptimizationExempt = batteryOptimizationExempt,
                            onOpenNotificationSettings = ::openNotificationListenerSettings,
                            onRequestAppNotifications = ::requestNotificationPermission,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onOpenBatterySettings = ::openBatteryOptimizationSettings,
                            onContinueWithoutSetup = {
                                prefs.edit().putBoolean(PERMISSION_SETUP_DONE_KEY, true).apply()
                                permissionSetupSkipped = true
                            }
                        )
                    } else {
                        MessageKeeperNavHost()
                    }

                    com.messagekeeper.ui.components.SonnerHost(
                        darkTheme = darkTheme,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isNotificationListenerEnabled()) return

        Thread {
            try {
                val component = android.content.ComponentName(this, MessageNotificationListener::class.java)
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.service.notification.NotificationListenerService.requestRebind(component)
                }
                sendBroadcast(Intent(ACTION_SYNC_NOTIFICATIONS).setPackage(packageName))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh notification listener", e)
            }
        }.start()
        startKeepAliveService()
    }

    private fun startKeepAliveService() {
        try {
            startForegroundService(Intent(this, ForegroundKeepAliveService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start keep-alive service", e)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPostNotificationPermission()) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasPostNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isNotificationListenerEnabled(): Boolean = try {
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
    } catch (e: Exception) {
        Log.e(TAG, "Unable to read notification-listener access", e)
        false
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open notification-listener settings", e)
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open battery-optimization settings", e)
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read battery-optimization status", e)
            false
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedClass = "com.messagekeeper.service.KeepAliveAccessibilityService"
        return try {
            val services = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            services.split(':').any { service ->
                service.contains(packageName, ignoreCase = true) &&
                    service.contains(expectedClass.substringAfterLast('.'), ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read accessibility-service status", e)
            false
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val ACTION_SYNC_NOTIFICATIONS = "com.messagekeeper.SYNC_NOTIFICATIONS"
        const val UPDATE_NOTES_KEY = "seen_update_1.2.0"
        const val PERMISSION_SETUP_DONE_KEY = "permission_setup_done"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun UpdateNotesSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "MessageKeeper 更新",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "最近更新了聊天列表、消息检索和应用设置界面。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            UpdateFeature("聊天列表", "支持更直接的会话操作和滑动删除，可在提示中撤销。")
            UpdateFeature("消息检索", "可按联系人、日期和消息内容查找本地记录。")
            UpdateFeature("设置与分析", "优化了设置弹层、日志查看和统计页面的加载状态。")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("知道了")
            }
        }
    }
}

@Composable
private fun UpdateFeature(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
