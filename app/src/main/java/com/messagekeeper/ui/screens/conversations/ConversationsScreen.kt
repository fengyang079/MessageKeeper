package com.messagekeeper.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.ui.components.ConversationItem
import com.messagekeeper.ui.components.DissolveBox
import com.messagekeeper.ui.components.EmptyState
import com.messagekeeper.ui.components.Sonner
import com.messagekeeper.ui.components.rememberDissolveState
import com.messagekeeper.ui.theme.AccentGreen
import com.messagekeeper.ui.theme.AccentOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (Long) -> Unit,
    viewModel: ConversationsViewModel = viewModel(factory = ConversationsViewModel.Factory)
) {
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showSleepScheduleDialog by remember { mutableStateOf(false) }
    var conversationActionTarget by remember { mutableStateOf<Conversation?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingDeletionIds by remember { mutableStateOf(emptySet<Long>()) }
    // 正在播放删除碎裂动画的会话 id（动画播完才真正删除）
    var dissolvingId by remember { mutableStateOf<Long?>(null) }

    fun deleteWithUndo(conversation: Conversation) {
        if (conversation.id in pendingDeletionIds) return
        pendingDeletionIds = pendingDeletionIds + conversation.id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "已删除会话「${conversation.display_name}」",
                actionLabel = "撤销",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            pendingDeletionIds = pendingDeletionIds - conversation.id
            if (result != SnackbarResult.ActionPerformed) {
                viewModel.deleteConversation(conversation.id)
            }
        }
    }

    BackHandler(enabled = showSearch) {
        showSearch = false
        viewModel.clearSearch()
    }

    conversationActionTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { conversationActionTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    text = target.display_name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(if (target.is_pinned) "取消置顶" else "置顶会话") },
                    leadingContent = {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        viewModel.togglePin(target.id, !target.is_pinned)
                        conversationActionTarget = null
                        Sonner.success(if (target.is_pinned) "已取消置顶" else "已置顶")
                    }
                )
                ListItem(
                    headlineContent = { Text(if (target.is_system) "计为普通会话" else "排除在聊天统计外") },
                    supportingContent = { Text("用于区分软件通知与聊天消息") },
                    leadingContent = {
                        Icon(
                            if (target.is_system) Icons.Rounded.ChatBubbleOutline else Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleSystem(target.id, !target.is_system)
                        conversationActionTarget = null
                        Sonner.success(if (target.is_system) "已计入聊天统计" else "已排除聊天统计")
                    }
                )
                Text(
                    text = "会话类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Conversation.CHAT_TYPE_GROUP to "群聊",
                        Conversation.CHAT_TYPE_PRIVATE to "私信",
                        Conversation.CHAT_TYPE_AUTO to "自动"
                    ).forEach { (type, label) ->
                        TextButton(
                            onClick = {
                                viewModel.setChatType(target.id, type)
                                conversationActionTarget = null
                                Sonner.success("已设为$label")
                            },
                            enabled = target.chat_type != type
                        ) { Text(label) }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                ListItem(
                    headlineContent = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("删除后可在「历史删除」中恢复") },
                    leadingContent = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        conversationActionTarget = null
                        // 先播碎裂动画，动画结束后 deleteWithUndo 才真正删除
                        dissolvingId = target.id
                    }
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶部栏（纯黑终端风格）──────────────────────────────────
        TopAppBar(
            title = {
                AnimatedVisibility(
                    visible = !showSearch,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Text(
                        "消息中心 ~/",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, // AccentOrange
                        fontSize = 18.sp
                    )
                }
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text("搜索 \"...\"", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = RoundedCornerShape(4.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Filled.Clear, "清除", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    )
                }
            },
            actions = {
                // 睡眠模式按钮（点击立即切换，右侧定时图标或长按打开定时睡眠设置）
                val sleepMode by com.messagekeeper.service.SleepModeManager.isSleepMode.collectAsState()
                val sleepSchedule by com.messagekeeper.service.SleepModeManager.scheduleConfig.collectAsState()
                val context = LocalContext.current

                TextButton(
                    onClick = {
                        if (sleepMode) {
                            com.messagekeeper.service.SleepModeManager.exitSleepMode(context)
                            Sonner.info("已退出睡眠模式")
                        } else {
                            com.messagekeeper.service.SleepModeManager.enterSleepMode(context)
                            Sonner.info("已进入睡眠模式")
                        }
                    }
                ) {
                    Icon(
                        if (sleepMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (sleepMode) AccentOrange else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (sleepMode) "我醒了" else "我睡觉啦",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sleepMode) AccentOrange else MaterialTheme.colorScheme.outline
                        )
                        if (sleepSchedule.enabled) {
                            Text(
                                text = sleepSchedule.formatRange(),
                                fontSize = 9.sp,
                                color = AccentGreen
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showSleepScheduleDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "定时睡眠设置",
                        modifier = Modifier.size(16.dp),
                        tint = if (sleepSchedule.enabled) AccentGreen else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.clearSearch()
                }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // ── 表头（终端 CLI 风格）──────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showSearch && searchQuery.isNotBlank() -> {
                    if (isSearching) {
                        EmptyState(title = "搜索中...", icon = { Text("[~]", color = AccentOrange) })
                    } else if (searchResults.isEmpty()) {
                        EmptyState(
                            title = "未找到匹配项",
                            subtitle = "请尝试其他关键词",
                            icon = { Text("[x]", color = MaterialTheme.colorScheme.outline) }
                        )
                    } else {
                        SearchResultsList(
                            results = searchResults,
                            onResultClick = { onConversationClick(it.message.conversation_id) }
                        )
                    }
                }
                showSearch -> {
                    EmptyState(
                        title = "输入关键词搜索",
                        subtitle = "支持搜索消息内容、联系人名称",
                        icon = { Text("[/]", color = AccentOrange) }
                    )
                }
                conversations.isEmpty() -> {
                    EmptyState(
                        title = "暂无消息记录",
                        subtitle = "等待接收新消息..."
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = conversations.filterNot { it.id in pendingDeletionIds }, key = { it.id }, contentType = { "conversation" }) { conversation ->
                            // 删除会话：先播放碎裂动画，动画结束后再走 deleteWithUndo（可撤销）
                            val dissolveState = rememberDissolveState(
                                onDissolved = {
                                    deleteWithUndo(conversation)
                                    if (dissolvingId == conversation.id) dissolvingId = null
                                }
                            )
                            LaunchedEffect(dissolvingId) {
                                if (dissolvingId == conversation.id) {
                                    dissolveState.dissolve()
                                }
                            }
                            DissolveBox(state = dissolveState) {
                                ConversationItem(
                                    conversation = conversation,
                                    onClick = { onConversationClick(conversation.id) },
                                    onLongClick = { conversationActionTarget = conversation },
                                    onMoreClick = { conversationActionTarget = conversation }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
    }

    if (showSleepScheduleDialog) {
        com.messagekeeper.ui.components.SleepScheduleDialog(
            onDismiss = { showSleepScheduleDialog = false }
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "找到 ${results.size} 条匹配项",
                style = MaterialTheme.typography.labelSmall,
                color = AccentOrange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(results, key = { it.message.id }) { result ->
            SearchResultItem(result = result, onClick = { onResultClick(result) })
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
) {
    val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        .format(Date(result.message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 彩色竖线装饰
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(AccentOrange)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = result.conversationName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AccentOrange,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 平台标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "[${Platform.displayName(result.message.platform)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = result.message.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}


