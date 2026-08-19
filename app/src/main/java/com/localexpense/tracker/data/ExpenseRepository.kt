package com.localexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {

    // Constructor to instantiate directly using Application context
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).expenseDao(),
        AppDatabase.getDatabase(context).categoryDao()
    )

    fun observeAll(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeExpenses(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeTotalsBySource(): Flow<List<SourceTotal>> = expenseDao.observeTotalsBySource()

    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()

    fun observeTotalsByCategoryBetween(start: Long, end: Long): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategoryBetween(start, end)

    suspend fun insert(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun insertExpense(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun addExpense(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun update(expense: Expense) = expenseDao.update(expense)

    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)

    suspend fun delete(expense: Expense) = expenseDao.delete(expense)

    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun addCategory(name: String): Long = categoryDao.insert(Category(name = name, isBuiltIn = false))

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}