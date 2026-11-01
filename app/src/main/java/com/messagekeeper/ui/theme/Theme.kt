package com.messagekeeper.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══ 极客深色方案 ═══════════════════════════════════
private val GeekColorScheme = darkColorScheme(
    primary              = AccentOrange,
    onPrimary            = Color.White,
    primaryContainer     = AccentOrangeDim,
    onPrimaryContainer   = AccentOrange,
    secondary            = AccentGreen,
    onSecondary          = Color.White,
    secondaryContainer   = AccentGreenDim,
    onSecondaryContainer = AccentGreen,
    tertiary             = AccentYellow,
    onTertiary           = Color.White,
    error                = AccentRed,
    onError              = Color.White,
    errorContainer       = AccentRedDim,
    onErrorContainer     = AccentRed,
    background           = BgRoot,
    onBackground         = TextPrimary,
    surface              = BgCard,
    onSurface            = TextPrimary,
    surfaceVariant       = BgHover,
    onSurfaceVariant     = TextSecondary,
    surfaceContainerLowest  = BgRoot,
    surfaceContainerLow     = BgCard,
    surfaceContainer        = BgElevated,
    surfaceContainerHigh    = BgHover,
    surfaceContainerHighest = BgActive,
    outline              = TextMuted,
    outlineVariant       = BorderDefault,
    scrim                = Color.Black,
    inverseSurface       = TextPrimary,
    inverseOnSurface     = BgRoot,
    inversePrimary       = AccentOrange,
)

// ═══ 现代明亮方案 ═══════════════════════════════════
private val LightColorScheme = lightColorScheme(
    primary              = AccentOrange,
    onPrimary            = Color.White,
    primaryContainer     = AccentOrangeDim,
    onPrimaryContainer   = AccentOrange,
    secondary            = AccentGreen,
    onSecondary          = Color.White,
    secondaryContainer   = AccentGreenDim,
    onSecondaryContainer = AccentGreen,
    tertiary             = AccentYellow,
    onTertiary           = Color.White,
    error                = AccentRed,
    onError              = Color.White,
    errorContainer       = AccentRedDim,
    onErrorContainer     = AccentRed,
    background           = LightBgRoot,
    onBackground         = LightTextPrimary,
    surface              = LightBgCard,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightBgHover,
    onSurfaceVariant     = LightTextSecondary,
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Color.White,
    surfaceContainer        = Color(0xFFF0F2F8),
    surfaceContainerHigh    = LightBgHover,
    surfaceContainerHighest = Color(0xFFE5E9F2),
    outline              = LightTextMuted,
    outlineVariant       = LightBorder,
    scrim                = Color.Black,
    inverseSurface       = TextPrimary,
    inverseOnSurface     = BgRoot,
    inversePrimary       = AccentOrange,
)

@Composable
fun MessageKeeperTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GeekColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
