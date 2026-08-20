package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    /** ميزانيات الفئات فقط (بدون صف الميزانية الكلية). */
    @Query("SELECT * FROM budgets WHERE categoryName != '__OVERALL__'")
    fun observeCategoryBudgets(): Flow<List<Budget>>

    @Query("SELECT limitMinor FROM budgets WHERE categoryName = '__OVERALL__' LIMIT 1")
    fun observeOverallLimit(): Flow<Long?>

    @Query("SELECT * FROM budgets WHERE categoryName = :categoryName LIMIT 1")
    suspend fun getBudget(categoryName: String): Budget?

    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun delete(categoryName: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
