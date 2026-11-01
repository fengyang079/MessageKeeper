package com.messagekeeper.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 手机间迁移的发送端 HTTP 客户端：
 * 与接收端 [PhoneConnectionServer] 通过配对码换取 token，再推送全量备份。
 */
object PhoneTransferClient {

    data class PairResult(val token: String? = null, val error: String? = null)

    suspend fun pair(ip: String, port: Int, code: String): PairResult = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "http://$ip:$port/api/pair"
            val url = URL(targetUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val reqJson = JSONObject().apply {
                put("code", code)
                put("deviceName", deviceName)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqJson.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val respStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val respJson = JSONObject(respStr)
                if (respJson.optBoolean("success")) {
                    return@withContext PairResult(token = respJson.getString("token"))
                }
                return@withContext PairResult(error = "200但success=false: $respStr")
            } else {
                val errorStr = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "no error body"
                return@withContext PairResult(error = "HTTP $responseCode: $errorStr")
            }
        } catch (e: Exception) {
            return@withContext PairResult(error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    suspend fun pushMessages(ip: String, port: Int, token: String, backupData: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/api/push")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true

            backupData.put("token", token)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(backupData.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val respStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return@withContext JSONObject(respStr).optBoolean("success")
            } else if (responseCode == 401) {
                throw SecurityException("Unauthorized")
            }
        } catch (e: Exception) {
            if (e is SecurityException) throw e
            e.printStackTrace()
        }
        false
    }
}
