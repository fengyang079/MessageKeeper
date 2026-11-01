package com.messagekeeper.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.messagekeeper.data.db.entity.SpecialAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecialAlertDao {

    /** 获取所有特别提醒规则 */
    @Query("SELECT * FROM special_alerts ORDER BY created_at DESC")
    fun getAllAlerts(): Flow<List<SpecialAlert>>

    /** 获取所有启用的特别提醒规则 */
    @Query("SELECT * FROM special_alerts WHERE is_enabled = 1")
    suspend fun getEnabledAlerts(): List<SpecialAlert>

    /** 根据平台和名称查找提醒规则 */
    @Query("SELECT * FROM special_alerts WHERE platform = :platform AND sender_name = :senderName LIMIT 1")
    suspend fun getAlert(platform: String, senderName: String): SpecialAlert?

    /** 插入提醒规则 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: SpecialAlert): Long

    /** 更新提醒规则 */
    @Update
    suspend fun update(alert: SpecialAlert)

    /** 启用/禁用提醒规则 */
    @Query("UPDATE special_alerts SET is_enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** 删除提醒规则 */
    @Delete
    suspend fun delete(alert: SpecialAlert)

    /** 更新提醒声音 */
    @Query("UPDATE special_alerts SET sound_name = :soundName WHERE id = :id")
    suspend fun updateSound(id: Long, soundName: String)

    /** 根据 ID 删除 */
    @Query("DELETE FROM special_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
