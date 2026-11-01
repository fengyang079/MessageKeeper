package com.messagekeeper.export

import android.content.Context
import android.net.Uri
import com.messagekeeper.data.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

object JsonExporter {
    suspend fun exportMessages(context: Context, uri: Uri, repository: MessageRepository): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val messages = repository.getAllMessagesGroupedByConversation()
            if (messages.isEmpty()) {
                return@withContext Result.success(0)
            }
            
            val convs = repository.getAllConversationsSync()
            val convMap = convs.associateBy { it.id }

            val jsonArray = JSONArray()
            for (msg in messages) {
                val conv = convMap[msg.conversation_id]
                val convName = conv?.display_name ?: msg.sender_name
                val msgJson = JSONObject().apply {
                    put("platform", msg.platform)
                    put("conversation_name", convName)
                    put("sender_name", msg.sender_name)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("notification_id", msg.notification_id)
                    put("is_recalled", msg.is_recalled)
                    put("is_pinned", conv?.is_pinned ?: false)
                    put("is_system", conv?.is_system ?: false)
                    put("chat_type", conv?.chat_type ?: 0)
                }
                jsonArray.put(msgJson)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonArray.toString(2))
                }
            } ?: throw Exception("Cannot open output stream")

            Result.success(messages.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
