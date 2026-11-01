package com.messagekeeper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Sonner 的 Compose 移植（交互与视觉对齐 emilkowalski/sonner，深色主题 + richColors）。
 *
 * 原版关键参数（src/index.tsx / styles.css）：
 *   可见 3 条；GAP=14px；缩放步进 0.05；圆角 8px；阴影 0 4px 12px rgba(0,0,0,.1)
 *   lifetime=4000ms；退场 200ms（TIME_BEFORE_UNMOUNT）；滑动阈值 45px 或速度 0.11px/ms
 *   滑动方向锁 + 阻尼 1/(1.5+|Δ|/20)；顶部位置只允许上滑退出
 *   按住时展开整叠并暂停计时；收起时旧卡片内容不可见，仅露卡片边缘
 */
object Sonner {

    /**
     * 深浅色主题 richColors 配色（对齐 styles.css [data-sonner-theme='dark' / 'light'] 段落的 HSL 换算）
     */
    enum class Type(
        val darkBg: Color,
        val darkBorder: Color,
        val darkContent: Color,
        val lightBg: Color,
        val lightBorder: Color,
        val lightContent: Color,
        val icon: ImageVector
    ) {
        SUCCESS(
            darkBg = Color(0xFF001F0F),
            darkBorder = Color(0xFF003D22),
            darkContent = Color(0xFF59F2A6),
            lightBg = Color(0xFFECFDF3),
            lightBorder = Color(0xFFD3FDE5),
            lightContent = Color(0xFF008A2E),
            icon = Icons.Rounded.CheckCircle
        ),
        ERROR(
            darkBg = Color(0xFF2D0706),
            darkBorder = Color(0xFF4D0804),
            darkContent = Color(0xFFFFA19E),
            lightBg = Color(0xFFFFF0F0),
            lightBorder = Color(0xFFFFE0E1),
            lightContent = Color(0xFFE60000),
            icon = Icons.Rounded.Error
        ),
        INFO(
            darkBg = Color(0xFF000D1F),
            darkBorder = Color(0xFF19233E),
            darkContent = Color(0xFF5896F4),
            lightBg = Color(0xFFF0F8FF),
            lightBorder = Color(0xFFDDF3FD),
            lightContent = Color(0xFF0973DC),
            icon = Icons.Rounded.Info
        );

        fun bg(darkTheme: Boolean): Color = if (darkTheme) darkBg else lightBg
        fun border(darkTheme: Boolean): Color = if (darkTheme) darkBorder else lightBorder
        fun content(darkTheme: Boolean): Color = if (darkTheme) darkContent else lightContent
    }

    data class ToastData(
        val id: Long,
        val message: String,
        val type: Type,
        val durationMs: Long,
        val exiting: Boolean = false
    )

    private val _toasts = MutableStateFlow<List<ToastData>>(emptyList())
    val toasts = _toasts.asStateFlow()

    private val nextId = AtomicLong(1L)

    /** VISIBLE_TOASTS_AMOUNT */
    const val VISIBLE_TOASTS = 3

    /** GAP：收起状态下相邻卡片边缘的露出间距 */
    val GAP = 14.dp

    /** SWIPE_THRESHOLD */
    val SWIPE_THRESHOLD = 45.dp

    /** 速度阈值 px/ms，与原版一致 */
    const val SWIPE_VELOCITY = 0.11f

    /** TIME_BEFORE_UNMOUNT / 退场动画时长 */
    const val EXIT_DURATION_MS = 200L

    /** TOAST_LIFETIME */
    const val LIFETIME_MS = 4_000L

    fun success(message: String, durationMs: Long = LIFETIME_MS) = show(message, Type.SUCCESS, durationMs)
    fun error(message: String, durationMs: Long = LIFETIME_MS) = show(message, Type.ERROR, durationMs)
    fun info(message: String, durationMs: Long = LIFETIME_MS) = show(message, Type.INFO, durationMs)

    fun show(message: String, type: Type, durationMs: Long) {
        val truncated = if (message.length > 60) message.take(57) + "..." else message
        val list = _toasts.value
        // 同内容且未退出的提示复用（对齐原版 create 的 alreadyExists 更新语义）
        val existing = list.firstOrNull { it.message == truncated && !it.exiting }
        _toasts.value = if (existing != null) {
            list.map { if (it.id == existing.id) it.copy(durationMs = durationMs) else it }
        } else {
            list + ToastData(nextId.getAndIncrement(), truncated, type, durationMs)
        }
    }

    fun dismiss(id: Long) {
        _toasts.value = _toasts.value.map { if (it.id == id && !it.exiting) it.copy(exiting = true) else it }
    }

