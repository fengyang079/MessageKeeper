package com.messagekeeper.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// GEEK & MODERN COLOR SYSTEM
// ═══════════════════════════════════════════════════════════════

// ── Surfaces ──────────────────────────────────────────────────
// 深色
val BgRoot          = Color(0xFF0E1015)    // 最深背景
val BgSidebar       = Color(0xFF13161D)    // 侧边栏
val BgHover         = Color(0xFF1E222D)    // Hover 态 / 次级容器
val BgActive        = Color(0xFF252A37)    // 选中态
val BgCard          = Color(0xFF171A22)    // 卡片/容器
val BgElevated      = Color(0xFF1D212B)    // 弹窗/浮层
val BorderDefault   = Color(0xFF282D3A)    // 默认边框
val BorderSubtle    = Color(0xFF1F232E)    // 极细分隔线

// 浅色
val LightBgRoot     = Color(0xFFF4F6FA)
val LightBgCard     = Color(0xFFFFFFFF)
val LightBgHover    = Color(0xFFEDF0F7)
val LightBorder     = Color(0xFFE2E6F0)

// ── Text hierarchy ───────────────────────────────────────────
// 深色
val TextPrimary     = Color(0xFFF0F2F5)    // 主文字：柔和白
val TextSecondary   = Color(0xFF9EA4B0)    // 次要文字
val TextMuted       = Color(0xFF6B7280)    // 低优先级
val TextDim         = Color(0xFF4B5563)    // 极暗提示

// 浅色
val LightTextPrimary   = Color(0xFF1A1D24)
val LightTextSecondary = Color(0xFF5E6573)
val LightTextMuted     = Color(0xFF8C93A4)

// ── Accents（兼顾深色与浅色背景的高对比度）──────────────────
val AccentOrange    = Color(0xFFFF6B00)    // 主强调：活力橙
val AccentGreen     = Color(0xFF10B981)    // 状态：翠绿（深浅模式皆清晰可读）
val AccentRed       = Color(0xFFEF4444)    // 危险/错误/删除
val AccentYellow    = Color(0xFFF59E0B)    // 琥珀黄（告别浅色下刺眼看不清的柠檬黄）
val AccentBlue      = Color(0xFF3B82F6)    // 科技蓝（辅助图标与状态强调色）

// ── Accent containers（低透明度底色）───────────────────────
val AccentOrangeDim = Color(0x1FFF6B00)    // ~12% alpha
val AccentGreenDim  = Color(0x1F10B981)
val AccentRedDim    = Color(0x1FEF4444)

// ── Status dots ──────────────────────────────────────────────
val DotUnread       = AccentOrange
val DotRead         = Color(0xFF4B5563)

// ── 头像色板（10色，深浅模式均高辨识度）──────────────────────
val AvatarColors = listOf(
    Color(0xFFFF6B00),  // 活力橙
    Color(0xFF10B981),  // 翠绿
    Color(0xFF06B6D4),  // 青蓝
    Color(0xFFEF4444),  // 珊瑚红
    Color(0xFFF59E0B),  // 琥珀金
    Color(0xFF3B82F6),  // 科技蓝
    Color(0xFFEC4899),  // 玫粉
    Color(0xFF8B5CF6),  // 优雅紫
    Color(0xFF14B8A6),  // 蓝绿
    Color(0xFFF97316),  // 暖橘
)

fun getAvatarColor(index: Int): Color {
    return AvatarColors[((index % AvatarColors.size) + AvatarColors.size) % AvatarColors.size]
}

// ── 气泡色 ───────────────────────────────────────────────────
val BubbleOtherLight = Color(0xFF1A1A1A)
val BubbleOtherDark  = Color(0xFF1A1A1A)
