package com.localexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val smsRuleDao: SmsRuleDao,
    private val budgetDao: BudgetDao,
    private val recurringExpenseDao: RecurringExpenseDao
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).expenseDao(),
        AppDatabase.getDatabase(context).categoryDao(),
        AppDatabase.getDatabase(context).smsRuleDao(),
        AppDatabase.getDatabase(context).budgetDao(),
        AppDatabase.getDatabase(context).recurringExpenseDao()
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
        val lastSeenTimestamp = HashMap<Pair<Long, String>, Long>()
        val toDelete = mutableListOf<Expense>()

        for (expense in all) {
            val key = expense.amountMinor to expense.bankName
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

    fun observeBudgets(): Flow<List<Budget>> = budgetDao.observeAll()
    suspend fun setBudget(budget: Budget) = budgetDao.insert(budget)
    suspend fun deleteBudget(categoryName: String) = budgetDao.delete(categoryName)

    fun observeRecurringExpenses(): Flow<List<RecurringExpense>> = recurringExpenseDao.observeAll()
    suspend fun getRecurringExpensesSync(): List<RecurringExpense> = recurringExpenseDao.getAllSync()
    suspend fun insertRecurringExpense(expense: RecurringExpense): Long = recurringExpenseDao.insert(expense)
    suspend fun updateRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.update(expense)
    suspend fun deleteRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.delete(expense)
}