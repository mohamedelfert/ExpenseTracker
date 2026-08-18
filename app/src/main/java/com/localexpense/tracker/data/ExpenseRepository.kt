package com.localexpense.tracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    fun observeAll(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeTotalsBySource(): Flow<List<SourceTotal>> = expenseDao.observeTotalsBySource()

    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()

    suspend fun insert(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun update(expense: Expense) = expenseDao.update(expense)

    suspend fun delete(expense: Expense) = expenseDao.delete(expense)
}