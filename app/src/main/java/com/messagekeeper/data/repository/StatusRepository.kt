package com.messagekeeper.data.repository

import android.content.Context
import com.messagekeeper.data.models.EventAttachment
import com.messagekeeper.data.models.EventTimelineLog
import com.messagekeeper.data.models.RecycleBinItem
import com.messagekeeper.data.models.RemoteStatus
import com.messagekeeper.data.models.StateCategory
import com.messagekeeper.data.models.StateEvent
import com.messagekeeper.data.models.StateUser
import com.messagekeeper.data.models.StatusDashboardData
import com.messagekeeper.data.models.StatusHistory
import com.messagekeeper.data.models.StatusPreset
import com.messagekeeper.data.models.StatusStats
import com.messagekeeper.data.models.SystemAlert
import com.messagekeeper.data.models.UserSession
import com.messagekeeper.data.prefs.StatusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StatusRepository private constructor(context: Context) {

    private val prefs = StatusPreferences.getInstance(context)
    private val deviceId: String = try {
        "APP-${android.os.Build.BRAND}-${android.os.Build.MODEL}".replace("\\s+".toRegex(), "_")
    } catch (_: Exception) {
        "APP-Android"
    }

    private fun openConnection(
        path: String,
        method: String = "GET",
        queryParams: Map<String, String> = emptyMap(),
        overrideBaseUrl: String? = null
    ): HttpURLConnection {
        val baseUrl = (overrideBaseUrl ?: prefs.serverUrl.value).trim().removeSuffix("/")
        if (baseUrl.isBlank()) {
            throw Exception("请先点击右上角设置配置服务器地址 (Base URL)")
        }
        val apiKey = prefs.apiKey.value

        val query = if (queryParams.isNotEmpty()) {
            "?" + queryParams.map { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }.joinToString("&")
        } else ""

        val fullUrl = "$baseUrl$path$query"
        val url = URL(fullUrl)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("User-Agent", "MessageKeeper-Android (${android.os.Build.MODEL})")
            if (apiKey.isNotBlank()) {
                setRequestProperty("X-API-Key", apiKey)
            }
        }
    }

    private fun parseUserObject(u: JSONObject): StateUser {
        val role = u.optString("role", "user")
        val rawLevel = u.optInt("level", 1)
        val effectiveLevel = if (role == "admin" && rawLevel < 4) 4 else rawLevel
        return StateUser(
            id = u.optLong("id"),
            username = u.optString("username"),
            level = effectiveLevel,
            status = u.optString("status", "active"),
            apiKey = u.optString("api_key"),
            note = u.optString("note"),
            lastIp = u.optString("last_ip"),
            lastDeviceId = u.optString("last_device_id"),
            lastUserAgent = u.optString("last_user_agent"),
            lastSeenAt = u.optString("last_seen_at"),
            createdAt = u.optString("created_at")
        )
    }

    // ── 0. 账户认证（密码登录 / 注册 / 当前身份校验） ──
    suspend fun fetchCurrentUser(): Result<StateUser?> = withContext(Dispatchers.IO) {
        try {
            if (prefs.apiKey.value.isBlank()) {
                return@withContext Result.success(null)
            }
            val conn = openConnection("/api/auth/me")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val authed = json.optBoolean("authenticated", false)
            val userObj = json.optJSONObject("user")
            if (authed && userObj != null) {
                Result.success(parseUserObject(userObj))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPassword(
        baseUrl: String,
        username: String,
        password: String
    ): Result<Pair<String, StateUser>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/auth/login", method = "POST", overrideBaseUrl = baseUrl).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("username", username.trim())
                put("password", password)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val msg = json.optString("message", "登录成功")
            val userObj = json.optJSONObject("user") ?: throw Exception("服务器未返回账户信息")
            Result.success(Pair(msg, parseUserObject(userObj)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAccount(
        baseUrl: String,
        username: String,
        password: String,
        note: String = ""
    ): Result<Pair<String, StateUser>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/auth/register", method = "POST", overrideBaseUrl = baseUrl).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("username", username.trim())
                put("password", password)
                if (note.isNotBlank()) put("note", note.trim())
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val msg = json.optString("message", "注册成功")
            val userObj = json.optJSONObject("user") ?: throw Exception("服务器未返回账户信息")
            Result.success(Pair(msg, parseUserObject(userObj)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readResponseOrThrow(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code in 200..299) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val res = reader.use { it.readText() }
            conn.disconnect()
            return res
        } else {
            val stream = conn.errorStream ?: conn.inputStream
            val errText = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
            conn.disconnect()
            val errMsg = try {
                JSONObject(errText).optString("error", "HTTP $code")
            } catch (_: Exception) {
                "HTTP $code: $errText"
            }
            throw Exception(errMsg)
        }
    }

    // ── 1. 状态仪表盘 ──
    suspend fun fetchStatusDashboard(): Result<StatusDashboardData> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/status")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val statusObj = json.optJSONObject("status") ?: JSONObject()

            val remoteStatus = RemoteStatus(
                label = statusObj.optString("label", "未设置"),
                category = statusObj.optString("category", "日常"),
                description = statusObj.optString("description", ""),
                color = statusObj.optString("color", "#DCE9F5"),
                isOnline = statusObj.optBoolean("is_online", true),
                publicVisible = statusObj.optBoolean("public_visible", true),
                updatedAt = statusObj.optString("updated_at", ""),
                autoRevertAt = if (statusObj.has("auto_revert_at") && !statusObj.isNull("auto_revert_at")) statusObj.optString("auto_revert_at") else null
            )

            val presets = mutableListOf<StatusPreset>()
            val presetsArr = json.optJSONArray("presets") ?: JSONArray()
            for (i in 0 until presetsArr.length()) {
                val p = presetsArr.optJSONObject(i) ?: continue
                presets.add(
                    StatusPreset(
                        id = p.optLong("id"),
                        label = p.optString("label"),
                        category = p.optString("category"),
                        description = p.optString("description"),
                        color = p.optString("color", "#DCE9F5")
                    )
                )
            }

            val history = mutableListOf<StatusHistory>()
            val historyArr = json.optJSONArray("history") ?: JSONArray()
            for (i in 0 until historyArr.length()) {
                val h = historyArr.optJSONObject(i) ?: continue
                history.add(
                    StatusHistory(
                        id = h.optLong("id"),
                        label = h.optString("label"),
                        category = h.optString("category"),
                        description = h.optString("description"),
                        color = h.optString("color", "#DCE9F5"),
                        createdAt = h.optString("created_at")
                    )
                )
            }

            Result.success(
                StatusDashboardData(
                    status = remoteStatus,
                    presets = presets,
                    history = history,
                    serverNow = json.optString("server_now", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(
        label: String,
        category: String,
        description: String,
        color: String,
        durationMinutes: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/status", method = "PUT").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("label", label)
                put("category", category.ifBlank { "日常" })
                put("description", description)
                put("color", color)
                if (durationMinutes > 0) put("duration_minutes", durationMinutes)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            val msg = try { JSONObject(text).optString("message", "状态已更新") } catch (_: Exception) { "状态已更新" }
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPreset(label: String, category: String, description: String, color: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/status/presets", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("label", label)
                put("category", category)
                put("description", description)
                put("color", color)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "已添加快捷标签"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePreset(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/status/presets/$id", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHistory(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/status/history/$id", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 2. 全站公告 ──
    suspend fun fetchAlert(): Result<SystemAlert?> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/alert")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            if (json.isNull("alert")) {
                Result.success(null)
            } else {
                val a = json.getJSONObject("alert")
                Result.success(
                    SystemAlert(
                        id = a.optLong("id"),
                        message = a.optString("message"),
                        level = a.optString("level", "blue"),
                        updatedAt = a.optString("updated_at")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun publishAlert(message: String, level: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/alert", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("message", message)
                put("level", level)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "公告已发布"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAlert(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/alert", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 3. 事件跟踪 ──
    suspend fun fetchEvents(
        type: String = "all",
        urgency: String = "all",
        archived: String = "0",
        search: String = ""
    ): Result<List<StateEvent>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf<String, String>()
            if (type != "all") params["type"] = type
            if (urgency != "all") params["urgency"] = urgency
            if (archived != "all") params["archived"] = archived
            if (search.isNotBlank()) params["search"] = search

            val conn = openConnection("/api/events", queryParams = params)
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val arr = json.optJSONArray("events") ?: JSONArray()
            val list = mutableListOf<StateEvent>()

            for (i in 0 until arr.length()) {
                val ev = arr.optJSONObject(i) ?: continue
                val logsList = mutableListOf<EventTimelineLog>()
                val logsArr = ev.optJSONArray("logs") ?: JSONArray()
                for (j in 0 until logsArr.length()) {
                    val lg = logsArr.optJSONObject(j) ?: continue
                    val attList = mutableListOf<EventAttachment>()
                    val attArr = lg.optJSONArray("attachments") ?: JSONArray()
                    for (k in 0 until attArr.length()) {
                        val at = attArr.optJSONObject(k) ?: continue
                        attList.add(
                            EventAttachment(
                                name = at.optString("name"),
                                url = at.optString("url"),
                                size = at.optLong("size"),
                                isImage = at.optBoolean("is_image")
                            )
                        )
                    }
                    logsList.add(
                        EventTimelineLog(
                            id = lg.optLong("id"),
                            eventId = lg.optLong("event_id"),
                            logType = lg.optString("log_type", "跟进"),
                            content = lg.optString("content"),
                            attachments = attList,
                            createdAt = lg.optString("created_at")
                        )
                    )
                }

                list.add(
                    StateEvent(
                        id = ev.optLong("id"),
                        title = ev.optString("title"),
                        type = ev.optString("type", "通用"),
                        urgency = ev.optInt("urgency", 3),
                        visibility = ev.optInt("visibility", 1),
                        isPinned = ev.optBoolean("is_pinned"),
                        isArchived = ev.optBoolean("is_archived"),
                        description = ev.optString("description"),
                        logs = logsList,
                        createdAt = ev.optString("created_at"),
                        updatedAt = ev.optString("updated_at"),
                        archivedAt = if (ev.has("archived_at") && !ev.isNull("archived_at")) ev.optString("archived_at") else null
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEvent(
        title: String,
        type: String,
        urgency: Int,
        visibility: Int,
        description: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("title", title)
                put("type", type.ifBlank { "通用" })
                put("urgency", urgency)
                put("visibility", visibility)
                put("description", description)
                put("is_archived", false)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "事件已创建"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFiles(context: Context, uris: List<android.net.Uri>): Result<List<EventAttachment>> = withContext(Dispatchers.IO) {
        try {
            if (uris.isEmpty()) return@withContext Result.success(emptyList())

            val boundary = "Boundary" + System.currentTimeMillis()
            val conn = openConnection("/api/upload", method = "POST").apply {
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            val out = java.io.DataOutputStream(conn.outputStream)
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            for (uri in uris) {
                var fileName = "file_${System.currentTimeMillis()}"
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val n = it.getString(nameIndex)
                            if (!n.isNullOrBlank()) fileName = n
                        }
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                out.writeBytes(twoHyphens + boundary + lineEnd)
                out.writeBytes("Content-Disposition: form-data; name=\"files\"; filename=\"$fileName\"" + lineEnd)
                out.writeBytes("Content-Type: $mimeType" + lineEnd)
                out.writeBytes(lineEnd)

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("无法读取文件流: $fileName")
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                out.writeBytes(lineEnd)
            }

            out.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            out.flush()
            out.close()

            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val attArr = json.optJSONArray("attachments") ?: JSONArray()
            val list = mutableListOf<EventAttachment>()
            for (i in 0 until attArr.length()) {
                val at = attArr.optJSONObject(i) ?: continue
                list.add(
                    EventAttachment(
                        name = at.optString("name"),
                        url = at.optString("url"),
                        size = at.optLong("size"),
                        isImage = at.optBoolean("is_image")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addEventLog(
        eventId: Long,
        logType: String,
        content: String,
        attachments: List<EventAttachment> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId/logs", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val attJsonArr = JSONArray()
            for (att in attachments) {
                attJsonArr.put(JSONObject().apply {
                    put("name", att.name)
                    put("url", att.url)
                    put("size", att.size)
                    put("is_image", att.isImage)
                })
            }
            val payload = JSONObject().apply {
                put("log_type", logType)
                put("content", content)
                put("attachments", attJsonArr)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "跟进已添加"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEventLog(eventId: Long, logId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId/logs/$logId", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePinEvent(eventId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId/pin", method = "PATCH")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleArchiveEvent(eventId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId/archive", method = "PATCH")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportEventMarkdown(eventId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/events/$eventId/export-md")
            val text = readResponseOrThrow(conn)
            val md = JSONObject(text).optString("markdown", "")
            Result.success(md)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 4. 自定义类目 ──
    suspend fun fetchCategories(): Result<List<StateCategory>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/categories")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val arr = json.optJSONArray("categories") ?: JSONArray()
            val list = mutableListOf<StateCategory>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                list.add(
                    StateCategory(
                        id = c.optLong("id"),
                        name = c.optString("name"),
                        color = c.optString("color", "#3b82f6")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCategory(name: String, color: String = "#3b82f6"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/categories", method = "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("name", name)
                put("color", color)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "类目已创建"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/categories/$id", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 5. 账户与终端审计 ──
    suspend fun fetchUsers(): Result<List<StateUser>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/users")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val arr = json.optJSONArray("users") ?: JSONArray()
            val list = mutableListOf<StateUser>()
            for (i in 0 until arr.length()) {
                val u = arr.optJSONObject(i) ?: continue
                list.add(
                    StateUser(
                        id = u.optLong("id"),
                        username = u.optString("username"),
                        level = u.optInt("level", 1),
                        status = u.optString("status", "active"),
                        apiKey = u.optString("api_key"),
                        note = u.optString("note"),
                        lastIp = u.optString("last_ip"),
                        lastDeviceId = u.optString("last_device_id"),
                        lastUserAgent = u.optString("last_user_agent"),
                        lastSeenAt = u.optString("last_seen_at"),
                        createdAt = u.optString("created_at")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserSessions(userId: Long): Result<List<UserSession>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/users/$userId/sessions")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val arr = json.optJSONArray("sessions") ?: JSONArray()
            val list = mutableListOf<UserSession>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                list.add(
                    UserSession(
                        id = s.optLong("id"),
                        ip = s.optString("ip"),
                        deviceId = s.optString("device_id"),
                        userAgent = s.optString("user_agent"),
                        action = s.optString("action"),
                        createdAt = s.optString("created_at")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserLevel(userId: Long, level: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/users/$userId", method = "PUT").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("level", level)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/users/$userId", method = "DELETE")
            readResponseOrThrow(conn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 6. 统计数据 ──
    suspend fun fetchStats(): Result<StatusStats> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/stats")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            Result.success(
                StatusStats(
                    active = json.optInt("active", 0),
                    urgent = json.optInt("urgent", 0),
                    archived = json.optInt("archived", 0),
                    weekLogs = json.optInt("week_logs", 0)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 7. 最近删除 / 回收站 ──
    suspend fun fetchRecycleBin(): Result<List<RecycleBinItem>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/recycle-bin")
            val text = readResponseOrThrow(conn)
            val json = JSONObject(text)
            val arr = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<RecycleBinItem>()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                list.add(
                    RecycleBinItem(
                        id = it.optLong("id"),
                        itemType = it.optString("item_type", "event"),
                        originalId = it.optLong("original_id"),
                        title = it.optString("title"),
                        subtitle = it.optString("subtitle"),
                        deletedBy = it.optString("deleted_by", "admin"),
                        deletedAt = it.optString("deleted_at")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreRecycleBinItem(id: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/recycle-bin/$id/restore", method = "POST")
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "已成功恢复"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecycleBinItem(id: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/recycle-bin/$id", method = "DELETE")
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "已彻底删除"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearRecycleBin(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("/api/recycle-bin", method = "DELETE")
            val text = readResponseOrThrow(conn)
            Result.success(JSONObject(text).optString("message", "已清空回收站"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StatusRepository? = null

        fun getInstance(context: Context): StatusRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatusRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
