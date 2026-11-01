package com.messagekeeper.network

import android.util.Log
import com.messagekeeper.data.repository.MessageRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PhoneConnectionServer(
    port: Int,
    private val expectedCode: String,
    private val repository: MessageRepository,
    private val onDevicePaired: (String) -> Unit,
    private val onMessageReceived: (Int) -> Unit
) : NanoHTTPD(port) {

    private val validTokens = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pairedDeviceName: String? = null

    override fun serve(session: IHTTPSession): Response {
        try {
            val uri = session.uri
            val method = session.method

            // Only support POST for api routes
            if (method != Method.POST) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
            }

            // Parse body safely as UTF-8
            val postData: String = try {
                val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val buf = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val r = session.inputStream.read(buf, totalRead, contentLength - totalRead)
                        if (r <= 0) break
                        totalRead += r
                    }
                    String(buf, 0, totalRead, Charsets.UTF_8)
                } else {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val rawStr = map["postData"] ?: map["content"] ?: ""
                    String(rawStr.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.e("PhoneServer", "读取 UTF-8 POST 数据失败", e)
                val map = HashMap<String, String>()
                session.parseBody(map)
                val rawStr = map["postData"] ?: ""
                String(rawStr.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            }

            if (postData.isBlank()) return jsonResponse(false, "No post data")
            
            val json = JSONObject(postData)

            when (uri) {
                "/api/pair" -> {
                    val code = json.optString("code")
                    val deviceName = json.optString("deviceName", "Unknown Device")
                    if (code == expectedCode) {
                        val token = UUID.randomUUID().toString()
                        validTokens.add(token)
                        pairedDeviceName = deviceName
                        
                        // Notify UI
                        scope.launch(Dispatchers.Main) {
                            onDevicePaired(deviceName)
                        }
                        
                        val responseJson = JSONObject().apply {
                            put("success", true)
                            put("token", token)
                        }
                        return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString())
                    } else {
                        return jsonResponse(false, "Invalid pairing code")
                    }
                }
                "/api/push" -> {
                    val token = json.optString("token")
                    if (!validTokens.contains(token)) {
                        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"success\":false,\"error\":\"Unauthorized\"}")
                    }

                    scope.launch {
                        // 合并语义：只补齐本机没有的消息，绝不删除本地已有数据。
                        // 同一时刻的相同消息（含两台设备同时在线导致的时间戳抖动）
                        // 由 importBackupJson 内部去重，一条消息只会显示一次。
                        val count = try {
                            if (json.has("conversations")) {
                                repository.importBackupJson(json.toString())
                            } else {
                                // 兼容旧版发送端：只带 messages 字段，逐条合并
                                // （saveIncomingMessage 自带重复广播判定，重复数据不会二次入库）
                                val messages = json.optJSONArray("messages") ?: JSONArray()
                                for (i in 0 until messages.length()) {
                                    val msg = messages.optJSONObject(i) ?: continue
                                    val platform = msg.optString("platform")
                                    val senderName = msg.optString("sender_name")
                                    val content = msg.optString("content")
                                    val timestamp = msg.optLong("timestamp", System.currentTimeMillis())
                                    val notificationId = msg.optString("notification_id", UUID.randomUUID().toString())
                                    val conversationName = msg.optString("conversation_name", senderName)
                                    repository.saveIncomingMessage(platform, conversationName, senderName, content, timestamp, notificationId)
                                }
                                messages.length()
                            }
                        } catch (e: Exception) {
                            // 事务已整体回滚，本地数据保持原样
                            Log.e("PhoneServer", "合并推送数据失败，本地数据未变更", e)
                            0
                        }

                        launch(Dispatchers.Main) {
                            onMessageReceived(count)
                        }
                    }

                    return jsonResponse(true, null)
                }
                else -> {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e("PhoneServer", "Server error", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private fun jsonResponse(success: Boolean, error: String?): Response {
        val json = JSONObject().apply {
            put("success", success)
            if (error != null) put("error", error)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }
}