    fun purge(id: Long) {
        _toasts.value = _toasts.value.filterNot { it.id == id }
    }

    fun dismissAll() {
        _toasts.value = _toasts.value.filter { it.exiting }
    }
}

/** CSS `transition: transform 400ms, opacity 400ms` 的默认 ease 曲线 */
private val SonnerEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
fun SonnerHost(
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toasts by Sonner.toasts.collectAsState()
    // 按住任意卡片时展开整叠并暂停计时（原版移动端 interacting + expand 语义）
    var expanded by remember { mutableStateOf(false) }
    // 各卡片实测高度（px），展开布局 y_i = Σ_{j<i} h_j + gap * i
    val heights = remember { mutableStateMapOf<Long, Int>() }

    val visible = toasts.take(Sonner.VISIBLE_TOASTS)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp) // MOBILE_VIEWPORT_OFFSET
    ) {
        visible.forEachIndexed { index, toast ->
            key(toast.id) {
                // 展开位置需要"排在它前面（更新）的卡片"的高度之和
                val beforeSumPx = visible.take(index).sumOf { heights[it.id] ?: 0 }
                SonnerToastCard(
                    toast = toast,
                    index = index,
                    darkTheme = darkTheme,
                    expanded = expanded,
                    beforeSumPx = beforeSumPx,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                    onDismiss = { Sonner.dismiss(toast.id) },
                    onPurge = {
                        heights.remove(toast.id)
                        Sonner.purge(toast.id)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex((visible.size - index).toFloat())
                )
            }
        }
    }
}

