package com.localexpense.tracker.domain

/**
 * توقّع صرف الشهر (المرحلة 10). حساب حسابي بحت وقابل للشرح بالكامل:
 *
 *   المتوسط اليومي = الصرف الحالي ÷ الأيام المنقضية
 *   التوقّع        = المتوسط اليومي × أيام الشهر
 *
 * مفيش أي "ذكاء" ولا نموذج هنا — الـ spec بيمنع استخدام الـ AI في أي حسابات
 * مالية، والمستخدم لازم يقدر يعمل نفس الحساب بنفسه على ورقة.
 */
data class ForecastResult(
    val netSpentMinor: Long,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val dailyAverageMinor: Long,
    val projectedMinor: Long,
    /** موجب = المتوقع بيتخطى الميزانية بالمقدار ده. null = مفيش ميزانية محددة. */
    val projectedOverBudgetMinor: Long?
)

fun forecast(
    netSpentMinor: Long,
    daysElapsed: Int,
    daysInMonth: Int,
    budgetLimitMinor: Long = 0L
): ForecastResult {
    val elapsed = daysElapsed.coerceIn(1, daysInMonth)
    val remaining = (daysInMonth - elapsed).coerceAtLeast(0)
    val daily = netSpentMinor / elapsed
    // بنبني التوقّع من المتوسط × عدد الأيام (مش الصرف + المتبقي × المتوسط)
    // عشان النتيجة تفضل متسقة مع المتوسط المعروض للمستخدم.
    val projected = daily * daysInMonth
    val over = if (budgetLimitMinor > 0L) projected - budgetLimitMinor else null
    return ForecastResult(
        netSpentMinor = netSpentMinor,
        daysElapsed = elapsed,
        daysRemaining = remaining,
        dailyAverageMinor = daily,
        projectedMinor = projected,
        projectedOverBudgetMinor = over
    )
}
