package com.messagekeeper.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * 持久化保存已置顶会话的唯一标识 (conversation_key)，
 * 作为数据库的双重保险：无论是应用升级、会话合并、从历史删除恢复、还是备份还原，
 * 都能确保置顶状态不会丢失。
 */
object PinnedConversationsManager {

    private const val PREF_NAME = "pinned_conversations_pref"
    private const val KEY_PINNED_SET = "pinned_keys_set"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    private fun ensurePrefs(): SharedPreferences? {
        prefs?.let { return it }
        return runCatching {
            val ctx = com.messagekeeper.MessageKeeperApp.instance.applicationContext
            init(ctx)
            prefs
        }.getOrNull()
    }

    fun getPinnedKeys(): Set<String> {
        val sp = ensurePrefs() ?: return emptySet()
        return sp.getStringSet(KEY_PINNED_SET, emptySet())?.toSet() ?: emptySet()
    }

    fun isPinned(conversationKey: String): Boolean {
        if (conversationKey.isBlank()) return false
        return conversationKey in getPinnedKeys()
    }

    fun setPinned(conversationKey: String, pinned: Boolean) {
        if (conversationKey.isBlank()) return
        val sp = ensurePrefs() ?: return
        val current = (sp.getStringSet(KEY_PINNED_SET, emptySet()) ?: emptySet()).toMutableSet()
        val changed = if (pinned) current.add(conversationKey) else current.remove(conversationKey)
        if (changed) {
            sp.edit().putStringSet(KEY_PINNED_SET, current).apply()
        }
    }

    fun addPinnedKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val sp = ensurePrefs() ?: return
        val current = (sp.getStringSet(KEY_PINNED_SET, emptySet()) ?: emptySet()).toMutableSet()
        if (current.addAll(keys.filter { it.isNotBlank() })) {
            sp.edit().putStringSet(KEY_PINNED_SET, current).apply()
        }
    }
}

