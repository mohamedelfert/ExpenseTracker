package com.localexpense.tracker.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/**
 * كل تجميعات المصروفات هنا بتستثني التحويلات (TRANSFER) وبتخصم الاسترداد
 * (REFUND) من الإجمالي، لأن التحويل بين حسابين مش مصروف والاسترداد بيرجّع
 * فلوس. الدخل (INCOME) ليه استعلاماته المنفصلة.
 */
// ملاحظة: نص الـ SQL مكتوب كامل في كل استعلام (مش ثابت مشترك) لأن Room
// بيقرا قيمة @Query وقت التوليد، وتمرير الشرط كـ const بيخلي قابلية القراءة
// عند التوليد تعتمد على طي الثوابت — مش مخاطرة تستاهل.

@Dao
interface ExpenseDao {

    // ===== قراءة عامة =====

    // سقف صريح: القائمة الرئيسية بتعرض أحدث 2000 حركة. التحليلات كلها بتتحسب
    // في SQL، فمفيش داعي نحمّل 100 ألف صف في الذاكرة عشان نجمعهم في Kotlin.
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC LIMIT 2000")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun observeBetween(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE installmentId = :installmentId ORDER BY timestamp ASC")
    suspend fun getByInstallment(installmentId: Long): List<Expense>

    /**
     * البحث والفلاتر والترتيب (المرحلة 3) في استعلام واحد مبني في الكود
     * (راجع [TransactionQuery]). Room مش بيقدر يستقبل ORDER BY كباراميتر،
     * وثمانية استعلامات شبه متطابقة أسوأ من باني استعلام واحد بمعاملات
     * مربوطة (bound args) وترتيب من قائمة مقفولة.
     */
    @RawQuery(observedEntities = [Expense::class])
    fun searchRaw(query: SupportSQLiteQuery): Flow<List<Expense>>

    @RawQuery
    suspend fun searchRawOnce(query: SupportSQLiteQuery): List<Expense>

    // ===== تجميعات =====

    @Query("SELECT SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) FROM expenses WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime")
    fun observeTotalBetween(startTime: Long, endTime: Long): Flow<Long?>

    @Query("SELECT SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) FROM expenses WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalBetween(startTime: Long, endTime: Long): Long?

    @Query("SELECT SUM(amountMinor) FROM expenses WHERE type = :type AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getSumByType(type: TransactionType, startTime: Long, endTime: Long): Long?

    @Query("SELECT SUM(amountMinor) FROM expenses WHERE type = :type AND timestamp BETWEEN :startTime AND :endTime")
    fun observeSumByType(type: TransactionType, startTime: Long, endTime: Long): Flow<Long?>

    @Query("SELECT bankName AS bankName, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total FROM expenses WHERE type IN ('EXPENSE', 'REFUND') GROUP BY bankName")
    fun observeTotalsBySource(): Flow<List<SourceTotal>>

    @Query("SELECT categoryName AS categoryName, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total FROM expenses WHERE type IN ('EXPENSE', 'REFUND') GROUP BY categoryName")
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT categoryName AS categoryName, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY categoryName ORDER BY total DESC
        """
    )
    fun observeTotalsByCategoryBetween(startTime: Long, endTime: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT categoryName AS categoryName, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY categoryName ORDER BY total DESC
        """
    )
    suspend fun getTotalsByCategoryBetween(startTime: Long, endTime: Long): List<CategoryTotal>

    @Query(
        """
        SELECT merchant AS merchant, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total, COUNT(*) AS count FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY merchant ORDER BY total DESC LIMIT :limit
        """
    )
    fun observeTopMerchantsBetween(startTime: Long, endTime: Long, limit: Int): Flow<List<MerchantTotal>>

    @Query(
        """
        SELECT merchant AS merchant, SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total, COUNT(*) AS count FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY merchant ORDER BY total DESC LIMIT :limit
        """
    )
    suspend fun getTopMerchantsBetween(startTime: Long, endTime: Long, limit: Int): List<MerchantTotal>

    /** إجماليات يومية (بتوقيت الجهاز) — للرسم البياني وشاشة التقويم. */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total
        FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY day ORDER BY day ASC
        """
    )
    fun observeDailyTotalsBetween(startTime: Long, endTime: Long): Flow<List<DayTotal>>

    @Query(
        """
        SELECT SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) AS total, COUNT(*) AS count, MAX(amountMinor) AS maxMinor
        FROM expenses WHERE type IN ('EXPENSE', 'REFUND') AND merchant = :merchant
        AND timestamp BETWEEN :startTime AND :endTime
        """
    )
    suspend fun getMerchantStats(merchant: String, startTime: Long, endTime: Long): MerchantStats?

    /** متوسط عملية الفئة (لكشف الحركات الشاذة) — على المصروفات فقط. */
    @Query(
        """
        SELECT AVG(amountMinor) FROM expenses
        WHERE type = 'EXPENSE' AND categoryName = :categoryName AND timestamp >= :sinceTime
        """
    )
    suspend fun getCategoryAverage(categoryName: String, sinceTime: Long): Double?

    @Query(
        """
        SELECT COUNT(*) FROM expenses
        WHERE type = 'EXPENSE' AND categoryName = :categoryName AND timestamp >= :sinceTime
        """
    )
    suspend fun getCategoryCount(categoryName: String, sinceTime: Long): Int

    // نفس فكرة إجمالي الفئة لكن one-shot - مستخدم في فحص تنبيهات الميزانية
    // فور تسجيل حركة جديدة.
    @Query(
        """
        SELECT SUM(CASE WHEN type = 'REFUND' THEN -amountMinor ELSE amountMinor END) FROM expenses
        WHERE type IN ('EXPENSE', 'REFUND') AND categoryName = :categoryName
        AND timestamp BETWEEN :startTime AND :endTime
        """
    )
    suspend fun getCategoryTotalBetween(categoryName: String, startTime: Long, endTime: Long): Long?

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Query("SELECT DISTINCT bankName FROM expenses ORDER BY bankName ASC")
    fun observeBankNames(): Flow<List<String>>

    // ===== كتابة =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("UPDATE expenses SET categoryName = :categoryName, updatedAt = :now WHERE merchant = :merchant")
    suspend fun setCategoryForMerchant(merchant: String, categoryName: String, now: Long): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    // ===== منع التكرار (المرحلة 17) =====

    @Query("SELECT COUNT(*) FROM expenses WHERE rawBody = :body AND timestamp = :timestamp")
    suspend fun exists(body: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE rawHash = :rawHash AND rawHash != ''")
    suspend fun existsByRawHash(rawHash: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM expenses
        WHERE referenceId = :referenceId AND referenceId != '' AND amountMinor = :amountMinor
        """
    )
    suspend fun existsByReference(referenceId: String, amountMinor: Long): Int

    // فحص تكرار تقريبي: نفس المبلغ + نفس البنك في نافذة زمنية قصيرة. ده أضعف
    // مؤشر عندنا (عمليتين مختلفتين ممكن يكون لهم نفس المبلغ فعلاً)، فبيتساءل
    // عنه بعد المرجع والبصمة، وبيضيف شرط الجهة لو الاتنين معروفين.
    @Query(
        """
        SELECT COUNT(*) FROM expenses
        WHERE amountMinor = :amountMinor
        AND bankName = :bankName
        AND timestamp BETWEEN :startTime AND :endTime
        """
    )
    suspend fun existsSimilar(
        amountMinor: Long,
        bankName: String,
        startTime: Long,
        endTime: Long
    ): Int
}