@Composable
private fun SonnerToastCard(
    toast: Sonner.ToastData,
    index: Int,
    darkTheme: Boolean,
    expanded: Boolean,
    beforeSumPx: Int,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isFront = index == 0
    val shape = RoundedCornerShape(8.dp) // --border-radius: 8px

    // —— 实测自身高度 ——
    var selfHeightPx by remember(toast.id) { mutableStateOf(0) }

    // —— 堆叠布局 ——
    // 收起：y = gap * index，缩放 1 + 0.05 * index（原版 --lift-amount * --toasts-before）
    // 展开：y = 前面卡片实际高度之和 + gap * index（原版 heightIndex * gap + toastsHeightBefore）
    val collapsedY = with(density) { Sonner.GAP.toPx() * index }
    val expandedY = beforeSumPx + with(density) { Sonner.GAP.toPx() * index }
    val targetY: Float = if (!expanded) collapsedY else expandedY.toFloat()
    val targetScale: Float = if (!expanded) 1f + 0.05f * index else 1f
    // 收起时旧卡片内容不可见（data-expanded=false [data-front=false] > * opacity: 0）
    val targetContentAlpha: Float = if (!expanded && !isFront) 0f else 1f

    val animY by animateFloatAsState(targetY, tween(400, easing = SonnerEasing), label = "sonnerY")
    val animScale by animateFloatAsState(targetScale, tween(400, easing = SonnerEasing), label = "sonnerScale")
    val animContentAlpha by animateFloatAsState(
        targetContentAlpha, tween(400, easing = SonnerEasing), label = "sonnerContentAlpha"
    )

    // —— 入场（data-mounted：从 -100% 滑入，400ms）——
    val entered = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.id) {
        entered.animateTo(1f, tween(400, easing = SonnerEasing))
    }

    // —— 退场（data-removed：front 上飞 -100%，非 front 下沉 40%，200ms）——
    val leaveProgress = remember(toast.id) { Animatable(0f) }
    var leaving by remember(toast.id) { mutableStateOf(false) }
    LaunchedEffect(toast.exiting) {
        if (toast.exiting && !leaving) {
            leaving = true
            leaveProgress.animateTo(1f, tween(Sonner.EXIT_DURATION_MS.toInt(), easing = SonnerEasing))
            onPurge()
        }
    }

    // —— 滑动位移（--swipe-amount）——
    val dragOffset = remember(toast.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val swipeOutY = remember(toast.id) { Animatable(0f) }

    // —— 计时器：展开/按压时暂停（原版 pauseTimer + remainingTime）——
    var remainingMs by remember(toast.id, toast.durationMs) { mutableStateOf(toast.durationMs.toFloat()) }
    LaunchedEffect(toast.id, toast.durationMs, expanded, toast.exiting) {
        if (toast.exiting || expanded) return@LaunchedEffect
        while (remainingMs > 0f) {
            val t0 = System.currentTimeMillis()
            delay(50)
            remainingMs -= (System.currentTimeMillis() - t0)
        }
        onDismiss()
    }

    val thresholdPx = with(density) { Sonner.SWIPE_THRESHOLD.toPx() }
    val drag = dragOffset.value
    val swipeOutYPx = swipeOutY.value

    val baseAlpha = entered.value * (1f - leaveProgress.value)
    val dragFade = (1f - (abs(drag.x) + abs(drag.y)) / (thresholdPx * 2f)).coerceAtLeast(0.3f)
    val swipeFade = 1f - (swipeOutYPx / ((selfHeightPx + 40f).coerceAtLeast(1f))).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // 移动端左右各 16px（MOBILE_VIEWPORT_OFFSET）
            .graphicsLayer {
                val leaveY = when {
                    !leaving -> 0f
                    isFront -> -selfHeightPx * leaveProgress.value          // front: translateY(-100%)
                    !expanded -> selfHeightPx * 0.4f * leaveProgress.value  // 非front(收起): translateY(40%)
                    else -> -selfHeightPx * leaveProgress.value             // 非front(展开): 上移退出
                }
                translationY = animY + (-selfHeightPx * (1f - entered.value)) + leaveY + drag.y + swipeOutYPx
                translationX = drag.x
                val s = animScale
                scaleX = s
                scaleY = s
                alpha = baseAlpha * dragFade * swipeFade
            }
            .shadow(elevation = 6.dp, shape = shape) // 0 4px 12px rgba(0,0,0,.1)
            .background(toast.type.bg(darkTheme), shape)
            .border(1.dp, toast.type.border(darkTheme), shape)
            .onSizeChanged { selfHeightPx = it.height }
        .pointerInput(toast.id) {
            // pointerInput 的挂起作用域自带协程上下文（主线程），手势块内用它派发动画协程
            val animScope = CoroutineScope(kotlin.coroutines.coroutineContext)
            awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onExpand() // 按住展开 + 暂停计时
                    var x = 0f
                    var y = 0f
                    var locked: Int? = null // 0 = x，1 = y（原版方向锁）
                    val dragStart = System.currentTimeMillis()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        x += delta.x
                        y += delta.y
                        if (locked == null && (abs(x) > 1f || abs(y) > 1f)) {
                            locked = if (abs(x) > abs(y)) 0 else 1
                        }
                    // 顶部位置只允许上滑退出；其余方向按原版阻尼衰减：1 / (1.5 + |Δ|/20)
                    fun dampen(d: Float) = d / (1.5f + abs(d) / 20f)
                    val target = when (locked) {
                        1 -> if (y < 0f) Offset(0f, y) else Offset(0f, dampen(y))
                        0 -> Offset(dampen(x), 0f)
                        else -> Offset.Zero
                    }
                    // 受限挂起作用域内不能直接调 snapTo，投递到外层动画协程
                    animScope.launch { dragOffset.snapTo(target) }
                    change.consume()
                    }

                    // 松手：判定滑动退出（原版 onPointerUp）
                    val amount = if (locked == 1) abs(y) else abs(x)
                    val timeTaken = (System.currentTimeMillis() - dragStart).coerceAtLeast(1L)
                    val velocity = amount / timeTaken
                    val allowedDirection = locked == 1 && y < 0f // 顶部位置：上滑
                    if (allowedDirection && (amount >= thresholdPx || velocity > Sonner.SWIPE_VELOCITY)) {
                        // swipe-out：200ms ease-out 滑出自身高度并淡出
                        animScope.launch {
                            swipeOutY.animateTo(
                                -(selfHeightPx + 40f),
                                tween(Sonner.EXIT_DURATION_MS.toInt(), easing = SonnerEasing)
                            )
                            onPurge()
                        }
                    } else {
                        // 弹回（原版把 --swipe-amount 重置为 0，由 CSS transition 平滑回弹）
                        animScope.launch {
                            dragOffset.animateTo(
                                Offset.Zero,
                                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                    onCollapse()
                }
            }
            .padding(16.dp), // [data-styled] padding: 16px
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            toast.type.icon,
            contentDescription = null,
            tint = toast.type.content(darkTheme),
            modifier = Modifier
                .size(16.dp) // [data-icon] 16px
                .graphicsLayer { alpha = animContentAlpha }
        )
        Spacer(modifier = Modifier.width(6.dp)) // gap: 6px
        Text(
            text = toast.message,
            color = toast.type.content(darkTheme),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium, // title font-weight 500
            lineHeight = 19.5.sp,           // line-height 1.5
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = animContentAlpha }
        )
    }
}
