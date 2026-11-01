package com.messagekeeper.export

import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.db.entity.Platform
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TXT 格式导出器
 */
object TextExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 导出消息列表为 TXT 文件
     */
    fun export(messages: List<Message>, outputFile: File): File {
        val sb = StringBuilder()

        sb.appendLine("========================================")
        sb.appendLine("       消息助手 - 聊天记录导出")
        sb.appendLine("========================================")
        sb.appendLine("导出时间：${dateFormat.format(Date())}")
        sb.appendLine("总消息数：${messages.size}")
        sb.appendLine("========================================")
        sb.appendLine()

        // 按对话分组
        val grouped = messages.groupBy { it.conversation_id }

        grouped.forEach { (_, conversationMessages) ->
            if (conversationMessages.isNotEmpty()) {
                val first = conversationMessages.first()
                sb.appendLine("━━━ ${first.sender_name}（${Platform.displayName(first.platform)}）━━━")
                sb.appendLine()

                conversationMessages.forEach { message ->
                    val time = dateFormat.format(Date(message.timestamp))
                    val prefix = if (message.is_recalled) "[已撤回] " else ""
                    sb.appendLine("[$time] ${message.sender_name}：$prefix${message.content}")
                }

                sb.appendLine()
                sb.appendLine("───────────────────────────────────────")
                sb.appendLine()
            }
        }

        sb.appendLine("========================================")
        sb.appendLine("          导出完毕")
        sb.appendLine("========================================")

        outputFile.writeText(sb.toString(), Charsets.UTF_8)
        return outputFile
    }
}
