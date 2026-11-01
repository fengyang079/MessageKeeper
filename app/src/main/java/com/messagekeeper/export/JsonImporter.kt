package com.messagekeeper.export

import android.content.Context
import android.net.Uri
import com.messagekeeper.data.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

object JsonImporter {
    suspend fun importMessages(context: Context, uri: Uri, repository: MessageRepository): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("Cannot open input stream")

            val trimmed = jsonString.trim()
            if (trimmed.startsWith("{")) {
                val imported = repository.importBackupJson(trimmed)
                return@withContext Result.success(imported)
            }

            val jsonArray = JSONArray(trimmed)
            var importedCount = 0

            for (i in 0 until jsonArray.length()) {
                val msgJson = jsonArray.optJSONObject(i) ?: continue
                
                val platform = msgJson.optString("platform")
                val senderName = msgJson.optString("sender_name")
                val content = msgJson.optString("content")
                val timestamp = msgJson.optLong("timestamp", System.currentTimeMillis())
                val notificationId = msgJson.optString("notification_id")
                val conversationName = msgJson.optString("conversation_name", senderName)
                val isPinned = msgJson.optBoolean("is_pinned", false)

                if (isPinned) {
                    com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned("${platform}_${conversationName}", true)
                }

                repository.saveIncomingMessage(
                    platform = platform,
                    conversationName = conversationName,
                    senderName = senderName,
                    content = content,
                    timestamp = timestamp,
                    notificationId = notificationId
                )
                importedCount++
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
