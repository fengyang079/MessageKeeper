package com.messagekeeper.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.ui.components.DissolveBox
import com.messagekeeper.ui.components.EmptyState
import com.messagekeeper.ui.components.MessageBubble
import com.messagekeeper.ui.components.Sonner
import com.messagekeeper.ui.components.rememberDissolveState
import com.messagekeeper.ui.theme.AccentOrange
import com.messagekeeper.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 长按菜单锚点：哪条消息、触点在根布局中的绝对坐标 */
private data class MessageMenuAnchor(
    val message: Message,
    val pressInRoot: Offset,
)

// Material3 DropdownMenu 尺寸估算：最小宽度 112dp，两项 48dp + 上下 8dp 内边距
private val MenuWidthEstimate = 128.dp
private val MenuHeightEstimate = 112.dp

/** 菜单与手指之间的间距，避免菜单被手指盖住 */
private val MenuFingerGap = 6.dp

/** Material3 DropdownMenu 内部保留的上下安全边距（MenuVerticalMargin = 48dp） */
private val MenuVerticalMargin = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        key = conversationId.toString(),
        factory = ChatViewModel.Factory(conversationId)
    )
) {
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val conversationName by viewModel.conversationName.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(conversationId) {
        viewModel.onConversationEntered()
    }

    // 长按菜单的位置（跟随手指；null 表示菜单关闭）
    var menuAnchor by remember { mutableStateOf<MessageMenuAnchor?>(null) }
    var dissolvingMessageId by remember { mutableStateOf<Long?>(null) }
    var screenTopLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                screenTopLeftInRoot = coordinates.positionInRoot()
                screenSize = coordinates.size
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── 顶部栏（纯黑终端风格）──────────────────────
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "查看 /${conversationName.ifEmpty { "未知联系人" }}",
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${messages.itemCount} 条已加载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            if (messages.itemCount == 0) {
                EmptyState(title = "记录为空")
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true, // We reversed the SQL query so we show from bottom up seamlessly
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(count = messages.itemCount, key = { i -> messages[i]?.id ?: i }) { index ->
                        val message = messages[index]
                        if (message != null) {
                            // reverseLayout implies index 0 is at bottom (newest)
                            // so older message is at index + 1
                            val currentDay = getDayString(message.timestamp)
                            val previousDay = if (index + 1 < messages.itemCount) {
                                messages[index + 1]?.timestamp?.let { getDayString(it) } ?: ""
                            } else ""

                            if (currentDay != previousDay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "———  $currentDay  ———",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            var itemTopLeftInRoot by remember(message.id) { mutableStateOf(Offset.Zero) }

                            // 删除动画：先碎裂，动画结束后再删库（归档到历史删除回收站）
                            val dissolveState = rememberDissolveState(
                                onDissolved = {
                                    viewModel.deleteMessage(message)
                                    if (dissolvingMessageId == message.id) dissolvingMessageId = null
                                    Sonner.success("已移入历史删除")
                                }
                            )

                            LaunchedEffect(dissolvingMessageId) {
                                if (dissolvingMessageId == message.id && !dissolveState.isDissolving) {
                                    dissolveState.dissolve()
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        itemTopLeftInRoot = coordinates.positionInRoot()
                                    }
                                    .pointerInput(message.id) {
                                        detectTapGestures(
                                            onLongPress = { pressPosition ->
                                                if (!dissolveState.isDissolving) {
                                                    menuAnchor = MessageMenuAnchor(
                                                        message = message,
                                                        pressInRoot = itemTopLeftInRoot + pressPosition
                                                    )
                                                }
                                            },
                                            onTap = {}
                                        )
                                    }
                            ) {
                                DissolveBox(state = dissolveState) {
                                    MessageBubble(message = message)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 将 DropdownMenu 独立挂载在左上角 0x0 锚点上，消除行高度和滚动容器带来的位置偏移
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            val anchor = menuAnchor
            DropdownMenu(
                expanded = anchor != null,
                onDismissRequest = { menuAnchor = null },
                offset = rememberMenuOffset(
                    anchor = anchor,
                    screenTopLeftInRoot = screenTopLeftInRoot,
                    screenSize = screenSize
                )
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        anchor?.message?.let { copyToClipboard(context, it.content) }
                        menuAnchor = null
                        Sonner.success("已复制到剪贴板")
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除消息", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        val targetId = anchor?.message?.id
                        menuAnchor = null
                        if (targetId != null) {
                            dissolvingMessageId = targetId
                        }
                    }
                )
            }
        }
    }
}

/**
 * 计算 DropdownMenu 的偏移量：
 * - 锚点为外层 Box 左上角 (0x0)，因此 DropdownMenu 的 offset 即相对于外层 Box 左上角的偏移
 * - 菜单左上角尽量落在手指长按点（略微下移避免挡住手指），并在可视区域内做 clamp
 */
@Composable
private fun rememberMenuOffset(
    anchor: MessageMenuAnchor?,
    screenTopLeftInRoot: Offset,
    screenSize: IntSize
): DpOffset {
    val density = LocalDensity.current
    val rootView = LocalView.current
    return remember(anchor, density, screenTopLeftInRoot, screenSize) {
        if (anchor == null) return@remember DpOffset.Zero
        with(density) {
            val containerWidth = if (screenSize.width > 0) screenSize.width.toFloat() else rootView.width.toFloat()
            val containerHeight = if (screenSize.height > 0) screenSize.height.toFloat() else rootView.height.toFloat()
            val menuWidth = MenuWidthEstimate.toPx()
            val menuHeight = MenuHeightEstimate.toPx()
            val fingerGap = MenuFingerGap.toPx()
            val verticalMargin = MenuVerticalMargin.toPx()

            val localX = anchor.pressInRoot.x - screenTopLeftInRoot.x
            val localY = anchor.pressInRoot.y - screenTopLeftInRoot.y + fingerGap

            val maxLeft = (containerWidth - menuWidth - 8.dp.toPx()).coerceAtLeast(8.dp.toPx())
            val maxTop = (containerHeight - menuHeight - verticalMargin).coerceAtLeast(verticalMargin)

            val clampedX = localX.coerceIn(8.dp.toPx(), maxLeft)
            val clampedY = localY.coerceIn(verticalMargin, maxTop)

            DpOffset(
                x = clampedX.toDp(),
                y = clampedY.toDp()
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("message", text)
    clipboard.setPrimaryClip(clip)
}

private val separatorDateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy年 MM月 dd日", Locale.getDefault()) }

private fun getDayString(timestamp: Long): String {
    return (separatorDateFormat.get() ?: SimpleDateFormat("yyyy年 MM月 dd日", Locale.getDefault())).format(Date(timestamp))
}
