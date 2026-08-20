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
    @Query("SELECT * FROM recurring_expenses ORDER BY isActive DESC, dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringExpense>>

    /** الاشتراكات = دفعات دورية بـ isSubscription = 1 (نفس الجدول). */
    @Query("SELECT * FROM recurring_expenses WHERE isSubscription = 1 ORDER BY isActive DESC, nextDueDate ASC")
    fun observeSubscriptions(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses WHERE isSubscription = 0 ORDER BY isActive DESC, dayOfMonth ASC")
    fun observeRecurringOnly(): Flow<List<RecurringExpense>>

    /** إجمالي الاشتراكات الشهرية النشطة (الشهرية فقط - غيرها بيتحوّل في الدومين). */
    @Query("SELECT SUM(amountMinor) FROM recurring_expenses WHERE isSubscription = 1 AND isActive = 1")
    fun observeSubscriptionsTotal(): Flow<Long?>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAllSync(): List<RecurringExpense>

    @Query("SELECT * FROM recurring_expenses WHERE isActive = 1")
    suspend fun getActive(): List<RecurringExpense>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecurringExpense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringExpense: RecurringExpense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecurringExpense>)

    @Update
    suspend fun update(recurringExpense: RecurringExpense)

    @Delete
    suspend fun delete(recurringExpense: RecurringExpense)

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAll()
}
