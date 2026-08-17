package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("""
        SELECT * FROM expenses
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampMillis DESC
    """)
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM expenses
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    fun observeTotalBetween(startMillis: Long, endMillis: Long): Flow<Double>

    @Query("""
        SELECT source, COALESCE(SUM(amount), 0) as total FROM expenses
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY source
    """)
    fun observeTotalsBySource(startMillis: Long, endMillis: Long): Flow<List<SourceTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

data class SourceTotal(
    val source: String,
    val total: Double
)