/**
 * نقطة دخول موحّدة لإدراج حركة مستخرجة من رسالة SMS أو إشعار، مستخدمة من
 * SmsReceiver و ExpenseNotificationListener و SmsImporter عشان منطق منع
 * التكرار يفضل مركزي ومتسق بين المسارات الثلاثة.
 *
 * الترتيب من الأقوى للأضعف (المرحلة 17):
 * 1) referenceId + المبلغ: رقم مرجع البنك — أدق مؤشر ممكن يكون عندنا.
 * 2) rawHash: بصمة نص الرسالة — بتلقط نفس الرسالة لو اتقرأت مرتين.
 * 3) النص الكامل + التوقيت بالملي ثانية.
 * 4) المبلغ + البنك في نافذة زمنية قصيرة — آخر حل، لأن عمليتين حقيقيتين
 *    ممكن يتصادف إن لهم نفس المبلغ.
 */
suspend fun ExpenseDao.insertIfNotDuplicate(
    expense: Expense,
    dedupWindowMillis: Long = 10 * 60 * 1000L // 10 دقايق
): Boolean {
    if (expense.referenceId.isNotBlank() &&
        existsByReference(expense.referenceId, expense.amountMinor) > 0
    ) return false

    if (expense.rawHash.isNotBlank() && existsByRawHash(expense.rawHash) > 0) return false

    if (exists(expense.rawBody, expense.timestamp) > 0) return false

    val similarDuplicate = existsSimilar(
        amountMinor = expense.amountMinor,
        bankName = expense.bankName,
        startTime = expense.timestamp - dedupWindowMillis,
        endTime = expense.timestamp + dedupWindowMillis
    ) > 0
    if (similarDuplicate) return false

    insertExpense(expense)
    return true
}
