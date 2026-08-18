package com.localexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.smsRuleDao()
    private val categoryDao = db.categoryDao()

    fun observeExpenses(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeTotalBetween(start: Long, end: Long): Flow<Double> =
        expenseDao.observeTotalBetween(start, end).map { it ?: 0.0 }

    fun observeTotalsBySource(): Flow<List<SourceTotal>> =
        expenseDao.observeTotalsBySource()

    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategory()

    suspend fun addExpense(expense: Expense) = expenseDao.insert(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    fun observeRules(): Flow<List<SmsRule>> = ruleDao.observeAll()
    suspend fun saveRule(rule: SmsRule) = ruleDao.insert(rule)
    suspend fun deleteRule(rule: SmsRule) = ruleDao.delete(rule)

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
    suspend fun addCategory(name: String) = categoryDao.insert(Category(name = name))
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}
