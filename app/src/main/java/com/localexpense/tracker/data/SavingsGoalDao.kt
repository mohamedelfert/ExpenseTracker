package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActiveGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun observeAllGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    @Query("UPDATE savings_goals SET savedMinor = savedMinor + :amountMinor WHERE id = :goalId")
    suspend fun addSaving(goalId: Long, amountMinor: Long)

    @Query("UPDATE savings_goals SET isArchived = 1 WHERE id = :goalId")
    suspend fun archive(goalId: Long)
}
