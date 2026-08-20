package com.localexpense.tracker.data

/**
 * صفوف نتائج التجميع (SUM/GROUP BY). كل التحليلات بتتحسب في SQL وبترجع
 * كصفوف صغيرة زي دي، مش بتحميل كل الحركات في الذاكرة — ده اللي بيخلي
 * التطبيق يفضل سريع مع عشرات الآلاف من الحركات.
 *
 * ملاحظة عن الإشارة: في تجميعات المصروفات بنستخدم
 * `SUM(CASE WHEN type='REFUND' THEN -amountMinor ELSE amountMinor END)`
 * فالاسترداد بيقلّل المصروف الصافي (زي ما الـ spec طالب) والتحويلات مستثناة
 * بالكامل من أي حساب مصروفات.
 */

data class MerchantTotal(
    val merchant: String,
    val total: Long,
    val count: Int
)

data class DayTotal(
    val day: String,   // "yyyy-MM-dd" بتوقيت الجهاز
    val total: Long
)

data class TypeTotal(
    val type: TransactionType,
    val total: Long
)

/**
 * استعلام تجميعي من غير GROUP BY بيرجّع صف واحد فيه NULL لو مفيش أي حركة
 * مطابقة، فالحقول لازم تكون nullable.
 */
/** تحليلات جهة واحدة لشاشة تفاصيل الجهة (المرحلة 4). */
data class MerchantAnalytics(
    val merchant: String,
    val thisMonthMinor: Long,
    val lastMonthMinor: Long,
    val transactionCount: Int,
    val averageMinor: Long,
    val highestMinor: Long,
    val history: List<Pair<String, Long>>
)

data class MerchantStats(
    val total: Long?,
    val count: Int,
    val maxMinor: Long?
)
