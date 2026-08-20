package com.localexpense.tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<Expense>

    @Query("SELECT SUM(amountMinor) FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime")
    fun observeTotalBetween(startTime: Long, endTime: Long): Flow<Long?>

    @Query("SELECT bankName AS bankName, SUM(amountMinor) AS total FROM expenses GROUP BY bankName")
    fun observeTotalsBySource(): Flow<List<SourceTotal>>

    @Query("SELECT categoryName AS categoryName, SUM(amountMinor) AS total FROM expenses GROUP BY categoryName")
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>>

    // استعلام محدد لحساب إجمالي الفئات لشهر محدد
    @Query("SELECT categoryName AS categoryName, SUM(amountMinor) AS total FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime GROUP BY categoryName")
    fun observeTotalsByCategoryBetween(startTime: Long, endTime: Long): Flow<List<CategoryTotal>>

    // نفس الفكرة لكن كـ one-shot query (مش Flow) - مستخدم في فحص تنبيهات
    // الميزانية فور تسجيل مصروف جديد.
    @Query("SELECT SUM(amountMinor) FROM expenses WHERE categoryName = :categoryName AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getCategoryTotalBetween(categoryName: String, startTime: Long, endTime: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT COUNT(*) FROM expenses WHERE rawBody = :body AND timestamp = :timestamp")
    suspend fun exists(body: String, timestamp: Long): Int

    // فحص تكرار تقريبي: بيمسك نفس العملية لو اتسجلت مرتين بفارق توقيت بسيط.
    // مقصودًا من غير شرط تطابق اسم الجهة (merchant)، لأن بعض البنوك بتبعت
    // أكتر من رسالة للعملية الواحدة (رسالة تنبيه + رسالة تأكيد، أو إعادة
    // إرسال من الشبكة) وممكن يستخرج الـ Parser اسم جهة مختلف شوية من كل
    // رسالة حتى لو العملية واحدة فعليًا. المبلغ + البنك + التوقيت القريب
    // كفاية كإشارة إن دي نفس العملية.
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

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

/**
 * نقطة دخول موحّدة لإدراج مصروف مستخرج من رسالة SMS، مستخدمة من كل من
 * SmsReceiver (الالتقاط اللحظي) و SmsImporter (الاستيراد اليدوي من صندوق
 * الرسائل)، عشان منطق منع التكرار يفضل مركزي ومتسق بين المسارين.
 *
 * سبب وجود فحصين:
 * 1) exists(): تطابق تام على النص والتوقيت (بالملي ثانية) — بيلقط لو
 *    نفس الاستيراد اليدوي اتنفذ مرتين بالظبط.
 * 2) existsSimilar(): تطابق على المبلغ + البنك في نافذة زمنية
 *    قصيرة — بيلقط الحالة الأشيع: نفس الرسالة الحقيقية بتوصل مرة عن طريق
 *    البرودكاست اللحظي (SmsReceiver.timestampMillis) ومرة عن طريق قراءة
 *    عمود DATE من مزوّد بيانات الرسائل (SmsImporter)، واللي ممكن يختلف
 *    عن بعضه شوية حتى لو الرسالة واحدة فعليًا.
 */
suspend fun ExpenseDao.insertIfNotDuplicate(
    expense: Expense,
    dedupWindowMillis: Long = 10 * 60 * 1000L // 10 دقايق
): Boolean {
    val exactDuplicate = exists(expense.rawBody, expense.timestamp) > 0
    if (exactDuplicate) return false

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