package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT COUNT(*) FROM expenses WHERE rawBody = :body AND timestamp = :timestamp")
    suspend fun exists(body: String, timestamp: Long): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}