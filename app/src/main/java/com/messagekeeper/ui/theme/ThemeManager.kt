package com.messagekeeper.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 主题模式
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/**
 * 主题管理器 - 全局控制深浅色模式
 */
object ThemeManager {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
}
