package com.messagekeeper.ui.screens.status

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.models.EventAttachment
import com.messagekeeper.data.models.RecycleBinItem
import com.messagekeeper.data.models.RemoteStatus
import com.messagekeeper.data.models.StateCategory
import com.messagekeeper.data.models.StateEvent
import com.messagekeeper.data.models.StateUser
import com.messagekeeper.data.models.StatusHistory
import com.messagekeeper.data.models.StatusPreset
import com.messagekeeper.data.models.StatusStats
import com.messagekeeper.data.models.SystemAlert
import com.messagekeeper.data.models.UserSession
import com.messagekeeper.ui.theme.AccentBlue
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.AccentRed
import com.messagekeeper.ui.theme.AccentYellow

// 8 种低饱和度标准色板
val Palette8 = listOf(
    Pair("雾蓝", Color(0xFFDCE9F5)),
    Pair("薄荷绿", Color(0xFFDDEEDC)),
    Pair("青灰", Color(0xFFD9ECE9)),
    Pair("淡紫", Color(0xFFE6DFF2)),
    Pair("豆沙粉", Color(0xFFF2DFE3)),
    Pair("杏橙", Color(0xFFF4E4D3)),
    Pair("燕麦黄", Color(0xFFF1ECCF)),
    Pair("浅灰", Color(0xFFE6E6E6))
)

val UrgencyColors = mapOf(
    1 to Color(0xFF10B981),
    2 to Color(0xFF3B82F6),
    3 to Color(0xFFF59E0B),
    4 to Color(0xFFF97316),
    5 to Color(0xFFEF4444)
)

val UrgencyLabels = mapOf(
    1 to "L1 低优",
    2 to "L2 常规",
    3 to "L3 留意",
    4 to "L4 重要",
    5 to "L5 紧急"
)

val LevelLabels = mapOf(
    0 to "L0 受限",
    1 to "L1 一级",
    2 to "L2 二级",
    3 to "L3 三级",
    4 to "管理员"
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFDCE9F5)): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun formatDisplayTime(dateStr: String): String {
    if (dateStr.isBlank()) return ""
    return try {
        val clean = dateStr.replace("T", " ").replace("Z", "")
        if (clean.length >= 16 && clean.contains("-")) {
            val parts = clean.split(" ")
            val dateParts = parts[0].split("-")
            if (dateParts.size == 3) {
                "${dateParts[1]}/${dateParts[2]} ${parts[1].substring(0, 5)}"
            } else clean.substring(0, 16)
        } else if (clean.length >= 16) {
            clean.substring(0, 16)
        } else {
            clean
        }
    } catch (_: Exception) {
        dateStr
    }
}

// ── 变形流体进度条 (Indeterminate Morphing Fluid Progress Bar) ─────────────
@Composable
fun MorphingProgressBar(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val transition = rememberInfiniteTransition(label = "morphing_progress")

        val headAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "head"
        )

        val tailAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1350
                    0f at 0
                    0f at 280
                    1f at 1350 using FastOutSlowInEasing
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "tail"
        )

        val shimmerOffset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer"
        )

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val left = tailAnim * canvasWidth
            val right = (headAnim * canvasWidth).coerceAtLeast(left + 24.dp.toPx())
            val barWidth = (right - left).coerceAtLeast(10f)

            val brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF5722),
                    Color(0xFFFF9800),
                    Color(0xFFFFC107),
                    Color(0xFFE91E63),
                    Color(0xFFFF5722)
                ),
                start = Offset(left - shimmerOffset % canvasWidth, 0f),
                end = Offset(right + 120f, canvasHeight)
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, canvasHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(canvasHeight / 2, canvasHeight / 2)
            )
        }
    }
}

