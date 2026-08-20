package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {

    @Query("SELECT * FROM merchants ORDER BY name ASC")
    fun observeAll(): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants")
    suspend fun getAllOnce(): List<Merchant>

    @Query("SELECT * FROM merchants WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): Merchant?

    @Query("SELECT * FROM merchants WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Merchant?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: Merchant): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(merchants: List<Merchant>)

    @Update
    suspend fun update(merchant: Merchant)

    @Delete
    suspend fun delete(merchant: Merchant)

    @Query("DELETE FROM merchants")
    suspend fun deleteAll()
}

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<MerchantRule>>

    @Query("SELECT * FROM merchant_rules WHERE isEnabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabled(): List<MerchantRule>

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAllOnce(): List<MerchantRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MerchantRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRule>)

    @Update
    suspend fun update(rule: MerchantRule)

    @Delete
    suspend fun delete(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE pattern = :pattern")
    suspend fun deleteByPattern(pattern: String)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
