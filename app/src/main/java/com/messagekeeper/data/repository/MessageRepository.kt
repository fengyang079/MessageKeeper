package com.messagekeeper.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.messagekeeper.MessageKeeperApp
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.DeletedMessage
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.db.entity.Platform
import com.messagekeeper.data.db.entity.SpecialAlert
import com.messagekeeper.data.parser.NotificationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 数据仓库 - 统一管理数据访问
 */
class MessageRepository {

    companion object {
        private const val TAG = "MessageRepository"

        /**
         * 跨设备合并去重窗口。
         * 两台手机同时在线时，同一条消息在各自设备上的落库时间戳会有几百毫秒偏差，
         * 因此除"时间戳完全相同"外，还允许在 ±该窗口内按（会话+发送者+内容）做一对一配对。
         */
        private const val MERGE_DEDUPE_WINDOW_MS = 2_000L

        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageRepository().also { INSTANCE = it }
            }
        }
    }

    private val database = MessageKeeperApp.instance.database
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val specialAlertDao = database.specialAlertDao()
    private val deletedMessageDao = database.deletedMessageDao()

    // ==================== 对话操作 ====================

    /** 获取所有对话（Flow） */
    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    /** 获取单个对话 */
    suspend fun getConversationById(id: Long): Conversation? = conversationDao.getConversationById(id)

    /** 重置未读计数 */
    suspend fun resetUnreadCount(conversationId: Long) = conversationDao.resetUnreadCount(conversationId)

    /** 删除对话及其所有消息（消息先归档到历史删除回收站，可恢复） */
    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val conv = conversationDao.getConversationById(id)
            if (conv != null) {
                val msgs = messageDao.getMessagesByConversationSync(id)
                if (msgs.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val deletedList = msgs.map { m ->
                        DeletedMessage(
                            conversation_key = conv.conversation_key,
                            conversation_name = conv.display_name,
                            chat_type = conv.chat_type,
                            is_system = conv.is_system,
                            sender_name = m.sender_name,
                            content = m.content,
                            timestamp = m.timestamp,
                            platform = m.platform,
                            notification_id = m.notification_id,
                            is_recalled = m.is_recalled,
                            deleted_at = now
                        )
                    }
                    deletedMessageDao.insertAll(deletedList)
                }
            }
            conversationDao.deleteById(id)
        }
    }

    /** 清除所有数据（消息先归档到历史删除回收站，可恢复） */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.withTransaction {
            val convs = conversationDao.getAllConversationsSyncList().associateBy { it.id }
            val msgs = messageDao.getAllMessagesGroupedByConversation()
            if (msgs.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val deletedList = msgs.map { m ->
                    val conv = convs[m.conversation_id]
                    val name = conv?.display_name ?: m.sender_name.ifBlank { "未知联系人" }
                    val key = conv?.conversation_key ?: "${m.platform}_$name"
                    DeletedMessage(
                        conversation_key = key,
                        conversation_name = name,
                        chat_type = conv?.chat_type ?: Conversation.CHAT_TYPE_AUTO,
                        is_system = conv?.is_system ?: false,
                        sender_name = m.sender_name,
                        content = m.content,
                        timestamp = m.timestamp,
                        platform = m.platform,
                        notification_id = m.notification_id,
                        is_recalled = m.is_recalled,
                        deleted_at = now
                    )
                }
                deletedMessageDao.insertAll(deletedList)
            }
            messageDao.deleteAll()
            conversationDao.deleteAll()
        }
    }

    // ==================== 消息操作 ====================

    /** 获取某个对话的所有消息 */
        fun getMessagesPagingData(conversationId: Long): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<com.messagekeeper.data.db.entity.Message>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 100
            ),
            pagingSourceFactory = { messageDao.getMessagesPagingSource(conversationId) }
        ).flow
    }

    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesByConversation(conversationId)

    /** 获取某个对话的所有消息（同步，用于导出） */
    suspend fun getMessagesByConversationSync(conversationId: Long): List<Message> =
        messageDao.getMessagesByConversationSync(conversationId)

    /** 一次性修复已有数据的 sender_name 为会话名称 */
    suspend fun fixSenderNamesToMatchConversation() = messageDao.fixSenderNamesToMatchConversation()

    /** 获取所有消息按对话分组（用于导出全部） */
    suspend fun getAllMessagesGroupedByConversation(): List<Message> =
        messageDao.getAllMessagesGroupedByConversation()

    /** 获取消息总数（用于 Dashboard 增量轮询判断） */
    suspend fun getMessageCount(): Int = messageDao.getMessageCount()

    /** 获取指定 ID 之后的新消息（用于 Dashboard 增量加载） */
    suspend fun getMessagesSince(afterId: Long): List<Message> =
        messageDao.getMessagesSince(afterId)
    /** 搜索消息 */
    suspend fun searchMessages(query: String): List<Message> {
        if (query.isBlank()) return emptyList()
        Log.d(TAG, "搜索消息: $query")
        return messageDao.searchMessages(query)
    }

    /** 删除单条消息（先归档到历史删除回收站，可恢复） */
    suspend fun deleteMessage(message: Message) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val conv = conversationDao.getConversationById(message.conversation_id)
            val convName = conv?.display_name ?: message.sender_name.ifBlank { "未知联系人" }
            val convKey = conv?.conversation_key ?: "${message.platform}_$convName"
            deletedMessageDao.insert(
                DeletedMessage(
                    conversation_key = convKey,
                    conversation_name = convName,
                    chat_type = conv?.chat_type ?: Conversation.CHAT_TYPE_AUTO,
                    is_system = conv?.is_system ?: false,
                    sender_name = message.sender_name,
                    content = message.content,
                    timestamp = message.timestamp,
                    platform = message.platform,
                    notification_id = message.notification_id,
                    is_recalled = message.is_recalled,
                    deleted_at = System.currentTimeMillis()
                )
            )
            messageDao.delete(message)
        }
    }

    // ==================== 历史删除（回收站）操作 ====================

    /** 获取所有已删除消息（Flow） */
    fun getAllDeletedMessages(): Flow<List<DeletedMessage>> = deletedMessageDao.getAllDeletedMessages()

    /** 从回收站恢复单条消息到原对话（若原对话已删除则自动重建） */
    suspend fun restoreDeletedMessage(deleted: DeletedMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                val restoredConvId = restoreMessageToConversationLocked(deleted)
                if (restoredConvId != 0L) {
                    deletedMessageDao.deleteById(deleted.id)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "恢复已删除消息失败: id=${deleted.id}", e)
            false
        }
    }

    /** 批量恢复回收站中的消息（例如按会话或全部恢复） */
    suspend fun restoreDeletedMessages(items: List<DeletedMessage>): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        var count = 0
        try {
            database.withTransaction {
                for (deleted in items) {
                    val restoredConvId = restoreMessageToConversationLocked(deleted)
                    if (restoredConvId != 0L) {
                        deletedMessageDao.deleteById(deleted.id)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "批量恢复已删除消息失败", e)
        }
        count
    }

    private suspend fun restoreMessageToConversationLocked(deleted: DeletedMessage): Long {
        val key = deleted.conversation_key.ifBlank { "${deleted.platform}_${deleted.conversation_name}" }
        val existing = conversationDao.getConversationByKey(key)
        val shouldPin = com.messagekeeper.data.prefs.PinnedConversationsManager.isPinned(key)
        val convId: Long
        if (existing == null) {
            var newId = conversationDao.insert(
                Conversation(
                    platform = deleted.platform,
                    conversation_key = key,
                    display_name = deleted.conversation_name.ifBlank { key.substringAfter('_') },
                    last_message = deleted.content,
                    last_message_time = deleted.timestamp,
                    unread_count = 0,
                    is_pinned = shouldPin,
                    is_system = deleted.is_system,
                    chat_type = deleted.chat_type
                )
            )
            if (newId == -1L) {
                newId = conversationDao.getConversationByKey(key)?.id ?: 0L
            }
            convId = newId
        } else {
            convId = existing.id
            if (deleted.timestamp >= existing.last_message_time) {
                conversationDao.updateLastMessage(existing.id, deleted.content, deleted.timestamp)
            }
            if (shouldPin && !existing.is_pinned) {
                conversationDao.updatePinnedStatus(existing.id, true)
            }
        }
        if (convId == 0L) return 0L
        messageDao.insert(
            Message(
                conversation_id = convId,
                sender_name = deleted.sender_name,
                content = deleted.content,
                timestamp = deleted.timestamp,
                platform = deleted.platform,
                notification_id = deleted.notification_id,
                is_recalled = deleted.is_recalled
            )
        )
        return convId
    }

    /** 彻底删除回收站中的单条消息 */
    suspend fun permanentlyDeleteMessage(id: Long) = withContext(Dispatchers.IO) {
        deletedMessageDao.deleteById(id)
    }

    /** 清空回收站中的所有已删除消息 */
    suspend fun clearAllDeletedMessages() = withContext(Dispatchers.IO) {
        deletedMessageDao.deleteAll()
    }

    /** 标记消息为已撤回 */
    suspend fun markMessageAsRecalled(messageId: Long) = messageDao.markAsRecalled(messageId)

    // ==================== 消息存储（供通知监听服务调用） ====================

    /**
     * 保存收到的消息
     * 自动创建/更新对话，去重
     * @return SavedMessage(isNew, senderName)：isNew 用于触发提醒等后续动作；
     *         senderName 是最终落库的发送者（群聊会话为从正文拆出的成员名），供特别关心匹配
     */
    suspend fun saveIncomingMessage(
        platform: String,
        conversationName: String,
        senderName: String,
        content: String,
        timestamp: Long,
        notificationId: String
    ): SavedMessage {
        Log.d(TAG, "saveIncomingMessage: conv=$conversationName, sender=$senderName, content=${content.take(50)}")

        // 1. 获取或创建归属对话（精准隔离不同联系人的对话）
        val conversationKey = "${platform}_${conversationName}"
        val existing = conversationDao.getConversationByKey(conversationKey)
        val displayName = existing?.display_name ?: conversationName
        val chatType = existing?.chat_type ?: Conversation.CHAT_TYPE_AUTO

        // 2. 按会话类型决定发送者与内容的最终形态
        //    - 群聊（手动指定）：正文以 "张三: 内容" 开头则拆出成员名
        //    - 私信（手动指定）：发送者恒为联系人名，正文不拆，不受正文冒号干扰
        //    - 自动：正文有前缀且前缀 ≠ 联系人名时拆出（群通知格式），否则保持原样
        //    fallbackSender 兼容导入/迁移数据：备份里已带成员名时直接沿用
        val fallbackSender = senderName.takeIf { it.isNotBlank() && it != conversationName } ?: displayName
        val split = NotificationParser.splitInTextSender(content)
        val finalSender: String
        val finalContent: String
        when (chatType) {
            Conversation.CHAT_TYPE_GROUP -> {
                finalSender = split?.first ?: fallbackSender
                finalContent = split?.second ?: content
            }
            Conversation.CHAT_TYPE_PRIVATE -> {
                finalSender = displayName
                finalContent = content
            }
            else -> {
                if (split != null && split.first != displayName) {
                    finalSender = split.first
                    finalContent = split.second
                } else {
                    finalSender = fallbackSender
                    finalContent = content
                }
            }
        }

        // 3. 完全重复（通知 ID + 内容 + 时间戳全同）= 服务重绑后 syncActiveNotifications
        //    重新扫描通知栏产生的重复广播，跳过。
        //    微信/QQ 连续消息是原地更新同一条通知：key 不变，但 postTime 会刷新为最新消息的时间——
        //    所以 key+content 相同而 timestamp 不同 = 通知携带了新消息，必须继续保存
        if (messageDao.countExactDuplicate(notificationId, finalContent, timestamp) > 0) {
            Log.d(TAG, "同通知同内容同时间的重复广播，跳过: $notificationId")
            return SavedMessage(false, "", "")
        }

        var conversation = existing
        val shouldPin = com.messagekeeper.data.prefs.PinnedConversationsManager.isPinned(conversationKey)
        if (conversation == null) {
            val newConv = Conversation(
                platform = platform,
                conversation_key = conversationKey,
                display_name = conversationName,
                last_message = finalContent,
                last_message_time = timestamp,
                unread_count = 0,
                is_pinned = shouldPin,
                is_system = (platform == "qqmusic" || conversationName.endsWith("视频动态"))
            )
            var newId = conversationDao.insert(newConv)
            if (newId == -1L) {
                newId = conversationDao.getConversationByKey(conversationKey)?.id ?: 0L
            }
            conversation = newConv.copy(id = newId)
        } else {
            conversationDao.updateLastMessage(conversation.id, finalContent, timestamp)
            if (shouldPin && !conversation.is_pinned) {
                conversationDao.updatePinnedStatus(conversation.id, true)
            }
            val shouldBeSystem = (platform == "qqmusic" || conversationName.endsWith("视频动态"))
            if (shouldBeSystem && !conversation.is_system) {
                conversationDao.updateSystemStatus(conversation.id, true)
            }
        }

        // 4. 兜底：仅在该对话内部校验 3 秒内的重复广播消息（个别应用重发通知时 key 会变）
        val timeWindow = if (platform == "qqmusic") 180_000L else 3_000L
        val recentMessages = messageDao.getRecentMessagesInConversation(conversation.id, finalSender, finalContent, timestamp - timeWindow)
        if (recentMessages.isNotEmpty()) {
            val currentRawHash = notificationId.substringAfterLast("_")
            val isDuplicateBroadcast = recentMessages.any { it.notification_id.endsWith("_$currentRawHash") }
            
            if (isDuplicateBroadcast) {
                Log.d(TAG, "同对话内部包含重复广播消息（相同的 rawHash=$currentRawHash），跳过插入")
                return SavedMessage(false, "", "")
            } else {
                Log.d(TAG, "同内容新消息：未读数量前缀等导致 rawHash 不同，放行入库！")
            }
        }

        // 5. 插入消息到归属对话
        messageDao.insert(
            Message(
                conversation_id = conversation.id,
                sender_name = finalSender,
                content = finalContent,
                timestamp = timestamp,
                platform = platform,
                notification_id = notificationId
            )
        )
        return SavedMessage(true, finalSender, finalContent)
    }

    /** 保存结果：isNew 用于触发提醒，senderName/content 是最终落库的形态 */
    data class SavedMessage(val isNew: Boolean, val senderName: String, val content: String)

    /** 设置会话类型（0=自动识别 1=群聊 2=私信），长按会话操作 */
    suspend fun setChatType(id: Long, chatType: Int) = conversationDao.updateChatType(id, chatType)

    /**
     * 检查是否有特别提醒规则匹配，返回匹配的 SpecialAlert（含声音设置）
     * @param senderName 真实发送者（私聊=联系人名，群聊=群成员名，由解析层从 "张三: 内容" 拆出）
     * @param conversationTitle 通知标题（群聊=群名），用于匹配"按群名"的规则
     * 好友名规则优先于群名规则
     */
    suspend fun findMatchingAlert(platform: String, senderName: String, conversationTitle: String? = null): SpecialAlert? {
        val alerts = specialAlertDao.getEnabledAlerts()
        alerts.firstOrNull { it.platform == platform && it.sender_name == senderName }?.let { return it }
        if (conversationTitle != null && conversationTitle != senderName) {
            alerts.firstOrNull { it.platform == platform && it.sender_name == conversationTitle }?.let { return it }
        }
        return null
    }

    /**
     * 处理撤回通知
     * 找到对应对话中该发送者在撤回时刻之前最近的一条未撤回消息，标记为已撤回
     * （不能简单取"最后一条"：对方连发多条后撤回较早的那条时，最后一条会被错误标记）
     */
    suspend fun handleRecallNotification(
        platform: String,
        conversationName: String,
        senderName: String,
        timestamp: Long,
        notificationId: String
    ) {
        val cleanConvName = conversationName.trim()
        val cleanSender = senderName.trimEnd(':' , '：').trim()
        val conversationKey = "${platform}_${cleanConvName}"
        var conversation = conversationDao.getConversationByKey(conversationKey)

        // 兜底 1: 若精确 key 未找到，尝试模糊匹配 display_name
        if (conversation == null) {
            val allConvs = conversationDao.getAllConversationsSyncList()
            conversation = allConvs.firstOrNull {
                it.platform == platform && (it.display_name == cleanConvName || cleanConvName.contains(it.display_name) || it.display_name.contains(cleanConvName))
            }
        }

        // 兜底 2: 若依然不存在对话，则先自动创建该对话，防止丢弃撤回事件
        if (conversation == null) {
            val newConv = Conversation(
                platform = platform,
                conversation_key = conversationKey,
                display_name = cleanConvName,
                last_message = "【对方撤回了一条消息】",
                last_message_time = timestamp,
                unread_count = 1
            )
            var newId = conversationDao.insert(newConv)
            if (newId == -1L) {
                newId = conversationDao.getConversationByKey(conversationKey)?.id ?: 0L
            }
            conversation = newConv.copy(id = newId)
        }

        if (conversation != null) {
            val messages = messageDao.getMessagesByConversationSync(conversation.id)
            // 匹配目标：
            // 1. 发送者相同且未撤回且时间在撤回前
            // 2. 发送者包含/模糊匹配
            // 3. 私聊场景下最后一条未撤回消息
            val target = messages.lastOrNull {
                it.sender_name == cleanSender && !it.is_recalled && it.timestamp <= timestamp
            } ?: messages.lastOrNull {
                !it.is_recalled && (it.sender_name == cleanSender || cleanSender.contains(it.sender_name) || it.sender_name.contains(cleanSender))
            } ?: messages.lastOrNull {
                !it.is_recalled && (it.sender_name == cleanConvName || cleanConvName.contains(it.sender_name))
            } ?: if (messages.isNotEmpty()) messages.lastOrNull { !it.is_recalled } else null

            if (target != null) {
                messageDao.markAsRecalled(target.id)
                conversationDao.updateLastMessage(
                    conversation.id,
                    "【已撤回】${target.content}",
                    timestamp
                )
                Log.d(TAG, "已成功标记并保留撤回消息: id=${target.id}, sender=${target.sender_name}, content=${target.content.take(30)}")
            } else {
                // 如果原消息未被记录（例如收信时微信在手机前台未弹通知），插入一条撤回占位消息，明确通知用户有人撤回！
                val finalSenderName = if (cleanSender.isNotBlank()) cleanSender else cleanConvName
                val recallPlaceholder = Message(
                    conversation_id = conversation.id,
                    sender_name = finalSenderName,
                    content = "【对方撤回了一条消息】（原消息在微信前台或监听未截获）",
                    timestamp = timestamp,
                    platform = platform,
                    notification_id = notificationId,
                    is_recalled = true
                )
                messageDao.insert(recallPlaceholder)
                conversationDao.updateLastMessage(
                    conversation.id,
                    "【对方撤回了一条消息】",
                    timestamp
                )
                Log.w(TAG, "原消息未捕获，已插入撤回占位消息: conv=$cleanConvName, sender=$finalSenderName")
            }
        }
    }
    // ==================== 特别提醒操作 ====================

    /** 切换对话置顶状态（同时写入 PinnedConversationsManager 双重持久化） */
    suspend fun togglePinConversation(id: Long, isPinned: Boolean) {
        conversationDao.updatePinnedStatus(id, isPinned)
        conversationDao.getConversationById(id)?.let { conv ->
            com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned(conv.conversation_key, isPinned)
        }
    }

    /** 切换对话软件消息状态 */
    suspend fun toggleSystemConversation(id: Long, isSystem: Boolean) =
        conversationDao.updateSystemStatus(id, isSystem)

    /** 获取所有特别提醒规则 */
    fun getAllSpecialAlerts(): Flow<List<SpecialAlert>> = specialAlertDao.getAllAlerts()

    /** 添加特别提醒规则，默认音效为“甘露” (ganlu) */
    suspend fun addSpecialAlert(platform: String, senderName: String, soundName: String = "ganlu"): Long {
        val existing = specialAlertDao.getAlert(platform, senderName)
        if (existing != null) {
            if (!existing.is_enabled) {
                specialAlertDao.setEnabled(existing.id, true)
            }
            return existing.id
        }
        return specialAlertDao.insert(
            SpecialAlert(
                platform = platform,
                sender_name = senderName,
                sound_name = soundName,
                created_at = System.currentTimeMillis()
            )
        )
    }

    /** 切换特别提醒启用状态 */
    suspend fun toggleSpecialAlert(id: Long, enabled: Boolean) =
        specialAlertDao.setEnabled(id, enabled)

    /** 更新提醒声音 */
    suspend fun updateAlertSound(id: Long, soundName: String) =
        specialAlertDao.updateSound(id, soundName)

    /** 删除特别提醒规则 */
    suspend fun deleteSpecialAlert(id: Long) = specialAlertDao.deleteById(id)

    // ==================== 导出相关 ====================

    /** 获取所有对话（同步，用于导出） */
    suspend fun getAllConversationsSync(): List<Conversation> {
        return conversationDao.getAllConversationsSyncList()
    }

    /**
     * 自动合并带有(X条新消息)等后缀而分散的对话，并统一合并 QQ音乐 对话，同时同步置顶状态
     */
    suspend fun mergeDuplicateConversations() {
        val conversations = conversationDao.getAllConversationsSyncList()
        val unreadRegex = Regex("[\\(\\[（]\\s*\\d+\\s*(?:条(?:新消息|消息|未读)?|messages?)\\s*[\\)\\]）]", RegexOption.IGNORE_CASE)

        // 0. 双向同步置顶状态（数据库 <-> PinnedConversationsManager），确保应用更新/恢复后置顶不丢
        val pinnedKeysInDb = conversations.filter { it.is_pinned }.map { it.conversation_key }
        if (pinnedKeysInDb.isNotEmpty()) {
            com.messagekeeper.data.prefs.PinnedConversationsManager.addPinnedKeys(pinnedKeysInDb)
        }
        val allPinnedKeys = com.messagekeeper.data.prefs.PinnedConversationsManager.getPinnedKeys()
        for (conv in conversations) {
            if (!conv.is_pinned && conv.conversation_key in allPinnedKeys) {
                conversationDao.updatePinnedStatus(conv.id, true)
            }
        }
        
        // 1. 统一合并 QQ 音乐对话
        val qqMusicConvs = conversations.filter { it.platform == "qqmusic" || it.conversation_key.startsWith("qqmusic_") }
        if (qqMusicConvs.size > 1) {
            val mainQqMusic = qqMusicConvs.find { it.conversation_key == "qqmusic_QQ音乐" } ?: qqMusicConvs.first()
            val anyPinned = qqMusicConvs.any { it.is_pinned || com.messagekeeper.data.prefs.PinnedConversationsManager.isPinned(it.conversation_key) }
            if (mainQqMusic.conversation_key != "qqmusic_QQ音乐" || !mainQqMusic.is_system) {
                conversationDao.updateConversationNameAndKey(mainQqMusic.id, "QQ音乐", "qqmusic_QQ音乐")
                conversationDao.updateSystemStatus(mainQqMusic.id, true)
            }
            if (anyPinned) {
                conversationDao.updatePinnedStatus(mainQqMusic.id, true)
                com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned("qqmusic_QQ音乐", true)
            }
            for (conv in qqMusicConvs) {
                if (conv.id != mainQqMusic.id) {
                    messageDao.updateConversationId(conv.id, mainQqMusic.id)
                    if (conv.last_message_time > mainQqMusic.last_message_time) {
                        conversationDao.updateLastMessage(mainQqMusic.id, conv.last_message, conv.last_message_time)
                    }
                    conversationDao.deleteById(conv.id)
                }
            }
        }

        // 2. 清理合并自带未读后缀的重复对话
        val updatedConversations = conversationDao.getAllConversationsSyncList()
        for (conv in updatedConversations) {
            val originalName = conv.display_name
            val cleanName = originalName.replace(unreadRegex, "").trim()
            
            if (cleanName != originalName && cleanName.isNotEmpty()) {
                val cleanKey = "${conv.platform}_$cleanName"
                val wasPinned = conv.is_pinned || com.messagekeeper.data.prefs.PinnedConversationsManager.isPinned(conv.conversation_key)
                if (wasPinned) {
                    com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned(conv.conversation_key, false)
                    com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned(cleanKey, true)
                }
                
                val targetConv = conversationDao.getConversationByKey(cleanKey)
                if (targetConv != null && targetConv.id != conv.id) {
                    Log.d(TAG, "合并对话: $originalName -> $cleanName")
                    messageDao.updateConversationId(conv.id, targetConv.id)
                    if (conv.last_message_time > targetConv.last_message_time) {
                        conversationDao.updateLastMessage(targetConv.id, conv.last_message, conv.last_message_time)
                    }
                    if (wasPinned && !targetConv.is_pinned) {
                        conversationDao.updatePinnedStatus(targetConv.id, true)
                    }
                    if (targetConv.chat_type == Conversation.CHAT_TYPE_AUTO && conv.chat_type != Conversation.CHAT_TYPE_AUTO) {
                        conversationDao.updateChatType(targetConv.id, conv.chat_type)
                    }
                    conversationDao.deleteById(conv.id)
                } else if (targetConv == null) {
                    Log.d(TAG, "重命名对话: $originalName -> $cleanName")
                    conversationDao.updateConversationNameAndKey(conv.id, cleanName, cleanKey)
                    if (wasPinned && !conv.is_pinned) {
                        conversationDao.updatePinnedStatus(conv.id, true)
                    }
                }
            }
        }
    }

    // ==================== 备份导入与导出 ====================

    /** 导出全量备份 JSON 字符串 */
    suspend fun exportBackupJson(): String {
        val conversations = conversationDao.getAllConversationsSyncList()
        val allMessages = messageDao.getAllMessagesGroupedByConversation()

        val rootObj = org.json.JSONObject().apply {
            put("version", 1)
            put("exported_at", System.currentTimeMillis())

            val convArray = org.json.JSONArray()
            for (c in conversations) {
                convArray.put(org.json.JSONObject().apply {
                    put("id", c.id)
                    put("platform", c.platform)
                    put("conversation_key", c.conversation_key)
                    put("display_name", c.display_name)
                    put("last_message", c.last_message)
                    put("last_message_time", c.last_message_time)
                    put("unread_count", c.unread_count)
                    put("is_pinned", c.is_pinned)
                    put("is_system", c.is_system)
                    // 会话类型（手动指定的群聊/私信）也一并带上，合并时不会被对方覆盖成"自动"
                    put("chat_type", c.chat_type)
                })
            }
            put("conversations", convArray)

            val msgArray = org.json.JSONArray()
            for (m in allMessages) {
                msgArray.put(org.json.JSONObject().apply {
                    put("id", m.id)
                    put("conversation_id", m.conversation_id)
                    put("sender_name", m.sender_name)
                    put("content", m.content)
                    put("timestamp", m.timestamp)
                    put("platform", m.platform)
                    put("notification_id", m.notification_id)
                    put("is_recalled", m.is_recalled)
                })
            }
            put("messages", msgArray)
        }
        return rootObj.toString(2)
    }

    /**
     * 导入全量备份 JSON（**合并**语义：只补齐本地没有的消息，绝不删除/覆盖本地已有数据）。
     *
     * 去重规则（"同一时刻的相同消息只保留一条"）：
     * 1) 同一会话 + 同一发送者 + 同一内容 + **同一时间戳** → 视为同一条，跳过；
     * 2) 双设备同时在线时，同一条消息在两台手机上的落库时间戳可能相差几百毫秒：
     *    再按（会话 + 发送者 + 内容）与"导入前就存在的本地消息"做 **一对一最近匹配**
     *    （±[MERGE_DEDUPE_WINDOW_MS] 内），每条本地消息最多吸收一条导入消息；
     * 3) 导入载荷内部只做"精确同一时刻"去重 —— 同一台设备连发的两条同内容消息
     *    时间戳不同，必须都保留（避免旧实现按 1 秒窗口误删）。
     *
     * 全程在一个事务里执行：中途失败会整体回滚，不会出现"删了一半"的状态。
     *
     * @return 实际新增的消息条数
     */
    suspend fun importBackupJson(jsonStr: String): Int = withContext(Dispatchers.IO) {
        importBackupJsonInternal(jsonStr)
    }

    /**
     * 合并导入的内部实现：与 [importBackupJson] 完全相同的"合并 + 去重"语义，
     * 自带 [database.withTransaction] 单事务保护（中途失败整体回滚）。
     *
     * 抽出为私有函数是为了让 [replaceWithBackupJson] 在**外层事务内**复用它，
     * 从而保证"回退 = 先清空再导入"是一个不可分割的原子操作。
     *
     * @return 实际新增的消息条数
     */
    private suspend fun importBackupJsonInternal(jsonStr: String): Int {
        val rootObj = org.json.JSONObject(jsonStr)
        val convArray = rootObj.optJSONArray("conversations") ?: org.json.JSONArray()
        val msgArray = rootObj.optJSONArray("messages") ?: org.json.JSONArray()

        var importedCount = 0

        database.withTransaction {
            val oldIdToNewIdMap = mutableMapOf<Long, Long>()

            // ── 1. 合并对话：按 conversation_key 精确对应 ──
            for (i in 0 until convArray.length()) {
                val cObj = convArray.getJSONObject(i)
                val oldId = cObj.optLong("id")
                val key = cObj.optString("conversation_key")
                if (key.isBlank()) continue
                val platform = cObj.optString("platform")
                val displayName = cObj.optString("display_name").ifBlank { key.substringAfter('_') }
                val lastMsg = cObj.optString("last_message")
                val lastTs = cObj.optLong("last_message_time")
                val importedUnread = cObj.optLong("unread_count", 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val isPinned = cObj.optBoolean("is_pinned", false) ||
                    com.messagekeeper.data.prefs.PinnedConversationsManager.isPinned(key)
                if (isPinned) {
                    com.messagekeeper.data.prefs.PinnedConversationsManager.setPinned(key, true)
                }
                val isSystem = cObj.optBoolean("is_system", false)
                val chatType = cObj.optInt("chat_type", Conversation.CHAT_TYPE_AUTO)

                val existing = conversationDao.getConversationByKey(key)
                if (existing == null) {
                    var newId = conversationDao.insert(
                        Conversation(
                            platform = platform,
                            conversation_key = key,
                            display_name = displayName,
                            last_message = lastMsg,
                            last_message_time = lastTs,
                            unread_count = importedUnread,
                            is_pinned = isPinned,
                            is_system = isSystem,
                            chat_type = chatType
                        )
                    )
                    if (newId == -1L) {
                        newId = conversationDao.getConversationByKey(key)?.id ?: 0L
                    }
                    if (newId != 0L) oldIdToNewIdMap[oldId] = newId
                } else {
                    oldIdToNewIdMap[oldId] = existing.id
                    // 只合并不覆盖：更晚的预览才更新；置顶/系统标记取"或"；
                    // 显示名、未读数以本地为准；手动指定的会话类型不被对方覆盖
                    if (lastTs > existing.last_message_time) {
                        conversationDao.updateLastMessage(existing.id, lastMsg, lastTs)
                    }
                    if (isPinned && !existing.is_pinned) {
                        conversationDao.updatePinnedStatus(existing.id, true)
                    }
                    if (isSystem && !existing.is_system) {
                        conversationDao.updateSystemStatus(existing.id, true)
                    }
                    if (existing.chat_type == Conversation.CHAT_TYPE_AUTO &&
                        chatType != Conversation.CHAT_TYPE_AUTO
                    ) {
                        conversationDao.updateChatType(existing.id, chatType)
                    }
                }
            }

            // ── 2. 建"导入前本地已有消息"索引：会话 -> (发送者\0内容) -> 时间戳列表 ──
            //     只收录导入前就存在的消息；配对成功即从列表移除，保证一对一匹配
            val localTimes = HashMap<Long, HashMap<String, MutableList<Long>>>()
            for (convId in oldIdToNewIdMap.values.toSet()) {
                val perConv = HashMap<String, MutableList<Long>>()
                for (m in messageDao.getMessagesByConversationSync(convId)) {
                    perConv.getOrPut(dedupeKey(m.sender_name, m.content)) { mutableListOf() }.add(m.timestamp)
                }
                perConv.values.forEach { it.sort() }
                localTimes[convId] = perConv
            }
            // 导入载荷内部的精确去重键：会话|发送者|内容|时间戳
            val importedExact = HashSet<String>()

            // ── 3. 合并消息：只补本地没有的 ──
            for (i in 0 until msgArray.length()) {
                val mObj = msgArray.getJSONObject(i)
                val targetConvId = oldIdToNewIdMap[mObj.optLong("conversation_id")] ?: continue
                if (targetConvId == 0L) continue
                val senderName = mObj.optString("sender_name")
                val content = mObj.optString("content")
                val ts = mObj.optLong("timestamp")
                if (ts <= 0L) continue
                val platform = mObj.optString("platform")
                val notifId = mObj.optString("notification_id")
                val isRecalled = mObj.optBoolean("is_recalled", false)

                val exactKey = "$targetConvId\u0001$senderName\u0001$content\u0001$ts"
                if (!importedExact.add(exactKey)) continue

                val candidates = localTimes.getOrPut(targetConvId) { HashMap() }
                    .getOrPut(dedupeKey(senderName, content)) { mutableListOf() }

                // 3a. 同一时刻的同一条：已存在 → 跳过（并消耗一个配对名额）
                val exactIdx = candidates.indexOfFirst { it == ts }
                if (exactIdx >= 0) {
                    candidates.removeAt(exactIdx)
                    continue
                }
                // 3b. 双设备时间戳抖动：与最近的一条本地消息一对一配对待 → 视为同一条
                val nearIdx = nearestIndexWithin(candidates, ts, MERGE_DEDUPE_WINDOW_MS)
                if (nearIdx >= 0) {
                    candidates.removeAt(nearIdx)
                    continue
                }

                // 3c. 本地确实没有 → 补进来
                messageDao.insert(
                    Message(
                        conversation_id = targetConvId,
                        sender_name = senderName,
                        content = content,
                        timestamp = ts,
                        platform = platform,
                        notification_id = notifId,
                        is_recalled = isRecalled
                    )
                )
                importedCount++
            }
        }

        Log.d(TAG, "合并备份完成：去重后新增 $importedCount 条消息")
        return importedCount
    }

    /**
     * 用备份**整体替换**本地数据（回退语义）：
     * 在同一个事务里先清空 messages / conversations（special_alerts 不动），
     * 再复用 [importBackupJsonInternal] 的解析与插入逻辑写回备份内容。
     *
     * - 中途任何异常（包括备份 JSON 解析失败）都会让 Room 事务整体回滚，
     *   不会出现"清空了却没导回来"的中间态；
     * - 调用方（BackupManager.restoreFromBackup）必须在调用前先创建一份 prerestore 备份；
     * - 与合并导入不同，这里会丢弃备份中不存在、且本地独有的数据。
     *
     * @return 实际导入的消息条数
     */
    suspend fun replaceWithBackupJson(jsonStr: String): Int = withContext(Dispatchers.IO) {
        Log.w(TAG, "开始整体回退：清空本地消息与对话（特别提醒规则保留）")
        val importedCount = database.withTransaction {
            messageDao.deleteAll()
            conversationDao.deleteAll()
            // 复用合并导入的内部实现（嵌套事务会并入当前事务，失败一起回滚）
            importBackupJsonInternal(jsonStr)
        }
        Log.d(TAG, "整体回退完成：导入 $importedCount 条消息")
        importedCount
    }

    /**
     * 清理早于 [cutoffMs] 的历史消息，并顺带删除因此变空、最后消息时间也早于 cutoff 的会话。
     * 破坏性操作：单事务执行 + 日志；special_alerts 不动。
     *
     * @return 实际删除的消息条数
     */
    suspend fun purgeMessagesBefore(cutoffMs: Long): Int = withContext(Dispatchers.IO) {
        val deletedMessages = database.withTransaction {
            val deletedMsgs = messageDao.deleteMessagesBefore(cutoffMs)
            val deletedConvs = conversationDao.deleteEmptyConversationsBefore(cutoffMs)
            Log.i(
                TAG,
                "自动清理(cutoff=$cutoffMs)：删除历史消息 $deletedMsgs 条，清理空会话 $deletedConvs 个"
            )
            deletedMsgs
        }
        deletedMessages
    }

    /** 去重键：同会话内"发送者 + 内容"相同才可能判重 */
    private fun dedupeKey(sender: String, content: String): String = "$sender\u0000$content"

    /** 在时间戳列表里找与 target 最近、且差值不超过 window 的元素下标，找不到返回 -1 */
    private fun nearestIndexWithin(times: MutableList<Long>, target: Long, window: Long): Int {
        var bestIndex = -1
        var bestDiff = Long.MAX_VALUE
        for (i in times.indices) {
            val diff = kotlin.math.abs(times[i] - target)
            if (diff <= window && diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * 一键重置清空所有消息与对话数据
     */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        messageDao.deleteAll()
        conversationDao.deleteAll()
        Log.d(TAG, "已重置并清空所有消息与对话数据")
    }

    /**
     * 清理所有历史乱码（问号 / \uFFFD）的异常消息与对话，
     * 并回填群聊消息的真实发送者（旧版本群消息 sender_name 存的是群名、正文带 "张三: " 前缀）
     */
    suspend fun cleanCorruptedData() = withContext(Dispatchers.IO) {
        try {
            messageDao.deleteCorruptedMessages()
            messageDao.deleteHashMessages()
            // 同一时刻的相同消息只保留一条（多设备同时在线产生的历史重复）
            val deduped = messageDao.deleteExactDuplicates()
            conversationDao.deleteCorruptedConversations()
            conversationDao.deleteHashConversations()
            backfillGroupSenderNames()
            Log.d(TAG, "已成功清理乱码数据并回填群消息发送者，去除重复消息 $deduped 条")
        } catch (e: Exception) {
            Log.e(TAG, "清理乱码数据失败", e)
        }
    }

    /**
     * 回填群聊消息的真实发送者：
     * 旧版群消息 sender_name = 群名、content = "张三: 内容"，拆开后发送者存成员名。
     * 只有"证明是群聊"的会话才回填——同一会话里出现 ≥2 个不同发送者
     * （新格式消息或不同的正文前缀），避免误拆私聊里 "注意: 明天开会" 这类正文。
     */
    private suspend fun backfillGroupSenderNames() {
        val conversations = conversationDao.getAllConversationsSyncList()
        var splitCount = 0
        for (conv in conversations) {
            // 群聊只存在于微信/QQ；系统会话（QQ音乐、视频动态）跳过
            if (conv.is_system || conv.platform !in setOf("wechat", "qq")) continue
            // 用户手动指定为私信的会话绝不动
            if (conv.chat_type == Conversation.CHAT_TYPE_PRIVATE) continue
            val messages = messageDao.getMessagesByConversationSync(conv.id)

            // 旧格式消息：发送者 = 会话名
            val candidates = messages.filter { it.sender_name == conv.display_name }
            if (candidates.isEmpty()) continue

            // 手动指定的群聊直接拆；自动识别的会话需要 ≥2 个不同发送者作群聊证据
            // （新格式消息或不同的正文前缀），避免误拆私聊里 "注意: 明天开会" 这类正文
            val isProvenGroup = if (conv.chat_type == Conversation.CHAT_TYPE_GROUP) {
                true
            } else {
                val provenSenders = messages.filter { it.sender_name != conv.display_name }
                    .map { it.sender_name }.distinct()
                val oldPrefixes = candidates.mapNotNull {
                    NotificationParser.splitInTextSender(it.content)?.first
                }.filter { it != conv.display_name }.distinct()
                provenSenders.size + oldPrefixes.size >= 2
            }
            if (!isProvenGroup) continue

            for (message in candidates) {
                val split = NotificationParser.splitInTextSender(message.content) ?: continue
                if (split.first == conv.display_name) continue
                messageDao.update(message.copy(sender_name = split.first, content = split.second))
                splitCount++
            }
        }
        Log.d(TAG, "群消息发送者回填完成，共拆分 $splitCount 条")
    }

    // ==================== 情报分析 (Analytics) ====================

    suspend fun getTotalMessageCount(conversationId: Long? = null): Int =
        if (conversationId == null) messageDao.getTotalMessageCount()
        else messageDao.getTotalMessageCountByConversation(conversationId)

    suspend fun getTopContacts(limit: Int = 5, conversationId: Long? = null) =
        if (conversationId == null) messageDao.getTopContacts(limit)
        else messageDao.getTopSendersInConversation(conversationId, limit)

    suspend fun getMessageCountByHour(conversationId: Long? = null) =
        if (conversationId == null) messageDao.getMessageCountByHour()
        else messageDao.getMessageCountByHourForConversation(conversationId)

    suspend fun getMessageCountByDay(since: Long, conversationId: Long? = null) =
        if (conversationId == null) messageDao.getMessageCountByDay(since)
        else messageDao.getMessageCountByDayForConversation(conversationId, since)

    suspend fun getMessageTimestampsSince(since: Long, conversationId: Long? = null): List<Long> =
        if (conversationId == null) messageDao.getMessageTimestampsSince(since)
        else messageDao.getMessageTimestampsForConversationSince(conversationId, since)
}








