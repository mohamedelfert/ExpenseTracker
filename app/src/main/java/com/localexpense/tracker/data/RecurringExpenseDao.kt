package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringExpense>>
    
    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAllSync(): List<RecurringExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringExpense: RecurringExpense): Long

    @Update
    suspend fun update(recurringExpense: RecurringExpense)

    @Delete
    suspend fun delete(recurringExpense: RecurringExpense)
}
