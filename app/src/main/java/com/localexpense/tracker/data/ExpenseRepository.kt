package com.localexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val smsRuleDao: SmsRuleDao
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).expenseDao(),
        AppDatabase.getDatabase(context).categoryDao(),
        AppDatabase.getDatabase(context).smsRuleDao()
    )

    fun observeAll(): Flow<List<Expense>> = expenseDao.observeAll()
    fun observeExpenses(): Flow<List<Expense>> = expenseDao.observeAll()
    fun observeTotalsBySource(): Flow<List<SourceTotal>> = expenseDao.observeTotalsBySource()
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()
    fun observeTotalsByCategoryBetween(start: Long, end: Long): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategoryBetween(start, end)

    suspend fun insert(expense: Expense): Long = expenseDao.insertExpense(expense)
    suspend fun insertExpense(expense: Expense): Long = expenseDao.insertExpense(expense)
    suspend fun update(expense: Expense) = expenseDao.update(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)
    suspend fun delete(expense: Expense) = expenseDao.delete(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun cleanupDuplicates(dedupWindowMillis: Long = 10 * 60 * 1000L): Int {
        val all = expenseDao.getAllOnce()
        val lastSeenTimestamp = HashMap<Pair<Double, String>, Long>()
        val toDelete = mutableListOf<Expense>()

        for (expense in all) {
            val key = expense.amount to expense.bankName
            val previousTimestamp = lastSeenTimestamp[key]
            if (previousTimestamp != null && expense.timestamp - previousTimestamp <= dedupWindowMillis) {
                toDelete += expense
            }
            lastSeenTimestamp[key] = expense.timestamp
        }

        toDelete.forEach { expenseDao.delete(it) }
        return toDelete.size
    }

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
    suspend fun addCategory(name: String): Long = categoryDao.insert(Category(name = name, isBuiltIn = false))
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    fun observeRules(): Flow<List<SmsRule>> = smsRuleDao.observeAll()
    suspend fun insertRule(rule: SmsRule): Long = smsRuleDao.insert(rule)
    suspend fun deleteRule(rule: SmsRule) = smsRuleDao.delete(rule)
}