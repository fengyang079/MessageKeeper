package com.messagekeeper.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.data.models.DailyStat
import com.messagekeeper.data.models.HourlyStat
import com.messagekeeper.data.models.TrendPoint
import com.messagekeeper.data.models.TrendRange
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showContactPicker by remember { mutableStateOf(false) }

    // 手动刷新时给一个明确的完成/失败提示
    var pendingRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, uiState.errorMessage) {
        if (!uiState.isLoading && pendingRefresh) {
            pendingRefresh = false
            val error = uiState.errorMessage
            if (error == null) {
                com.messagekeeper.ui.components.Sonner.success("统计已刷新")
            } else {
                com.messagekeeper.ui.components.Sonner.error("刷新失败：$error")
            }
        }
    }

    fun refreshWithFeedback() {
        pendingRefresh = true
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("消息统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (uiState.selectedConversationId == null) "当前统计：全部联系人" else "当前统计：${uiState.selectedConversationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 联系人筛选按钮
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showContactPicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = uiState.selectedConversationName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(84.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { refreshWithFeedback() }, enabled = !uiState.isLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新统计")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            uiState.isLoading -> AnalyticsLoadingPage()
            uiState.errorMessage != null -> AnalyticsErrorPage(
                message = uiState.errorMessage.orEmpty(),
                onRetry = { refreshWithFeedback() }
            )
            else -> AnalyticsContent(
                uiState = uiState,
                onSelectTrendRange = { viewModel.selectTrendRange(it) }
            )
        }
    }

    if (showContactPicker) {
        ContactSelectorDialog(
            conversations = uiState.conversations,
            selectedConversationId = uiState.selectedConversationId,
            onSelect = { convId ->
                showContactPicker = false
                viewModel.selectConversation(convId)
            },
            onDismiss = { showContactPicker = false }
        )
    }
}

@Composable
private fun ContactSelectorDialog(
    conversations: List<Conversation>,
    selectedConversationId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchKeyword by remember { mutableStateOf("") }
    val filtered = remember(conversations, searchKeyword) {
        if (searchKeyword.isBlank()) conversations
        else conversations.filter { it.display_name.contains(searchKeyword.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择查看的联系人", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("搜索联系人或群聊...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedConversationId == null,
                                onClick = { onSelect(null) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "全部联系人 (汇总统计)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    items(filtered, key = { it.id }) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(conv.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedConversationId == conv.id,
                                onClick = { onSelect(conv.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.display_name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = Platform.displayName(conv.platform),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AnalyticsLoadingPage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("正在整理统计数据…", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text("读取本地消息记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsErrorPage(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂时无法加载统计", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    onSelectTrendRange: (TrendRange) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TotalMessagesSection(
                total = uiState.totalMessages,
                contactName = uiState.selectedConversationName,
                isSingleContact = uiState.selectedConversationId != null
            )
        }
        item {
            MessageTrendLineChartSection(
                selectedRange = uiState.selectedTrendRange,
                points = uiState.trendPoints,
                onSelectRange = onSelectTrendRange
            )
        }
        item { DailyActivitySection(uiState.dailyStats) }
        item {
            TopContactsSection(
                contacts = uiState.topContacts,
                isSingleContact = uiState.selectedConversationId != null
            )
        }
        item { HourlyHeatmapSection(uiState.hourlyStats) }
    }
}

@Composable
private fun TotalMessagesSection(
    total: Int,
    contactName: String,
    isSingleContact: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (isSingleContact) "「$contactName」的消息总数" else "已保存消息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                NumberFormat.getIntegerInstance().format(total),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isSingleContact) "仅统计该联系人的本地聊天记录" else "不包含软件通知和 QQ 音乐通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageTrendLineChartSection(
    selectedRange: TrendRange,
    points: List<TrendPoint>,
    onSelectRange: (TrendRange) -> Unit
) {
    var selectedPointIndex by remember(points, selectedRange) { mutableIntStateOf(-1) }
    val rangeTotal = remember(points) { points.sumOf { it.count } }
    val maxCount = remember(points) { (points.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val pointCenterColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("消息发送趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "过去 ${selectedRange.label} 共 $rangeTotal 条消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedPointIndex in points.indices) {
                    val pt = points[selectedPointIndex]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${pt.label}：${pt.count} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 时间范围选择栏（5小时 / 12小时 / 1天 / 7天 / 1月 / 6月 / 1年，默认 1天）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrendRange.values().forEach { range ->
                    val selected = range == selectedRange
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectRange(range) },
                        label = { Text(range.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 折线统计图绘制区
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无趋势数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                ) {
                    // 纵轴刻度标签
                    Column(
                        modifier = Modifier
                            .height(160.dp)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(maxCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text((maxCount / 2).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }

                    // 右侧折线画布 + 横轴标签
                    Column(modifier = Modifier.weight(1f)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .pointerInput(points) {
                                    detectTapGestures { tapOffset ->
                                        if (points.size == 1) {
                                            selectedPointIndex = 0
                                        } else if (points.size > 1 && size.width > 0) {
                                            val stepX = size.width.toFloat() / (points.size - 1)
                                            val idx = (tapOffset.x / stepX).roundToInt().coerceIn(0, points.lastIndex)
                                            selectedPointIndex = idx
                                        }
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val topPad = 10.dp.toPx()
                            val bottomPad = 8.dp.toPx()
                            val chartH = (h - topPad - bottomPad).coerceAtLeast(1f)

                            // 1. 绘制 3 条水平参考网格线
                            listOf(0f, 0.5f, 1f).forEach { ratio ->
                                val y = topPad + chartH * ratio
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // 2. 计算所有数据点的坐标
                            val coords = points.mapIndexed { index, pt ->
                                val x = if (points.size <= 1) w / 2f else (w * index.toFloat() / (points.size - 1))
                                val y = topPad + chartH * (1f - (pt.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f))
                                Offset(x, y)
                            }

                            // 3. 折线下方渐变填充区域
                            if (coords.size >= 2) {
                                val fillPath = Path().apply {
                                    moveTo(coords.first().x, topPad + chartH)
                                    coords.forEach { lineTo(it.x, it.y) }
                                    lineTo(coords.last().x, topPad + chartH)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.30f),
                                            primaryColor.copy(alpha = 0.02f)
                                        ),
                                        startY = topPad,
                                        endY = topPad + chartH
                                    )
                                )

                                // 4. 绘制折线本身
                                val linePath = Path().apply {
                                    moveTo(coords.first().x, coords.first().y)
                                    for (i in 1 until coords.size) {
                                        lineTo(coords[i].x, coords[i].y)
                                    }
                                }
                                drawPath(
                                    path = linePath,
                                    color = primaryColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // 5. 绘制选中点的竖直辅助线与所有数据圆点
                            if (selectedPointIndex in coords.indices) {
                                val sel = coords[selectedPointIndex]
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.45f),
                                    start = Offset(sel.x, topPad),
                                    end = Offset(sel.x, topPad + chartH),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val drawAllDots = coords.size <= 15
                            coords.forEachIndexed { idx, offset ->
                                val isSelected = idx == selectedPointIndex
                                if (drawAllDots || isSelected || points[idx].count > 0) {
                                    val outerR = if (isSelected) 5.5.dp.toPx() else 3.5.dp.toPx()
                                    val innerR = if (isSelected) 2.5.dp.toPx() else 1.6.dp.toPx()
                                    drawCircle(
                                        color = primaryColor,
                                        radius = outerR,
                                        center = offset
                                    )
                                    drawCircle(
                                        color = pointCenterColor,
                                        radius = innerR,
                                        center = offset
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // 横轴时间刻度（取首、1/4、中、3/4、尾，防止密集重叠）
                        val tickIndices = remember(points.size) {
                            when {
                                points.size <= 6 -> points.indices.toList()
                                else -> listOf(
                                    0,
                                    points.lastIndex / 4,
                                    points.lastIndex / 2,
                                    (points.lastIndex * 3) / 4,
                                    points.lastIndex
                                ).distinct()
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            tickIndices.forEach { idx ->
                                Text(
                                    text = points[idx].label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyActivitySection(dailyStats: List<DailyStat>) {
    val today = LocalDate.now()
    val dayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
    val statsByDay = dailyStats.associateBy { it.day }
    val values = (6L downTo 0L).map { offset ->
        val day = today.minusDays(offset)
        day to (statsByDay[day.toString()]?.count ?: 0)
    }
    val maxCount = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("过去 7 天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEach { (day, count) ->
                    val fraction = count.toFloat() / maxCount
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((76f * fraction.coerceAtLeast(if (count > 0) 0.06f else 0f)).dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = day.format(dayFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopContactsSection(
    contacts: List<com.messagekeeper.data.models.ContactStat>,
    isSingleContact: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (isSingleContact) "该会话内最活跃发送者" else "消息最多的会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            if (contacts.isEmpty()) {
                Text("还没有可统计的聊天记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val maxCount = contacts.maxOf { it.count }.coerceAtLeast(1)
                contacts.forEachIndexed { index, contact ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = NumberFormat.getIntegerInstance().format(contact.count),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(contact.count.toFloat() / maxCount)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyHeatmapSection(hourlyStats: List<HourlyStat>) {
    val statsByHour = hourlyStats.associateBy { it.hour }
    val maxCount = hourlyStats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("历史消息时段分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("按小时统计聊天记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (column in 0 until 6) {
                        val hour = row * 6 + column
                        val hourLabel = hour.toString().padStart(2, '0')
                        val count = statsByHour[hourLabel]?.count ?: 0
                        val intensity = count.toFloat() / maxCount
                        val tileColor = if (count == 0) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f + 0.84f * intensity)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 6.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tileColor)
                                .padding(4.dp)
                                .semantics { contentDescription = "$hourLabel 时，$count 条消息" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$hourLabel 时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
