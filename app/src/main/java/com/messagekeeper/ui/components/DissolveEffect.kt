package com.messagekeeper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════
// 删除粒子 / 碎裂动画（Telegram 风格）
//
// 用法：
//   val dissolve = rememberDissolveState(onDissolved = { /* 动画结束后再真正删除 */ })
//   DissolveBox(state = dissolve) { 行内容 }
//   dissolve.dissolve()   // 触发
//
// 实现：dissolve() 时用 GraphicsLayer 把当前内容录成图层，随后按 8x4 网格切片，
// 每片带随机水平初速度 + 重力下落 + 轻微旋转 + 淡出，约 520ms 结束。
// ═══════════════════════════════════════════════════════════════

/** 网格列数 */
private const val TILE_COLUMNS = 8

/** 网格行数 */
private const val TILE_ROWS = 4

/** 动画总时长（毫秒），落在 450~600ms 区间 */
private const val DISSOLVE_DURATION_MS = 520

/** 重力系数：以内容高度为单位，越往后下落越快 */
private const val GRAVITY_FACTOR = 1.4f

/**
 * 碎裂动画状态。由 [rememberDissolveState] 创建，通过 [dissolve] 触发。
 */
@Stable
class DissolveState internal constructor() {

    internal var active by mutableStateOf(false)
        private set

    internal var progress by mutableFloatStateOf(0f)
        private set

    /** 内容是否已经录进 GraphicsLayer（只在动画第一帧录一次，避免每帧重复录制） */
    internal var recorded = false

    internal var tiles: List<DissolveTile> = emptyList()
        private set

    internal val paint: Paint by lazy { Paint() }

    /** 是否正在播放删除动画（用于避免重复触发） */
    val isDissolving: Boolean
        get() = active

    /** 开始播放碎裂动画；重复调用会被忽略 */
    fun dissolve() {
        if (active) return
        tiles = List(TILE_COLUMNS * TILE_ROWS) { index ->
            DissolveTile(
                column = index % TILE_COLUMNS,
                row = index / TILE_COLUMNS,
                // 水平初速度：-1 ~ 1，按内容宽度缩放
                velocityX = Random.nextFloat() * 2f - 1f,
                // 垂直初速度：小的向上/向下惯性，随后被重力拉下去
                velocityY = Random.nextFloat() * 0.8f - 0.6f,
                // 旋转角度：-30° ~ 30°
                rotation = Random.nextFloat() * 60f - 30f,
                // 逐片延迟：让碎裂有先后层次
                delay = Random.nextFloat() * 0.15f
            )
        }
        recorded = false
        progress = 0f
        active = true
    }

    internal fun setProgress(value: Float) {
        progress = value
    }

    internal fun finish() {
        // 动画播完后保持 active=true：此时所有碎片 alpha 已为 0，内容完全不可见。
        // 等数据层真正删除、列表项被移除即可，避免「删除落库前的几帧原内容闪现」。
        progress = 1f
    }
}

/** 单块碎片参数（几何位置在绘制时按内容尺寸换算成像素） */
internal data class DissolveTile(
    val column: Int,
    val row: Int,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val delay: Float
)

/**
 * 创建与当前 Composable 生命周期绑定的 [DissolveState]。
 *
 * @param onDissolved 动画播完后的回调（通常在这里真正删除数据）。
 */
@Composable
fun rememberDissolveState(onDissolved: () -> Unit): DissolveState {
    val state = remember { DissolveState() }
    val currentOnDissolved by rememberUpdatedState(onDissolved)

    LaunchedEffect(state.active) {
        if (!state.active) return@LaunchedEffect
        val animation = Animatable(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DISSOLVE_DURATION_MS, easing = LinearEasing)
        ) {
            state.setProgress(value)
        }
        state.finish()
        currentOnDissolved()
    }

    return state
}

/**
 * 用 [state] 包住需要「碎裂消失」的内容。
 * 未触发动画时与原样直接绘制内容；触发后改为切片碎裂绘制。
 */
@Composable
fun DissolveBox(
    state: DissolveState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layer = rememberGraphicsLayer()

    Box(
        modifier = modifier.drawWithContent {
            if (!state.active) {
                drawContent()
                return@drawWithContent
            }
            if (size.width <= 0f || size.height <= 0f) return@drawWithContent

            // 内容在动画期间不会变化，只需要录一次图层
            if (!state.recorded) {
                layer.record {
                    this@drawWithContent.drawContent()
                }
                state.recorded = true
            }
            drawDissolveTiles(layer, state, state.progress)
        }
    ) {
        content()
    }
}

/**
 * 把录好的图层按网格切开绘制：
 * - 每片带延迟地开始运动
 * - 水平方向随机漂移，垂直方向先惯性后重力
 * - 绕自身中心轻微旋转
 * - 通过 saveLayer 的 alpha 逐片淡出
 */
private fun DrawScope.drawDissolveTiles(
    layer: GraphicsLayer,
    state: DissolveState,
    progress: Float
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val cellWidth = width / TILE_COLUMNS
    val cellHeight = height / TILE_ROWS
    val paint = state.paint

    state.tiles.forEach { tile ->
        // 单片的本地进度（带起始延迟）
        val local = ((progress - tile.delay) / (1f - tile.delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@forEach

        val alpha = (1f - local).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return@forEach

        val left = tile.column * cellWidth
        val top = tile.row * cellHeight
        val right = if (tile.column == TILE_COLUMNS - 1) width else left + cellWidth
        val bottom = if (tile.row == TILE_ROWS - 1) height else top + cellHeight
        val pivot = Offset((left + right) / 2f, (top + bottom) / 2f)

        val dx = tile.velocityX * width * 0.28f * local
        val dy = (tile.velocityY * height * 0.5f + GRAVITY_FACTOR * height * 0.5f * local) * local
        val rotation = tile.rotation * local

        withTransform({
            translate(dx, dy)
            rotate(rotation, pivot = pivot)
            clipRect(left, top, right, bottom)
        }) {
            if (alpha >= 0.99f) {
                // 还没开始变淡，直接绘制，省一次离屏合成
                drawLayer(layer)
            } else {
                paint.alpha = alpha
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(left, top, right, bottom), paint)
                }
                drawLayer(layer)
                drawIntoCanvas { canvas ->
                    canvas.restore()
                }
            }
        }
    }
}
