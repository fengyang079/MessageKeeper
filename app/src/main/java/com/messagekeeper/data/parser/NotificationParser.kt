package com.messagekeeper.data.parser

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.os.Build
import android.util.Log
import com.messagekeeper.data.prefs.MonitoredAppsManager

/**
 * 通知解析结果
 */
data class ParsedNotification(
    /** 平台标识 */
    val platform: String,
    /** 对话名称（个人=联系人名，群聊=群名） */
    val conversationName: String,
    /** 发送者名称（群聊中是具体的群成员） */
    val senderName: String,
    /** 消息内容 */
    val content: String,
    /** 消息时间戳（毫秒） */
    val timestamp: Long,
    /** 通知唯一 ID（用于去重） */
    val notificationId: String,
    /** 是否是撤回通知 */
    val isRecall: Boolean,
)

/**
 * 通知解析器
 * 动态支持用户选择的任意应用通知解析
 */
object NotificationParser {

    private const val TAG = "NotificationParser"

    // 视频平台标识列表（用于 # 话题检测合并）
    private val VIDEO_PLATFORMS = setOf("douyin", "kuaishou")

    // 撤回关键词（中文/英文）
    private val RECALL_KEYWORDS = listOf(
        "撤回了一条消息",
        "recalled a message",
        "撤回一条消息",
        "撤回了消息",
        "撤回了一则消息",
        "对方撤回了一条消息",
        "尝试撤回一条消息"
    )

    // 微信/QQ 自带的未读消息数量后缀，如 "[2条]", "(5条新消息)", "（5条新消息）"
    private val unreadRegex = Regex("[\\(\\[（]\\s*\\d+\\s*(?:条(?:新消息|消息|未读)?|messages?)\\s*[\\)\\]）]", RegexOption.IGNORE_CASE)

    // 内置应用的包名到平台标识的映射（保持向后兼容）
    private val BUILTIN_PACKAGE_MAP = mapOf(
        "com.tencent.mm" to "wechat",
        "com.tencent.mobileqq" to "qq",
        "com.ss.android.ugc.aweme" to "douyin",
        "com.ss.android.ugc.aweme.lite" to "douyin",
        "com.smile.gifmaker" to "kuaishou",
        "com.kuaishou.nebula" to "kuaishou",
        "my.maya.android" to "duoshan",
        "com.tencent.qqmusic" to "qqmusic",
    )

    /**
     * 解析通知（需要 Context 以支持动态应用配置）
     */
    fun parse(context: Context, sbn: StatusBarNotification): ParsedNotification? {
        val notification = sbn.notification ?: return null
        return parse(context, sbn.packageName, notification, sbn.postTime, sbn.key)
    }