// ── 远程图片缩略组件 (内置高性能解码与状态反馈) ─────────────────────────
@Composable
fun RemoteThumbnailImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        if (url.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val stream = conn.inputStream
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (bmp != null) {
                        bitmap = bmp.asImageBitmap()
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentOrange)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(viewModel: StatusViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部栏（MessageKeeper 经典终端风格） ─────────────────────────
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "在线状态 ~/",
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (uiState.selectedTab) {
                            StatusSubTab.STATUS -> "实时大屏"
                            StatusSubTab.EVENTS -> "事件脉络"
                            StatusSubTab.AUDIT  -> "终端审计"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            actions = {
                val isAdmin = uiState.currentUser != null && uiState.currentUser!!.level >= 4
                if (isAdmin) {
                    IconButton(onClick = { viewModel.openRecycleBinDialog() }) {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (uiState.recycleBinItems.isNotEmpty()) {
                                    androidx.compose.material3.Badge(
                                        containerColor = AccentOrange,
                                        contentColor = Color.White
                                    ) {
                                        Text("${uiState.recycleBinItems.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "最近删除",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.refreshAll() }) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentOrange
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // 账户登录 / 当前身份快捷入口
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.currentUser != null) AccentOrange.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.currentUser != null) AccentOrange.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.openConfigDialog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "账户登录与服务配置",
                            modifier = Modifier.size(14.dp),
                            tint = if (uiState.currentUser != null) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = uiState.currentUser?.username ?: "登录",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.currentUser != null) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 72.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.openConfigDialog() }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "服务配置",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // ── 全站公告横幅 ──────────────────────────────────────────────────
        if (uiState.alert != null) {
            val alert = uiState.alert!!
            val isRed = alert.level == "red"
            val alertBg = if (isRed) AccentRed.copy(alpha = 0.12f) else AccentBlue.copy(alpha = 0.12f)
            val alertTextCol = if (isRed) AccentRed else AccentBlue
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                color = alertBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, alertTextCol.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = alertTextCol
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = alertTextCol,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.currentUser != null && uiState.currentUser!!.level >= 4) {
                        IconButton(
                            onClick = { viewModel.deleteAlert() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = alertTextCol, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // ── 三分屏顶置 Tab 切换器 ──────────────────────────────────────────
        val isAdminTabVisible = uiState.currentUser != null && uiState.currentUser!!.level >= 4
        val activeTabIndex = if (!isAdminTabVisible && uiState.selectedTab == StatusSubTab.AUDIT) 0 else uiState.selectedTab.ordinal
        TabRow(
            selectedTabIndex = activeTabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = AccentOrange,
            indicator = { tabPositions ->
                val safeIdx = activeTabIndex.coerceIn(0, (tabPositions.size - 1).coerceAtLeast(0))
                if (tabPositions.isNotEmpty()) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[safeIdx]),
                        color = AccentOrange,
                        height = 2.dp
                    )
                }
            },
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
        ) {
            Tab(
                selected = uiState.selectedTab == StatusSubTab.STATUS,
                onClick = { viewModel.switchTab(StatusSubTab.STATUS) },
                text = { Text("实时状态", fontWeight = if (uiState.selectedTab == StatusSubTab.STATUS) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = uiState.selectedTab == StatusSubTab.EVENTS,
                onClick = { viewModel.switchTab(StatusSubTab.EVENTS) },
                text = { Text("事件跟踪 (${uiState.events.size})", fontWeight = if (uiState.selectedTab == StatusSubTab.EVENTS) FontWeight.Bold else FontWeight.Normal) }
            )
            if (isAdminTabVisible) {
                Tab(
                    selected = uiState.selectedTab == StatusSubTab.AUDIT,
                    onClick = { viewModel.switchTab(StatusSubTab.AUDIT) },
                    text = { Text("终端审计", fontWeight = if (uiState.selectedTab == StatusSubTab.AUDIT) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // 顶置变形流体进度条（数据加载/提交/上传即时响应，彻底杜绝假死感）
        MorphingProgressBar(
            visible = uiState.isLoading || uiState.isUpdating || uiState.isUploadingAttachments || uiState.isAuthenticating
        )

        // ── 错误提示条 ────────────────────────────────────────────────────
        if (uiState.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "连接异常：${uiState.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.openConfigDialog() }) {
                        Text("配置", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── 分屏内容容器（流畅淡入淡出动画过渡） ────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "tab_switch_anim"
            ) { tab ->
                when (tab) {
                    StatusSubTab.STATUS -> StatusDashboardView(viewModel, uiState)
                    StatusSubTab.EVENTS -> EventsTrackingView(viewModel, uiState, onPreviewImage = { url -> previewImageUrl = url })
                    StatusSubTab.AUDIT  -> UsersAuditView(viewModel, uiState)
                }
            }
        }
    }

    // ── 全局弹窗群 ────────────────────────────────────────────────────────
    if (uiState.showConfigDialog) {
        ConfigDialog(
            currentUrl = uiState.serverUrl,
            currentKey = uiState.apiKey,
            currentUser = uiState.currentUser,
            isAuthenticating = uiState.isAuthenticating,
            onLoginOrRegister = { isReg, url, user, pwd, note ->
                viewModel.loginOrRegister(isReg, url, user, pwd, note)
            },
            onLogout = { viewModel.logoutAccount() },
            onDismiss = { viewModel.closeConfigDialog() },
            onConfirm = { url, key -> viewModel.saveConfig(url, key) }
        )
    }

    if (uiState.showCustomUpdateDialog) {
        CustomStatusDialog(
            initialLabel = uiState.statusData.status.label,
            initialCategory = uiState.statusData.status.category,
            initialDesc = uiState.statusData.status.description,
            onDismiss = { viewModel.closeCustomUpdateDialog() },
            onSubmit = { label, cat, desc, col, dur ->
                viewModel.submitCustomStatus(label, cat, desc, col, dur)
            }
        )
    }

    if (uiState.showCreateEventDialog) {
        CreateEventDialog(
            categories = uiState.categories,
            onDismiss = { viewModel.closeCreateEventDialog() },
            onSubmit = { title, type, urgency, vis, desc ->
                viewModel.createEvent(title, type, urgency, vis, desc)
            }
        )
    }

    if (uiState.showAddLogDialogForEvent != null) {
        AddLogDialog(
            event = uiState.showAddLogDialogForEvent!!,
            serverUrl = uiState.serverUrl,
            isUploading = uiState.isUploadingAttachments,
            pendingAttachments = uiState.pendingAttachments,
            onPickFiles = { uris -> viewModel.uploadAttachments(context, uris) },
            onRemoveAttachment = { att -> viewModel.removePendingAttachment(att) },
            onDismiss = { viewModel.closeAddLogDialog() },
            onSubmit = { type, content, atts ->
                viewModel.addEventLog(uiState.showAddLogDialogForEvent!!.id, type, content, atts)
            }
        )
    }

    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteThumbnailImage(
                        url = previewImageUrl!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { previewImageUrl = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("关闭全屏预览", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (uiState.showPublishAlertDialog) {
        PublishAlertDialog(
            currentAlert = uiState.alert?.message ?: "",
            onDismiss = { viewModel.closePublishAlertDialog() },
            onPublish = { msg, level -> viewModel.publishAlert(msg, level) }
        )
    }

    if (uiState.showCategoryManageDialog) {
        CategoryManageDialog(
            categories = uiState.categories,
            onDismiss = { viewModel.closeCategoryManageDialog() },
            onCreate = { name, col -> viewModel.createCategory(name, col) },
            onDelete = { id ->
                viewModel.requestConfirmDelete("删除类目", "确定删除该类目？关联的事件不会被删除。") {
                    viewModel.deleteCategory(id)
                }
            }
        )
    }

    if (uiState.showRecycleBinDialog) {
        RecycleBinDialog(
            items = uiState.recycleBinItems,
            isLoading = uiState.isLoadingRecycleBin,
            onRestore = { id -> viewModel.restoreRecycleItem(id) },
            onPermanentDelete = { id ->
                viewModel.requestConfirmDelete("彻底删除", "确定彻底删除该项目？此操作不可逆！") {
                    viewModel.deleteRecycleItem(id)
                }
            },
            onClearAll = {
                viewModel.requestConfirmDelete("清空回收站", "确定清空最近删除回收站中的所有项目？此操作无法撤销！") {
                    viewModel.clearRecycleBin()
                }
            },
            onDismiss = { viewModel.closeRecycleBinDialog() }
        )
    }

    if (uiState.pendingDeleteConfirm != null) {
        val confirm = uiState.pendingDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDelete() },
            title = { Text(confirm.title, fontWeight = FontWeight.Bold) },
            text = { Text(confirm.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = { confirm.onConfirm() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDelete() }) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showExportMdDialog != null) {
        ExportMarkdownDialog(
            markdown = uiState.showExportMdDialog!!,
            onDismiss = { viewModel.closeExportMdDialog() },
            onCopy = {
                clipboard.setText(AnnotatedString(uiState.showExportMdDialog!!))
                com.messagekeeper.ui.components.Sonner.success("Markdown 内容已复制到剪贴板")
            }
        )
    }

    if (uiState.activeSessionUser != null) {
        UserSessionsDialog(
            user = uiState.activeSessionUser!!,
            sessions = uiState.activeUserSessions,
            isLoading = uiState.isLoadingSessions,
            onDismiss = { viewModel.closeUserSessions() }
        )
    }
}

// =========================================================================
// SCREEN 1: 实时状态大屏 (Hero + 2x2 指标 + 快捷切换 + 历史记录)
// =========================================================================
@Composable
private fun StatusDashboardView(viewModel: StatusViewModel, uiState: StatusUiState) {
    val isAdmin = uiState.currentUser != null && uiState.currentUser!!.level >= 4
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // 当前状态 Hero 卡片
        item {
            val status = uiState.statusData.status
            val tagColor = parseHexColor(status.color)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(tagColor))
                            Spacer(Modifier.width(8.dp))
                            Text("当前实时状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentOrange.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { viewModel.openCustomUpdateDialog() }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = AccentOrange)
                                    Spacer(Modifier.width(4.dp))
                                    Text("更新状态", style = MaterialTheme.typography.labelSmall, color = AccentOrange, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(10.dp))
                        // 长方形分类标签（严格不折行）
                        Surface(shape = RoundedCornerShape(4.dp), color = tagColor.copy(alpha = 0.85f)) {
                            Text(
                                text = status.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (status.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = status.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("同步自 State Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        if (status.autoRevertAt != null) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("倒计时恢复模式", style = MaterialTheme.typography.labelSmall, color = AccentYellow, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // 2x2 统计指标
        item {
            val stats = uiState.stats
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("进行中事件", stats.active.toString(), AccentBlue, Modifier.weight(1f))
                    StatCard("高紧急 (L4+)", stats.urgent.toString(), AccentRed, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("已归档", stats.archived.toString(), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                    StatCard("近 7 天跟进", stats.weekLogs.toString(), AccentGreen, Modifier.weight(1f))
                }
            }
        }

        // 快捷标签切换 (仅管理员可见)
        if (isAdmin && uiState.statusData.presets.isNotEmpty()) {
            item {
                Text("快捷状态切换", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.statusData.presets.chunked(2).forEach { rowPresets ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowPresets.forEach { preset ->
                                PresetChip(preset = preset, modifier = Modifier.weight(1f), onClick = { viewModel.applyPreset(preset) })
                            }
                            if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 历史记录列表
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("最近状态记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("点击展开全文", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        if (uiState.statusData.history.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(uiState.statusData.history) { h ->
                StatusHistoryRow(
                    item = h,
                    showDelete = isAdmin,
                    onDelete = {
                        viewModel.requestConfirmDelete(
                            title = "移除历史状态",
                            message = "确定移除该历史状态？内容将移至「最近删除」，在 30 天内可随时恢复。"
                        ) {
                            viewModel.deleteHistory(h.id)
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =========================================================================
// SCREEN 2: 事件脉络跟踪 (筛选栏 + 长方形边框卡片 + 时间线 + 写跟进 + 导出MD)
// =========================================================================
@Composable
private fun EventsTrackingView(
    viewModel: StatusViewModel,
    uiState: StatusUiState,
    onPreviewImage: (String) -> Unit
) {
    val isAdmin = uiState.currentUser != null && uiState.currentUser!!.level >= 4
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // 操作顶栏：搜索 + 新建事件 + 公告管理 + 类目管理
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.eventSearchQuery,
                    onValueChange = { viewModel.setEventFilters(query = it) },
                    placeholder = { Text("搜索事件标题、内容...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                )
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.openCreateEventDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建")
                    }
                }
            }
        }

        // 快捷管理按钮条 (仅管理员可见)
        if (isAdmin) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).clickable { viewModel.openPublishAlertDialog() }
                    ) {
                        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentOrange)
                            Spacer(Modifier.width(6.dp))
                            Text("全站公告", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).clickable { viewModel.openCategoryManageDialog() }
                    ) {
                        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentBlue)
                            Spacer(Modifier.width(6.dp))
                            Text("类目管理", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 类目水平滑动 FilterChip
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.eventFilterType == "all",
                    onClick = { viewModel.setEventFilters(type = "all") },
                    label = { Text("全部类目") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange, selectedLabelColor = Color.White)
                )
                uiState.categories.forEach { cat ->
                    FilterChip(
                        selected = uiState.eventFilterType == cat.name,
                        onClick = { viewModel.setEventFilters(type = cat.name) },
                        label = { Text(cat.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange, selectedLabelColor = Color.White)
                    )
                }
            }
        }

        // 状态筛选条：进行中 / 已归档 / 全部
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("0" to "进行中", "1" to "已归档", "all" to "全部").forEach { (v, lbl) ->
                    val isSel = uiState.eventFilterArchived == v
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.clickable { viewModel.setEventFilters(archived = v) }
                    ) {
                        Text(
                            text = lbl,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 事件列表
        if (uiState.isEventsLoading) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = AccentOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    EventListSkeleton()
                }
            }
        } else if (uiState.events.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("暂无符合条件的事件记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(uiState.events, key = { it.id }) { ev ->
                EventCard(
                    event = ev,
                    serverUrl = uiState.serverUrl,
                    isAdmin = isAdmin,
                    onAddLog = { viewModel.openAddLogDialog(ev) },
                    onDeleteLog = { logId ->
                        viewModel.requestConfirmDelete(
                            title = "删除跟进记录",
                            message = "确定删除该条跟进记录？内容将移至「最近删除」，在 30 天内可随时恢复。"
                        ) {
                            viewModel.deleteEventLog(ev.id, logId)
                        }
                    },
                    onTogglePin = { viewModel.togglePinEvent(ev.id) },
                    onToggleArchive = { viewModel.toggleArchiveEvent(ev.id) },
                    onDelete = {
                        viewModel.requestConfirmDelete(
                            title = "删除事件「${ev.title}」",
                            message = "确定删除该事件？包含的跟进记录将一并移至「最近删除」，在 30 天内可随时恢复。"
                        ) {
                            viewModel.deleteEvent(ev.id)
                        }
                    },
                    onExportMd = { viewModel.exportEventMarkdown(ev.id) },
                    onPreviewImage = onPreviewImage
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── 事件骨架加载动画 ─────────────────────────────────────────
@Composable
private fun EventListSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "eventSkeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eventSkeletonAlpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.25f))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.45f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.25f))
                    )
                }
            }
        }
    }
}

// ── 事件卡片组件（左侧长方形笔直指示条，绝无弯钩） ─────────────────────────
@Composable
private fun EventCard(
    event: StateEvent,
    serverUrl: String,
    isAdmin: Boolean,
    onAddLog: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onExportMd: () -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val urgColor = UrgencyColors[event.urgency] ?: Color(0xFFF59E0B)
    var showAllLogs by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 笔直的长方形左侧颜色指示条 (宽 4dp)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .padding(top = 12.dp)
                    .background(urgColor, shape = RoundedCornerShape(1.dp))
            )

            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                // 标签栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (event.isPinned) {
                            Surface(shape = RoundedCornerShape(3.dp), color = AccentOrange.copy(alpha = 0.15f)) {
                                Text("置顶", style = MaterialTheme.typography.labelSmall, color = AccentOrange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Surface(shape = RoundedCornerShape(3.dp), color = urgColor.copy(alpha = 0.15f)) {
                            Text(UrgencyLabels[event.urgency] ?: "L3", style = MaterialTheme.typography.labelSmall, color = urgColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(event.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        if (event.isArchived) {
                            Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("已归档", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text("跟进 ${event.logs.size} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                Spacer(Modifier.height(8.dp))

                // 标题与描述
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (event.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(event.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 连续时间线
                if (event.logs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val logsToShow = if (showAllLogs || event.logs.size <= 2) event.logs else event.logs.takeLast(2)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (event.logs.size > 2 && !showAllLogs) {
                            Text(
                                "展开较早的 ${event.logs.size - 2} 条跟进记录...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange,
                                modifier = Modifier.clickable { showAllLogs = true }
                            )
                        }
                        logsToShow.forEach { log ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(log.logType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(log.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    if (log.createdAt.isNotBlank()) {
                                        Text(
                                            text = formatDisplayTime(log.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                    if (isAdmin) {
                                        IconButton(onClick = { onDeleteLog(log.id) }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                if (log.attachments.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val context = LocalContext.current
                                        log.attachments.forEach { att ->
                                            val fullUrl = if (att.url.startsWith("http")) att.url else "${serverUrl.removeSuffix("/")}${att.url}"
                                            if (att.isImage) {
                                                Surface(
                                                    modifier = Modifier
                                                        .size(68.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onPreviewImage(fullUrl) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                ) {
                                                    RemoteThumbnailImage(url = fullUrl, modifier = Modifier.fillMaxSize())
                                                }
                                            } else {
                                                Surface(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {}
                                                        },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = att.name.ifBlank { "附件" },
                                                            style = MaterialTheme.typography.labelSmall,
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
                        }
                    }
                }

                // 操作按钮栏：追加跟进 | 导出MD | 置顶 | 归档 | 删除
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentOrange.copy(alpha = 0.12f),
                            modifier = Modifier.clickable { onAddLog() }
                        ) {
                            Text("+ 追加跟进", style = MaterialTheme.typography.labelSmall, color = AccentOrange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onExportMd() }
                    ) {
                        Text("导出 MD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onTogglePin() }
                        ) {
                            Text(if (event.isPinned) "取消置顶" else "置顶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onToggleArchive() }
                        ) {
                            Text(if (event.isArchived) "还原" else "归档", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除事件", tint = AccentRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SCREEN 3: 账户与终端审计 (多级权限滑块 + IP/设备指纹 + 会话审计弹窗)
// =========================================================================
@Composable
private fun UsersAuditView(viewModel: StatusViewModel, uiState: StatusUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Text(
                "账户与终端设备审计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "滑动权限滑块实时在线提权/降权 (0-4级)，点击卡片可查看 IP 与客户端设备指纹历史。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (uiState.users.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.isLoading) "正在获取用户审计列表..." else "暂无用户或需要管理员 API Key",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(uiState.users) { u ->
                UserAuditCard(
                    user = u,
                    onLevelChange = { newLvl -> viewModel.updateUserLevel(u.id, newLvl) },
                    onViewSessions = { viewModel.openUserSessions(u) },
                    onDelete = {
                        viewModel.requestConfirmDelete(
                            title = "注销账户「${u.username}」",
                            message = "确定注销该账户？此操作无法撤销。"
                        ) {
                            viewModel.deleteUser(u.id)
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun UserAuditCard(
    user: StateUser,
    onLevelChange: (Int) -> Unit,
    onViewSessions: () -> Unit,
    onDelete: () -> Unit
) {
    var sliderVal by remember(user.level) { mutableFloatStateOf(user.level.toFloat()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("#${user.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (user.status == "active") AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (user.status == "active") "正常" else "禁用",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (user.status == "active") AccentGreen else AccentRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 权限滑块
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("权限等级:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { onLevelChange(sliderVal.toInt()) },
                    valueRange = 0f..4f,
                    steps = 3,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
                )
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = AccentOrange.copy(alpha = 0.15f)) {
                    Text(
                        LevelLabels[sliderVal.toInt()] ?: "L1",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 设备与终端信息
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最近 IP: ${user.lastIp.ifBlank { "无" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                Text(user.lastDeviceId.ifBlank { "无设备指纹" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onViewSessions) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("登录历史审计")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) {
                    Text("删除", color = AccentRed)
                }
            }
        }
    }
}

// ── 登录会话审计弹窗 ──────────────────────────────────────────────────────────
@Composable
private fun UserSessionsDialog(
    user: StateUser,
    sessions: List<UserSession>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("访问 IP 与设备审计 · ${user.username}") },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentOrange)
                }
            } else if (sessions.isEmpty()) {
                Text("暂无该用户的登录历史记录", color = MaterialTheme.colorScheme.outline)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions) { s ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.action, style = MaterialTheme.typography.labelSmall, color = AccentOrange, fontWeight = FontWeight.Bold)
                                    Text(s.ip, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                                Text(s.deviceId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                                Text(s.userAgent, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ── Markdown 导出弹窗 ────────────────────────────────────────────────────────
@Composable
private fun ExportMarkdownDialog(
    markdown: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("复盘 Markdown 文档") },
        text = {
            OutlinedTextField(
                value = markdown,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = {
            Button(onClick = onCopy, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                Text("复制全部 Markdown")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ── 新建事件弹窗 ─────────────────────────────────────────────────────────────
@Composable
private fun CreateEventDialog(
    categories: List<StateCategory>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, type: String, urgency: Int, vis: Int, desc: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(categories.firstOrNull()?.name ?: "通用") }
    var urgency by remember { mutableIntStateOf(3) }
    var visibility by remember { mutableIntStateOf(1) }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事件标题 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                )

                // 类目快速点选
                Text("事件类目", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("通用") + categories.map { it.name }).distinct().forEach { catName ->
                        FilterChip(
                            selected = selectedType == catName,
                            onClick = { selectedType = catName },
                            label = { Text(catName) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange, selectedLabelColor = Color.White)
                        )
                    }
                }

                // 紧急度选择
                Text("紧急度: ${UrgencyLabels[urgency]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { u ->
                        val isSel = urgency == u
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSel) (UrgencyColors[u] ?: AccentOrange) else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f).clickable { urgency = u }
                        ) {
                            Text("L$u", style = MaterialTheme.typography.labelSmall, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("详细背景与说明") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSubmit(title, selectedType, urgency, visibility, desc) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("创建事件")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 追加跟进记录弹窗 (支持多附件/多图选择与实时预览) ───────────────────
@Composable
private fun AddLogDialog(
    event: StateEvent,
    serverUrl: String,
    isUploading: Boolean,
    pendingAttachments: List<EventAttachment>,
    onPickFiles: (List<Uri>) -> Unit,
    onRemoveAttachment: (EventAttachment) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (logType: String, content: String, attachments: List<EventAttachment>) -> Unit
) {
    var selectedType by remember { mutableStateOf("跟进") }
    var content by remember { mutableStateOf("") }
    val followupTypes = listOf("跟进", "问题原因", "解决方法", "反馈", "反思", "里程碑", "待办")

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onPickFiles(uris)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("追加跟进 · ${event.title}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("记录类型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    followupTypes.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentOrange, selectedLabelColor = Color.White)
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("输入具体记录内容...") },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                )

                // 附件/图片选择栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "附件与图片 (${pendingAttachments.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isUploading,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isUploading) "上传中..." else "+ 选图/附件", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (isUploading) {
                    MorphingProgressBar(visible = true, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "正在上传文件至服务器...",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }

                if (pendingAttachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pendingAttachments.forEach { att ->
                            val fullUrl = if (att.url.startsWith("http")) att.url else "${serverUrl.removeSuffix("/")}${att.url}"
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (att.isImage) {
                                        Surface(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp))) {
                                            RemoteThumbnailImage(url = fullUrl, modifier = Modifier.fillMaxSize())
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = att.name.ifBlank { "附件" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 100.dp)
                                    )
                                    IconButton(
                                        onClick = { onRemoveAttachment(att) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
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
                onClick = {
                    if (content.isNotBlank() || pendingAttachments.isNotEmpty()) {
                        onSubmit(selectedType, content, pendingAttachments)
                    }
                },
                enabled = !isUploading && (content.isNotBlank() || pendingAttachments.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("提交跟进")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 发布公告弹窗 ─────────────────────────────────────────────────────────────
@Composable
private fun PublishAlertDialog(
    currentAlert: String,
    onDismiss: () -> Unit,
    onPublish: (message: String, level: String) -> Unit
) {
    var msg by remember { mutableStateOf(currentAlert) }
    var level by remember { mutableStateOf("blue") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布全站系统公告") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    placeholder = { Text("输入全站通知内容...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = level == "blue",
                        onClick = { level = "blue" },
                        label = { Text("普通通知 (蓝色)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentBlue, selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = level == "red",
                        onClick = { level = "red" },
                        label = { Text("紧急停机 (红色)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentRed, selectedLabelColor = Color.White)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (msg.isNotBlank()) onPublish(msg, level) }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                Text("立即发布")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 类目管理弹窗 ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryManageDialog(
    categories: List<StateCategory>,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义事件类目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("新类目名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreate(newName, "#3b82f6")
                                newName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("添加")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { c ->
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "删除", tint = AccentRed, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

// ── 常用统计小卡片 ────────────────────────────────────────────────────────────
@Composable
private fun StatCard(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accentColor, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── 快捷标签 Chip ────────────────────────────────────────────────────────────
@Composable
private fun PresetChip(preset: StatusPreset, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val chipColor = parseHexColor(preset.color)
    Surface(
        modifier = modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(chipColor))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preset.category.isNotBlank()) {
                    Text(preset.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
        }
    }
}

// ── 历史记录项 ────────────────────────────────────────────────────────────────
@Composable
private fun StatusHistoryRow(item: StatusHistory, showDelete: Boolean = false, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val itemColor = parseHexColor(item.color)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(itemColor))
                Spacer(Modifier.width(8.dp))
                Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                if (item.category.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(4.dp), color = itemColor.copy(alpha = 0.8f)) {
                        Text(item.category, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (item.createdAt.isNotBlank()) {
                    Text(
                        text = formatDisplayTime(item.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                if (showDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── 服务配置 & 账户密码登录/注册弹窗 (完成后自动填入 API Key) ─────────────────
@Composable
private fun ConfigDialog(
    currentUrl: String,
    currentKey: String,
    currentUser: StateUser?,
    isAuthenticating: Boolean,
    onLoginOrRegister: (isRegister: Boolean, url: String, username: String, password: String, note: String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var keyText by remember { mutableStateOf(currentKey) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    // 当密码登录或注册成功后，自动同步并填入最新返回的 API Key
    LaunchedEffect(currentKey) {
        keyText = currentKey
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = AccentOrange)
                Spacer(Modifier.width(8.dp))
                Text("账户登录 & 服务配置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 当前已登录账户卡片
                if (currentUser != null) {
                    val lvlText = LevelLabels[currentUser.level.coerceIn(0, 4)] ?: "L${currentUser.level}"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已登录：${currentUser.username} ($lvlText)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                                Text(
                                    text = "专属 API Key 已自动绑定",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    keyText = ""
                                    onLogout()
                                }
                            ) {
                                Text("退出账户", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. 服务地址 Base URL
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("服务地址 (Base URL)") },
                    placeholder = { Text("https://your-domain.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 3. 账号密码登录 / 注册面板
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isRegisterMode,
                                onClick = { isRegisterMode = false },
                                label = { Text("密码登录", fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentOrange,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = isRegisterMode,
                                onClick = { isRegisterMode = true },
                                label = { Text("注册新账户", fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentOrange,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        Text(
                            text = if (isRegisterMode) "注册后默认开通 L1 权限，并自动填入专属 API Key" else "输入账号密码登录，完成后将自动获取并填入 API Key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            placeholder = { Text(if (isRegisterMode) "2-24 位字符" else "输入您的用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            placeholder = { Text(if (isRegisterMode) "至少 6 位密码" else "输入登录密码") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showPassword = !showPassword }) {
                                    Text(if (showPassword) "隐藏" else "显示", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                        )

                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("备注说明 (选填)") },
                                placeholder = { Text("告知管理员您的身份，便于提权") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                            )
                        }

                        Button(
                            onClick = {
                                onLoginOrRegister(isRegisterMode, urlText, username, password, note)
                            },
                            enabled = !isAuthenticating && username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isRegisterMode) "正在注册并获取 Key..." else "正在登录并获取 Key...")
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isRegisterMode) "注册并自动填入 API Key" else "登录并自动填入 API Key",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 4. API Key 凭证框（登录/注册完成后自动填入，也支持手动修改，默认掩码防窥）
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("API Key (登录/注册后自动填入)") },
                    placeholder = { Text("sk_state_xxxx (或使用上方密码登录自动获取)") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (keyText.isNotBlank()) {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "隐藏" else "显示", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange, focusedLabelColor = AccentOrange)
                )
                if (keyText.isNotBlank()) {
                    Text(
                        text = "✓ 已填入 API Key 凭据",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(urlText, keyText) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("保存并完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// ── 自定义状态修改弹窗 ────────────────────────────────────────────────────────
@Composable
private fun CustomStatusDialog(
    initialLabel: String,
    initialCategory: String,
    initialDesc: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Int) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var category by remember { mutableStateOf(initialCategory) }
    var desc by remember { mutableStateOf(initialDesc) }
    var selectedColorHex by remember { mutableStateOf("#DCE9F5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新在线状态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("状态名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("补充说明") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange))
                Text("标签色彩 (低饱和 8 色)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Palette8.forEach { (_, col) ->
                        val hex = String.format("#%06X", (0xFFFFFF and col.hashCode()))
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) AccentOrange else Color.Transparent, shape = CircleShape)
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.DarkGray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (label.isNotBlank()) onSubmit(label, category, desc, selectedColorHex, 0) }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                Text("提交更新")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 最近删除 / 回收站弹窗 (保留30天) ─────────────────────────────────────────
@Composable
private fun RecycleBinDialog(
    items: List<RecycleBinItem>,
    isLoading: Boolean,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("all") }

    val filteredItems = items.filter {
        selectedFilter == "all" || it.itemType == selectedFilter
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccentOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("最近删除 (${items.size})", fontWeight = FontWeight.Bold)
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("清空全部", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "最近 30 天内删除的内容保留在此，可随时单项恢复或彻底清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("all" to "全部", "event" to "事件", "status_history" to "状态", "event_log" to "跟进").forEach { (type, lbl) ->
                        FilterChip(
                            selected = selectedFilter == type,
                            onClick = { selectedFilter = type },
                            label = { Text(lbl) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentOrange)
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("暂无已删除内容", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems) { item ->
                            val typeLabel = when (item.itemType) {
                                "event" -> "事件"
                                "status_history" -> "状态"
                                "event_log" -> "跟进"
                                else -> "条目"
                            }
                            val typeColor = when (item.itemType) {
                                "event" -> Color(0xFF10B981)
                                "status_history" -> Color(0xFF3B82F6)
                                "event_log" -> Color(0xFF8B5CF6)
                                else -> AccentOrange
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = RoundedCornerShape(3.dp), color = typeColor.copy(alpha = 0.15f)) {
                                            Text(
                                                text = typeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = typeColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = formatDisplayTime(item.deletedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { onPermanentDelete(item.id) }) {
                                            Text("彻底删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Button(
                                            onClick = { onRestore(item.id) },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = typeColor)
                                        ) {
                                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("恢复", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
