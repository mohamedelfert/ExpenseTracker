package com.localexpense.tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime")
    fun observeTotalBetween(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT bankName AS bankName, SUM(amount) AS total FROM expenses GROUP BY bankName")
    fun observeTotalsBySource(): Flow<List<SourceTotal>>

    @Query("SELECT categoryName AS categoryName, SUM(amount) AS total FROM expenses GROUP BY categoryName")
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT COUNT(*) FROM expenses WHERE rawBody = :body AND timestamp = :timestamp")
    suspend fun exists(body: String, timestamp: Long): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}