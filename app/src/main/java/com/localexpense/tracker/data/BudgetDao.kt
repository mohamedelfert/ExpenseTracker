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

    @Query("SELECT * FROM budgets WHERE categoryName = :categoryName LIMIT 1")
    suspend fun getBudget(categoryName: String): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun delete(categoryName: String)
}
