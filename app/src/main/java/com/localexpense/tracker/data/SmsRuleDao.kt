package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsRuleDao {

    @Query("SELECT * FROM sms_rules ORDER BY bankName ASC")
    fun observeAll(): Flow<List<SmsRule>>

    @Query("SELECT * FROM sms_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<SmsRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsRule): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<SmsRule>)

    @Update
    suspend fun update(rule: SmsRule)

    @Delete
    suspend fun delete(rule: SmsRule)

    @Query("SELECT COUNT(*) FROM sms_rules")
    suspend fun count(): Int

    @Query("SELECT * FROM sms_rules")
    suspend fun getAllOnce(): List<SmsRule>

    @Query("SELECT * FROM sms_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsRule?

    @Query("DELETE FROM sms_rules")
    suspend fun deleteAll()
}
