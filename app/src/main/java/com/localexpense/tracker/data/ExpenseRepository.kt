package com.localexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.smsRuleDao()

    fun observeExpenses(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeTotalBetween(start: Long, end: Long): Flow<Double> =
        expenseDao.observeTotalBetween(start, end)

    fun observeTotalsBySource(start: Long, end: Long): Flow<List<SourceTotal>> =
        expenseDao.observeTotalsBySource(start, end)

    suspend fun addExpense(expense: Expense) = expenseDao.insert(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    fun observeRules(): Flow<List<SmsRule>> = ruleDao.observeAll()
    suspend fun saveRule(rule: SmsRule) = ruleDao.insert(rule)
    suspend fun deleteRule(rule: SmsRule) = ruleDao.delete(rule)
}
