package com.localexpense.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    @Query("SELECT * FROM installments ORDER BY isActive DESC, nextDueDate ASC")
    fun observeAll(): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE isActive = 1 ORDER BY nextDueDate ASC")
    suspend fun getActive(): List<Installment>

    @Query("SELECT * FROM installments")
    suspend fun getAllOnce(): List<Installment>

    @Query("SELECT * FROM installments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Installment?

    /** إجمالي الأقساط الشهرية النشطة — بيظهر في الداشبورد. */
    @Query("SELECT SUM(installmentMinor) FROM installments WHERE isActive = 1")
    fun observeMonthlyLoad(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installment: Installment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(installments: List<Installment>)

    @Update
    suspend fun update(installment: Installment)

    @Delete
    suspend fun delete(installment: Installment)

    @Query("DELETE FROM installments")
    suspend fun deleteAll()
}
