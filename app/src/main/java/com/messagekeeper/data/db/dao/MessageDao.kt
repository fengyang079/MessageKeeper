package com.messagekeeper.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.messagekeeper.data.db.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** 获取某个对话的所有消息，按时间正序 */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC")
    fun getMessagesPagingSource(conversationId: Long): androidx.paging.PagingSource<Int, Message>

    /** 获取某个对话的所有消息（非 Flow，用于导出） */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversationSync(conversationId: Long): List<Message>

    /** 获取所有消息（用于导出全部） */
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversation_id = c.id ORDER BY c.display_name ASC, m.timestamp ASC")
    suspend fun getAllMessagesGroupedByConversation(): List<Message>

    /** 获取消息总数（用于快速判断是否有新消息，避免全量查询） */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    /** 增量查询：只获取指定 ID 之后的新消息 */
    @Query("SELECT m.* FROM messages m WHERE m.id > :afterId ORDER BY m.timestamp ASC")
    suspend fun getMessagesSince(afterId: Long): List<Message>

    /** 搜索消息内容或联系人 */
    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN conversations c ON m.conversation_id = c.id
        WHERE m.content LIKE '%' || :query || '%'
           OR c.display_name LIKE '%' || :query || '%'
           OR m.sender_name LIKE '%' || :query || '%'
        ORDER BY m.timestamp DESC
        LIMIT 200
    """)
    suspend fun searchMessages(query: String): List<Message>

    /** 根据 NotificationId 寻找已存在消息 */
    @Query("SELECT * FROM messages WHERE notification_id = :notificationId LIMIT 1")
    suspend fun findByNotificationId(notificationId: String): Message?

    /** 寻找时间窗口内的重复消息 */
    @Query("""
        SELECT * FROM messages
        WHERE sender_name = :senderName AND timestamp >= :since AND timestamp <= :until
        LIMIT 1
    """)
    suspend fun findDuplicateMessageInWindow(senderName: String, since: Long, until: Long): Message?

    /** 更新/覆盖已有消息 */
    @Update
    suspend fun update(message: Message)

    /** 插入消息 */
    @Insert
    suspend fun insert(message: Message): Long

    /** 完全相同的重复广播：同一通知 ID + 同内容 + 同时间戳（服务重绑后重新扫描通知栏产生的重复） */
    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE notification_id = :notificationId AND content = :content AND timestamp = :timestamp
    """)
    suspend fun countExactDuplicate(notificationId: String, content: String, timestamp: Long): Int

    /** 检查同对话内时间窗口是否有相同发送者与内容的重复消息 */
    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE conversation_id = :conversationId AND sender_name = :senderName AND content = :content AND timestamp > :since
    """)
    suspend fun countDuplicateInConversation(conversationId: Long, senderName: String, content: String, since: Long): Int

    /** 获取同对话内时间窗口近期的重复消息，用于进一步校验 raw_hash */
    @Query("""
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND sender_name = :senderName AND content = :content AND timestamp > :since
    """)
    suspend fun getRecentMessagesInConversation(conversationId: Long, senderName: String, content: String, since: Long): List<Message>

    /** 删除单条消息 */
    @Delete
    suspend fun delete(message: Message)

    /** 根据 ID 删除消息 */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清理含有 \uFFFD 或连续问号乱码的异常消息 */
    @Query("DELETE FROM messages WHERE sender_name LIKE '%\uFFFD%' OR content LIKE '%\uFFFD%' OR sender_name LIKE '????%' OR content LIKE '????%'")
    suspend fun deleteCorruptedMessages()

    /** 清理发送者/联系人含有 # 的消息 */
    @Query("DELETE FROM messages WHERE sender_name LIKE '%#%'")
    suspend fun deleteHashMessages()

    /**
     * 清理"同一时刻的相同消息"的重复项：
     * 同一会话 + 同一发送者 + 同一内容 + 同一时间戳 视为同一条，只保留 id 最小的一条。
     * 用于清理多设备同时在线（两台手机各自记了一条）造成的历史重复。
     */
    @Query("""
        DELETE FROM messages WHERE EXISTS (
            SELECT 1 FROM messages m2
            WHERE m2.conversation_id = messages.conversation_id
              AND m2.sender_name = messages.sender_name
              AND m2.content = messages.content
              AND m2.timestamp = messages.timestamp
              AND m2.id < messages.id
        )
    """)
    suspend fun deleteExactDuplicates(): Int

    /** 删除某个对话的所有消息 */
    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long)

    /** 批量更新消息的所属对话 */
    @Query("UPDATE messages SET conversation_id = :newConversationId WHERE conversation_id = :oldConversationId")
    suspend fun updateConversationId(oldConversationId: Long, newConversationId: Long)

    /** 删除所有消息 */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    /**
     * 删除时间戳早于 cutoff 的消息（历史消息自动清理用）
     * @return 实际删除的消息条数
     */
    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteMessagesBefore(cutoff: Long): Int

    /** 标记消息为已撤回 */
    @Query("UPDATE messages SET is_recalled = 1 WHERE id = :id")
    suspend fun markAsRecalled(id: Long)

    /** 一次性修复已有数据的 sender_name 为会话名称 */
    @Query("UPDATE messages SET sender_name = (SELECT display_name FROM conversations WHERE id = messages.conversation_id)")
    suspend fun fixSenderNamesToMatchConversation()

    // ==================== 情报分析 (Analytics) ====================

    /** 获取总消息数（排除软件消息和QQ音乐） */
    @Query("""
        SELECT COUNT(*) FROM messages m 
        INNER JOIN conversations c ON m.conversation_id = c.id 
        WHERE c.is_system = 0 AND m.platform != 'qqmusic'
    """)
    suspend fun getTotalMessageCount(): Int

    /** 获取指定会话的总消息数 */
    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    suspend fun getTotalMessageCountByConversation(conversationId: Long): Int

    /** 获取最活跃的联系人（按消息数降序，排除软件消息和QQ音乐） */
    @Query("""
        SELECT c.display_name as name, COUNT(m.id) as count 
        FROM messages m 
        INNER JOIN conversations c ON m.conversation_id = c.id 
        WHERE c.is_system = 0 AND m.platform != 'qqmusic'
        GROUP BY m.conversation_id 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    suspend fun getTopContacts(limit: Int = 5): List<com.messagekeeper.data.models.ContactStat>

    /** 获取指定会话内最活跃的发送者（适合群聊或私聊统计） */
    @Query("""
        SELECT sender_name as name, COUNT(id) as count
        FROM messages
        WHERE conversation_id = :conversationId
        GROUP BY sender_name
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getTopSendersInConversation(conversationId: Long, limit: Int = 5): List<com.messagekeeper.data.models.ContactStat>

    /** 获取按小时统计的消息热度（排除软件消息和QQ音乐） */
    @Query("""
        SELECT strftime('%H', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) as hour, COUNT(*) as count 
        FROM messages m 
        INNER JOIN conversations c ON m.conversation_id = c.id 
        WHERE c.is_system = 0 AND m.platform != 'qqmusic'
        GROUP BY hour 
        ORDER BY hour ASC
    """)
    suspend fun getMessageCountByHour(): List<com.messagekeeper.data.models.HourlyStat>

    /** 获取指定会话按小时统计的消息热度 */
    @Query("""
        SELECT strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) as hour, COUNT(*) as count
        FROM messages
        WHERE conversation_id = :conversationId
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getMessageCountByHourForConversation(conversationId: Long): List<com.messagekeeper.data.models.HourlyStat>

    @Query("""
        SELECT strftime('%Y-%m-%d', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) as day,
               COUNT(*) as count
        FROM messages m
        INNER JOIN conversations c ON m.conversation_id = c.id
        WHERE c.is_system = 0 AND m.platform != 'qqmusic' AND m.timestamp >= :since
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getMessageCountByDay(since: Long): List<com.messagekeeper.data.models.DailyStat>

    @Query("""
        SELECT strftime('%Y-%m-%d', datetime(timestamp / 1000, 'unixepoch', 'localtime')) as day,
               COUNT(*) as count
        FROM messages
        WHERE conversation_id = :conversationId AND timestamp >= :since
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getMessageCountByDayForConversation(conversationId: Long, since: Long): List<com.messagekeeper.data.models.DailyStat>

    /** 获取时间范围内的消息时间戳（排除软件消息和QQ音乐，用于计算消息发送趋势折线图） */
    @Query("""
        SELECT m.timestamp
        FROM messages m
        INNER JOIN conversations c ON m.conversation_id = c.id
        WHERE c.is_system = 0 AND m.platform != 'qqmusic' AND m.timestamp >= :since
        ORDER BY m.timestamp ASC
    """)
    suspend fun getMessageTimestampsSince(since: Long): List<Long>

    /** 获取指定会话在时间范围内的消息时间戳（用于计算单个联系人的消息发送趋势折线图） */
    @Query("""
        SELECT timestamp
        FROM messages
        WHERE conversation_id = :conversationId AND timestamp >= :since
        ORDER BY timestamp ASC
    """)
    suspend fun getMessageTimestampsForConversationSince(conversationId: Long, since: Long): List<Long>
}

