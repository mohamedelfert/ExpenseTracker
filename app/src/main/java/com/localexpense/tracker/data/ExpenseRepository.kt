package com.localexpense.tracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {

    fun observeAll(): Flow<List<Expense>> = expenseDao.observeAll()

    fun observeTotalsBySource(): Flow<List<SourceTotal>> = expenseDao.observeTotalsBySource()

    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()

    fun observeTotalsByCategoryBetween(start: Long, end: Long): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategoryBetween(start, end)

    suspend fun insert(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun update(expense: Expense) = expenseDao.update(expense)

    suspend fun delete(expense: Expense) = expenseDao.delete(expense)

    /**
     * بينظف المصروفات المكررة اللي اتسجلت قبل إصلاح منطق منع التكرار
     * (نفس المبلغ + نفس البنك خلال سلسلة فروق زمنية قصيرة متتالية —
     * من غير شرط تطابق اسم الجهة، لأن رسائل التنبيه والتأكيد بتاعة
     * نفس العملية ممكن يستخرج منها الـ Parser اسم جهة مختلف شوية).
     * بيحتفظ بأول مصروف في كل سلسلة مكررة ويمسح الباقي.
     * بيرجع عدد المصروفات اللي اتمسحت.
     */
    suspend fun cleanupDuplicates(dedupWindowMillis: Long = 10 * 60 * 1000L): Int {
        val all = expenseDao.getAllOnce() // مرتبة تصاعديًا بالتوقيت
        val lastSeenTimestamp = HashMap<Pair<Double, String>, Long>()
        val toDelete = mutableListOf<Expense>()

        for (expense in all) {
            val key = expense.amount to expense.bankName
            val previousTimestamp = lastSeenTimestamp[key]
            // المقارنة بآخر عنصر "اتشاف" (مش بس اللي اتحفظ) عشان نمسك
            // سلاسل زي 12:20 ← 12:23 ← 12:24 ← 12:27 حتى لو الفرق بين
            // أول وآخر واحدة في السلسلة أكبر من نافذة الفحص نفسها
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
}
