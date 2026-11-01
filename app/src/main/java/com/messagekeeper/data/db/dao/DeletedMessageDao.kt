package com.messagekeeper.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.messagekeeper.data.db.entity.DeletedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedMessageDao {

    /** 获取所有已删除消息，按删除时间倒序 */
    @Query("SELECT * FROM deleted_messages ORDER BY deleted_at DESC, timestamp DESC")
    fun getAllDeletedMessages(): Flow<List<DeletedMessage>>

    /** 插入单条已删除消息 */
    @Insert
    suspend fun insert(deletedMessage: DeletedMessage): Long

    /** 批量插入已删除消息 */
    @Insert
    suspend fun insertAll(deletedMessages: List<DeletedMessage>)

    /** 彻底删除单条记录 */
    @Delete
    suspend fun delete(deletedMessage: DeletedMessage)

    /** 根据 ID 彻底删除 */
    @Query("DELETE FROM deleted_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空回收站 */
    @Query("DELETE FROM deleted_messages")
    suspend fun deleteAll()
}

