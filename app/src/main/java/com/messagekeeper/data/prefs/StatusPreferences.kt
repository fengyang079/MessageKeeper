package com.messagekeeper.data.prefs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusPreferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("status_api_prefs", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(
        prefs.getString(KEY_API_KEY, "") ?: ""
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun updateConfig(url: String, key: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanKey = key.trim()
        prefs.edit {
            putString(KEY_SERVER_URL, cleanUrl)
            putString(KEY_API_KEY, cleanKey)
        }
        _serverUrl.value = cleanUrl
        _apiKey.value = cleanKey
    }

    companion object {
        const val DEFAULT_URL = ""
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"

        @Volatile
        private var INSTANCE: StatusPreferences? = null

        fun getInstance(context: Context): StatusPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatusPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

