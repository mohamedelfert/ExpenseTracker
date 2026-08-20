package com.localexpense.tracker.domain

/**
 * ملخص شهر مالي. كل المبالغ بوحدات صغرى (قروش) وكلها موجبة كقيم مطلقة؛
 * الإشارة بتتحدد في [netCashFlowMinor] و [netSpentMinor].
 *
 * القاعدة (من الـ spec، والاختبارات بتثبّتها):
 *   الصافي = الدخل − المصروفات + الاسترداد
 *   **التحويلات مش بتدخل في أي حساب** — تحويل من حساب لحساب مش مصروف ومش دخل.
 */
data class MonthSummary(
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val refundMinor: Long = 0,
    val transferMinor: Long = 0
) {
    /** المصروف الصافي: المصروفات ناقص الاسترداد (الاسترداد بيقلّل الصرف). */
    val netSpentMinor: Long get() = expenseMinor - refundMinor

    /** صافي التدفق النقدي: دخل − مصروفات + استرداد. التحويلات مستثناة. */
    val netCashFlowMinor: Long get() = incomeMinor - expenseMinor + refundMinor

    /** المتبقي من الدخل بعد الصرف الصافي (بيبقى سالب لو الصرف زاد عن الدخل). */
    val remainingMinor: Long get() = incomeMinor - netSpentMinor
}

/** متوسط الصرف اليومي بوحدات صغرى. [daysElapsed] لازم يكون 1 على الأقل. */
fun dailyAverageMinor(netSpentMinor: Long, daysElapsed: Int): Long =
    if (daysElapsed <= 0) 0L else netSpentMinor / daysElapsed

/**
 * نسبة التغيير بين قيمتين كنسبة مئوية. بترجع null لو مفيش أساس للمقارنة
 * (الشهر السابق صفر) — نسبة "زيادة ∞%" مش معلومة مفيدة، فبنقولها للمستخدم
 * كـ "جديد" بدل رقم مضلل.
 */
fun percentChange(previousMinor: Long, currentMinor: Long): Double? {
    if (previousMinor == 0L) return null
    return (currentMinor - previousMinor) * 100.0 / previousMinor
}