    /**
     * 兼容旧接口（无 Context 时使用内置映射）
     */
    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val notification = sbn.notification ?: return null
        return parse(null, sbn.packageName, notification, sbn.postTime, sbn.key)
    }

    /**
     * 核心解析逻辑
     */
    fun parse(
        context: Context?,
        packageName: String,
        notification: Notification,
        postTime: Long,
        uniqueKey: String
    ): ParsedNotification? {
        // 动态获取平台标识
        val platform = if (context != null) {
            MonitoredAppsManager.getPlatformId(context, packageName)
        } else {
            BUILTIN_PACKAGE_MAP[packageName] ?: return null
        }

        // 跳过 Android 的群组摘要通知（不含实际消息内容）
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.d(TAG, "跳过群组摘要通知: $uniqueKey")
            return null
        }

        val extras = notification.extras ?: return null

        // 使用 getCharSequence 而非 getString，确保 SpannableString 等类型也能正确读取
        var title = extras.getCharSequence("android.title")?.toString()?.trim()
        var text = extras.getCharSequence("android.text")?.toString()?.trim()

        // 如果 android.text 为空，尝试 android.bigText（展开通知时的完整文本）
        if (text.isNullOrBlank()) {
            text = extras.getCharSequence("android.bigText")?.toString()?.trim()
        }

        // 如果还是空的，尝试 android.textLines（InboxStyle 收件箱样式通知，取最后一行）
        if (text.isNullOrBlank()) {
            val textLines = extras.getCharSequenceArray("android.textLines")
            if (textLines != null && textLines.isNotEmpty()) {
                text = textLines.last()?.toString()?.trim()
            }
        }

        // 如果 android.title 为空，尝试从 android.title.big 获取
        if (title.isNullOrBlank()) {
            title = extras.getCharSequence("android.title.big")?.toString()?.trim()
        }

        // 记录原始的完整文本，用于计算哈希
        val rawTitle = title ?: ""
        val rawText = text ?: ""
        val rawFullText = "$rawTitle|$rawText"
        val rawHash = rawFullText.hashCode()

        // 如果 title 和 text 都获取不到，无法解析
        if (title.isNullOrBlank() || text.isNullOrBlank()) {
            Log.w(TAG, "通知缺少标题或内容: title=$title, text=$text, pkg=$packageName, key=$uniqueKey")
            return null
        }
        
        // 移除微信/QQ自带的未读消息数量，如 "[2条]", "(5条新消息)", "（5条新消息）"
        title = title.replace(unreadRegex, "").trim()
        text = text.replace(unreadRegex, "").trim()

        val timestamp = if (postTime > 0) postTime else System.currentTimeMillis()

        // 检查是否为撤回通知
        val isRecall = RECALL_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true) || title.contains(keyword, ignoreCase = true)
        }

        // 生成唯一通知 ID (附加 rawHash，用于精准区分“相同内容连发”和“同一条通知重绘”)
        val notificationId = "${platform}_${uniqueKey}_${rawHash}"

        // 解析发送者和内容
        val (senderName, content) = extractSenderAndContent(platform, title, text, isRecall)

        // 对话名称逻辑：
        // 1. QQ音乐 -> 统一为 "QQ音乐"
        // 2. 视频平台 + 内容含 # -> 合并为 "xx视频动态"
        // 3. 撤回 -> 用通知标题（私聊=联系人名，群聊=群名，与原消息的对话归属一致；
        //    若用发送者名，群聊撤回会因对不上群名而无法匹配到原消息）
        // 4. 其他 -> 用通知标题
        val conversationName = when {
            platform == "qqmusic" -> "QQ音乐"
            isVideoTopicMessage(platform, content) -> getVideoTopicConversationName(context, platform)
            isRecall -> title
            else -> title
        }

        return ParsedNotification(
            platform = platform,
            conversationName = conversationName,
            senderName = senderName,
            content = content,
            timestamp = timestamp,
            notificationId = notificationId,
            isRecall = isRecall,
        )
    }

    /**
     * 从消息正文拆出 "发送者: 内容" 前缀（群聊通知格式，如 "张三: 内容"、"[1条]张三：内容"）
     * 未读前缀（"[1条]" / "(2条新消息)" 等）先剥除；第一个中/英文冒号作为分隔符
     * 返回 (发送者, 剩余内容)，不是群消息格式时返回 null
     */
    fun splitInTextSender(content: String): Pair<String, String>? {
        if (content.isBlank()) return null
        val cleaned = content.replace(unreadRegex, "").trim()
        val colonIndex = cleaned.indexOfAny(charArrayOf(':', '：'))
        if (colonIndex <= 0) return null
        val prefix = cleaned.substring(0, colonIndex).trim()
        val rest = cleaned.substring(colonIndex + 1).trim()
        if (prefix.isEmpty() || rest.isEmpty()) return null
        if (prefix.length > 30) return null          // 昵称不会太长，防止把整句话误当发送者
        if (prefix.contains('\n')) return null
        if (prefix.startsWith("http", ignoreCase = true)) return null // 链接误判
        // 形如 "12:30 开会" 的短数字前缀是时间，不是昵称（长纯数字 QQ 昵称不受影响）
        if (prefix.length <= 5 && prefix.all { it.isDigit() }) return null
        return prefix to rest
    }

    /**
     * 检测是否是视频平台的 # 话题消息
     */
    private fun isVideoTopicMessage(platform: String, content: String): Boolean {
        return VIDEO_PLATFORMS.contains(platform) && content.contains("#")
    }

    /**
     * 获取视频话题消息的统一对话名称
     */
    private fun getVideoTopicConversationName(context: Context?, platform: String): String {
        val displayName = if (context != null) {
            MonitoredAppsManager.getDisplayName(context, platform)
        } else {
            when (platform) {
                "douyin" -> "抖音"
                "kuaishou" -> "快手"
                else -> platform
            }
        }
        return "${displayName}视频动态"
    }

    /**
     * 从通知中提取发送者和消息内容
     */
    private fun extractSenderAndContent(
        platform: String,
        title: String,
        text: String,
        isRecall: Boolean
    ): Pair<String, String> {
        if (isRecall) {
            var extractedSender = ""
            for (keyword in RECALL_KEYWORDS) {
                val index = text.indexOf(keyword)
                if (index >= 0) {
                    val rawPrefix = text.substring(0, index).trim()
                    val cleanPrefix = rawPrefix
                        .replace(unreadRegex, "")
                        .trimEnd(':', '：')
                        .trim()
                    if (cleanPrefix.isNotBlank()) {
                        extractedSender = cleanPrefix
                    }
                    break
                }
            }
            // 如果未能从正文前缀提取到发送者（例如正文仅为“撤回了一条消息”），则标题即为联系人名（私聊场景）
            val finalSender = if (extractedSender.isNotBlank()) {
                extractedSender
            } else {
                title.replace(unreadRegex, "").trim()
            }
            return finalSender to "【已撤回】$text"
        }

        // QQ音乐：合并标题和内容
        if (platform == "qqmusic") {
            val contentStr = if (title == "QQ音乐" || title.isBlank()) text else if (text.isNotBlank() && text != title) "$title - $text" else title
            return "QQ音乐" to contentStr
        }

        // 发送者与内容的最终形态（群聊拆 "张三: 内容" 前缀）由仓库层按会话类型
        // （自动/群聊/私信）决定，见 MessageRepository.saveIncomingMessage
        return title to text
    }

    /**
     * 判断是否为支持/监听的平台（动态版本）
     */
    fun isSupported(context: Context, packageName: String): Boolean {
        return MonitoredAppsManager.isMonitored(context, packageName)
    }

    /**
     * 兼容旧接口（无 Context 时使用内置映射）
     */
    fun isSupported(packageName: String): Boolean {
        return BUILTIN_PACKAGE_MAP.containsKey(packageName)
    }

    /**
     * 核心解析逻辑（兼容旧接口，无 Context）
     */
    fun parse(
        packageName: String,
        notification: Notification,
        postTime: Long,
        uniqueKey: String
    ): ParsedNotification? {
        return parse(null, packageName, notification, postTime, uniqueKey)
    }
}
