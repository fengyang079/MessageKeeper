package com.messagekeeper.export

import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.db.entity.Platform
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTML 格式导出器
 * 生成带样式的聊天记录网页（首页仅展示会话标题列表，点击会话进入对应聊天详情页）
 */
object HtmlExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun export(
        messages: List<Message>,
        outputFile: File,
        conversations: Map<Long, Conversation> = emptyMap()
    ): File {
        val sb = StringBuilder()

        val grouped = messages.groupBy { it.conversation_id }
        val nonEmptyGroups = grouped.entries.filter { it.value.isNotEmpty() }

        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"zh-CN\">")
        sb.appendLine("<head>")
        sb.appendLine("<meta charset=\"UTF-8\">")
        sb.appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.appendLine("<title>消息助手 - 聊天记录导出</title>")
        sb.appendLine("<style>")
        sb.appendLine(createStyles())
        sb.appendLine("</style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")

        // ── 列表首页视图（默认只显示会话标题卡片，点击进入详情） ──
        sb.appendLine("<div id=\"list-view\">")
        sb.appendLine("<div class=\"header\">")
        sb.appendLine("<h1>消息助手 · 聊天记录</h1>")
        sb.appendLine("<p>导出时间：${dateFormat.format(Date())} · 共 ${nonEmptyGroups.size} 个会话 · ${messages.size} 条消息</p>")
        sb.appendLine("<div class=\"search-box\"><input id=\"conv-search\" type=\"text\" placeholder=\"搜索联系人或群聊标题...\" oninput=\"filterConvs(this.value)\" /></div>")
        sb.appendLine("</div>")

        sb.appendLine("<div class=\"conv-list\" id=\"conv-list\">")
        nonEmptyGroups.forEachIndexed { idx, (convId, convMessages) ->
            val first = convMessages.first()
            val last = convMessages.last()
            val conv = conversations[convId]
            val title = conv?.display_name?.ifBlank { first.sender_name } ?: first.sender_name
            val platformName = Platform.displayName(conv?.platform ?: first.platform)
            val lastTimeStr = dateFormat.format(Date(last.timestamp))
            val avatarChar = title.firstOrNull()?.toString() ?: "#"
            val avatarColor = getSenderColor(title)

            sb.appendLine(
                """
                <div class="conv-card" data-title="${escapeHtml(title.lowercase())}" onclick="openConv($idx)">
                    <div class="conv-avatar" style="background: ${avatarColor}22; color: $avatarColor;">${escapeHtml(avatarChar)}</div>
                    <div class="conv-info">
                        <div class="conv-title-row">
                            <span class="platform-badge">$platformName</span>
                            <span class="conv-title">${escapeHtml(title)}</span>
                        </div>
                        <div class="conv-meta">最后消息：$lastTimeStr</div>
                    </div>
                    <div class="conv-right">
                        <span class="msg-count">${convMessages.size} 条</span>
                        <span class="arrow">›</span>
                    </div>
                </div>
                """.trimIndent()
            )
        }
        sb.appendLine("</div>")
        sb.appendLine("</div>")

        // ── 详情页视图（点击某个标题后展示该会话的聊天记录） ──
        sb.appendLine("<div id=\"detail-view\" style=\"display:none;\">")
        nonEmptyGroups.forEachIndexed { idx, (convId, convMessages) ->
            val first = convMessages.first()
            val conv = conversations[convId]
            val title = conv?.display_name?.ifBlank { first.sender_name } ?: first.sender_name
            val platformName = Platform.displayName(conv?.platform ?: first.platform)

            sb.appendLine("<div class=\"conversation-detail\" id=\"conv-detail-$idx\" style=\"display:none;\">")
            sb.appendLine("<div class=\"detail-Sticky-header\">")
            sb.appendLine("<button class=\"back-btn\" onclick=\"backToList()\">← 返回会话列表</button>")
            sb.appendLine("<div class=\"detail-title-wrap\">")
            sb.appendLine("<span class=\"platform-badge\">$platformName</span>")
            sb.appendLine("<span class=\"detail-title\">${escapeHtml(title)}</span>")
            sb.appendLine("<span class=\"detail-count\">(${convMessages.size} 条)</span>")
            sb.appendLine("</div>")
            sb.appendLine("</div>")
            sb.appendLine("<div class=\"messages\">")

            convMessages.forEach { message ->
                sb.appendLine(renderMessage(message))
            }

            sb.appendLine("</div>")
            sb.appendLine("</div>")
        }
        sb.appendLine("</div>")

        sb.appendLine("<div class=\"footer\">")
        sb.appendLine("<p>由「消息助手」生成 · 数据仅保存在本地</p>")
        sb.appendLine("</div>")

        sb.appendLine("<script>")
        sb.appendLine(createScript())
        sb.appendLine("</script>")
        sb.appendLine("</body>")
        sb.appendLine("</html>")

        outputFile.writeText(sb.toString(), Charsets.UTF_8)
        return outputFile
    }

    private fun renderMessage(message: Message): String {
        val time = timeFormat.format(Date(message.timestamp))
        val dateStr = dateOnlyFormat.format(Date(message.timestamp))
        val timeDisplay = "$dateStr $time"

        return if (message.is_recalled) {
            """
            <div class="message recalled">
                <div class="message-header">
                    <span class="sender-name" style="color: ${getSenderColor(message.sender_name)}">${escapeHtml(message.sender_name)}</span>
                    <span class="message-time">$timeDisplay</span>
                </div>
                <div class="recalled-badge">⚠️ [已撤回] ${escapeHtml(message.content).replace("\n", "<br>")}</div>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="message">
                <div class="message-header">
                    <span class="sender-name" style="color: ${getSenderColor(message.sender_name)}">${escapeHtml(message.sender_name)}</span>
                    <span class="message-time">$timeDisplay</span>
                </div>
                <div class="message-bubble">
                    ${escapeHtml(message.content).replace("\n", "<br>")}
                </div>
            </div>
            """.trimIndent()
        }
    }

    private fun createScript(): String = """
        var currentDetailIdx = -1;
        var savedScrollY = 0;

        function openConv(idx) {
            savedScrollY = window.scrollY || document.documentElement.scrollTop;
            document.getElementById('list-view').style.display = 'none';
            document.getElementById('detail-view').style.display = 'block';
            if (currentDetailIdx >= 0) {
                var prev = document.getElementById('conv-detail-' + currentDetailIdx);
                if (prev) prev.style.display = 'none';
            }
            var target = document.getElementById('conv-detail-' + idx);
            if (target) target.style.display = 'block';
            currentDetailIdx = idx;
            window.scrollTo(0, 0);
            try { history.pushState({ conv: idx }, ''); } catch (e) {}
        }

        function backToList() {
            if (currentDetailIdx >= 0) {
                var cur = document.getElementById('conv-detail-' + currentDetailIdx);
                if (cur) cur.style.display = 'none';
                currentDetailIdx = -1;
            }
            document.getElementById('detail-view').style.display = 'none';
            document.getElementById('list-view').style.display = 'block';
            window.scrollTo(0, savedScrollY);
        }

        window.addEventListener('popstate', function() {
            if (currentDetailIdx >= 0) {
                backToList();
            }
        });

        function filterConvs(keyword) {
            var q = (keyword || '').toLowerCase().trim();
            var cards = document.querySelectorAll('.conv-card');
            for (var i = 0; i < cards.length; i++) {
                var title = cards[i].getAttribute('data-title') || '';
                cards[i].style.display = (!q || title.indexOf(q) !== -1) ? 'flex' : 'none';
            }
        }
    """.trimIndent()

    private fun createStyles(): String = """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: #F5F7FA;
            color: #1A1A1A;
            max-width: 820px;
            margin: 0 auto;
            padding: 16px;
        }
        .header {
            text-align: center;
            padding: 24px 16px;
            background: linear-gradient(135deg, #E3F2FD 0%, #BBDEFB 100%);
            border-radius: 14px;
            margin-bottom: 18px;
        }
        .header h1 { font-size: 22px; margin-bottom: 6px; color: #1A1A1A; }
        .header p { font-size: 13px; color: #555; margin-bottom: 14px; }
        .search-box input {
            width: 100%;
            max-width: 480px;
            padding: 10px 14px;
            border: 1px solid #90CAF9;
            border-radius: 10px;
            font-size: 14px;
            outline: none;
            background: #fff;
        }
        .conv-list {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .conv-card {
            background: #fff;
            border-radius: 12px;
            padding: 14px 16px;
            display: flex;
            align-items: center;
            gap: 14px;
            cursor: pointer;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
            transition: transform 0.12s ease, box-shadow 0.12s ease;
        }
        .conv-card:hover {
            transform: translateY(-1px);
            box-shadow: 0 4px 10px rgba(0,0,0,0.1);
        }
        .conv-avatar {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 18px;
            flex-shrink: 0;
        }
        .conv-info {
            flex: 1;
            min-width: 0;
        }
        .conv-title-row {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 4px;
        }
        .conv-title {
            font-size: 16px;
            font-weight: 600;
            color: #1A1A1A;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .conv-meta {
            font-size: 12px;
            color: #888;
        }
        .conv-right {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
        }
        .msg-count {
            font-size: 12px;
            background: #EEF2F6;
            color: #4A5568;
            padding: 3px 9px;
            border-radius: 12px;
            font-weight: 600;
        }
        .arrow {
            font-size: 20px;
            color: #999;
            font-weight: bold;
        }
        .platform-badge {
            font-size: 11px;
            background: #E3F2FD;
            padding: 2px 8px;
            border-radius: 8px;
            color: #1976D2;
            font-weight: 600;
            flex-shrink: 0;
        }
        .conversation-detail {
            background: #fff;
            border-radius: 14px;
            overflow: hidden;
            box-shadow: 0 2px 6px rgba(0,0,0,0.08);
        }
        .detail-Sticky-header {
            position: sticky;
            top: 0;
            z-index: 10;
            padding: 12px 16px;
            background: #E3F2FD;
            border-bottom: 1px solid #BBDEFB;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 10px;
        }
        .back-btn {
            background: #1976D2;
            color: #fff;
            border: none;
            padding: 7px 14px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
        }
        .back-btn:hover { background: #1565C0; }
        .detail-title-wrap {
            display: flex;
            align-items: center;
            gap: 8px;
            flex: 1;
            min-width: 0;
        }
        .detail-title {
            font-weight: 700;
            font-size: 16px;
            color: #1A1A1A;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .detail-count {
            font-size: 12px;
            color: #666;
            flex-shrink: 0;
        }
        .messages { padding: 12px 16px; }
        .message { margin: 12px 0; }
        .message-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 4px;
        }
        .sender-name { font-size: 13px; font-weight: 600; }
        .message-time { font-size: 11px; color: #999; }
        .message-bubble {
            background: #F0F7FF;
            border: 1px solid #D6E8FF;
            padding: 10px 14px;
            border-radius: 4px 12px 12px 12px;
            font-size: 15px;
            line-height: 1.55;
            word-wrap: break-word;
        }
        .message.recalled .recalled-badge {
            display: block;
            background: #FFF0F0;
            border: 1px solid #FFCDD2;
            color: #D32F2F;
            font-size: 14px;
            padding: 8px 14px;
            border-radius: 8px;
            line-height: 1.5;
            word-wrap: break-word;
        }
        .footer {
            text-align: center;
            padding: 20px;
            color: #999;
            font-size: 12px;
        }
    """.trimIndent()

    private fun getSenderColor(name: String): String {
        val colors = listOf("#42A5F5", "#66BB6A", "#EF5350", "#AB47BC", "#FF7043", "#26C6DA", "#EC407A", "#8D6E63")
        val index = kotlin.math.abs(name.hashCode()) % colors.size
        return colors[index]
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
