package com.messagekeeper.data.prefs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class ThemePreferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit {
            putString(KEY_THEME, mode.name)
        }
        _themeMode.value = mode
    }

    companion object {
        private const val KEY_THEME = "theme_mode"

        @Volatile
        private var INSTANCE: ThemePreferences? = null

        fun getInstance(context: Context): ThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
