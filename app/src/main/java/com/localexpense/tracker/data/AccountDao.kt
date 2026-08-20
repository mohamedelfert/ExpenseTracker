package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, name ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAllOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Account?

    /**
     * رصيد الحساب = دخل + استرداد + تحويلات داخلة − مصروف − تحويلات خارجة.
     * محسوب من الحركات نفسها (مفيش عمود رصيد متخزّن يسيب فرصة إنه يبوظ).
     */
    @Query(
        """
        SELECT COALESCE((
            SELECT SUM(CASE
                WHEN type IN ('INCOME', 'REFUND') THEN amountMinor
                WHEN type = 'EXPENSE' THEN -amountMinor
                WHEN type = 'TRANSFER' THEN -amountMinor
                ELSE 0 END)
            FROM expenses WHERE accountId = :accountId
        ), 0) + COALESCE((
            SELECT SUM(amountMinor) FROM expenses
            WHERE type = 'TRANSFER' AND toAccountId = :accountId
        ), 0)
        """
    )
    suspend fun getBalance(accountId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}
