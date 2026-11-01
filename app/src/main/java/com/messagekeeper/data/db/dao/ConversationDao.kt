package com.messagekeeper.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.messagekeeper.data.db.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    /** 获取所有对话，优先置顶，其次按最后消息时间倒序 */
    @Query("SELECT * FROM conversations ORDER BY is_pinned DESC, last_message_time DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    /** 根据 ID 获取对话 */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    /** 获取所有对话（同步，用于清理和合并） */
    @Query("SELECT * FROM conversations")
    suspend fun getAllConversationsSyncList(): List<Conversation>

    /** 根据 conversation_key 获取对话 */
    @Query("SELECT * FROM conversations WHERE conversation_key = :key LIMIT 1")
    suspend fun getConversationByKey(key: String): Conversation?

    /** 插入或替换对话 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: Conversation): Long

    /** 更新对话 */
    @Update
    suspend fun update(conversation: Conversation)

    /** 删除所有对话 */
    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    /** 清理含有 \uFFFD 或连续问号乱码的异常对话 */
    @Query("DELETE FROM conversations WHERE display_name LIKE '%\uFFFD%' OR display_name LIKE '????%' OR conversation_key LIKE '%\uFFFD%'")
    suspend fun deleteCorruptedConversations()

    /** 清理含有 # 的异常对话 */
    @Query("DELETE FROM conversations WHERE display_name LIKE '%#%'")
    suspend fun deleteHashConversations()

    /** 强制更新对话名称和 Key */
    @Query("UPDATE conversations SET display_name = :newName, conversation_key = :newKey WHERE id = :id")
    suspend fun updateConversationNameAndKey(id: Long, newName: String, newKey: String)

    /** 设置置顶状态 */
    @Query("UPDATE conversations SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updatePinnedStatus(id: Long, isPinned: Boolean)

    /** 设置软件消息状态 */
    @Query("UPDATE conversations SET is_system = :isSystem WHERE id = :id")
    suspend fun updateSystemStatus(id: Long, isSystem: Boolean)

    /** 设置会话类型（0=自动 1=群聊 2=私信） */
    @Query("UPDATE conversations SET chat_type = :chatType WHERE id = :id")
    suspend fun updateChatType(id: Long, chatType: Int)

    /** 更新对话的最后一条消息 */
    @Query("""
        UPDATE conversations
        SET last_message = :message, last_message_time = :timestamp, unread_count = unread_count + 1
        WHERE id = :conversationId
    """)
    suspend fun updateLastMessage(conversationId: Long, message: String, timestamp: Long)

    /** 重置未读计数 */
    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun resetUnreadCount(conversationId: Long)

    /** 删除对话 */
    @Delete
    suspend fun delete(conversation: Conversation)

    /** 根据 ID 删除对话 */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 清理因历史消息被删除而变空、且最后消息时间也早于 cutoff 的会话（历史消息自动清理用）
     * 仅删除"没有任何消息残留"的会话，避免误删仍有消息的会话
     * @return 实际删除的会话数
     */
    @Query(
        "DELETE FROM conversations " +
            "WHERE last_message_time < :cutoff " +
            "AND id NOT IN (SELECT DISTINCT conversation_id FROM messages)"
    )
    suspend fun deleteEmptyConversationsBefore(cutoff: Long): Int
}

