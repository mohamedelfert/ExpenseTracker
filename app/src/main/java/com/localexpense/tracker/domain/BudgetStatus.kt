package com.localexpense.tracker.domain

/** حالة الميزانية (المرحلة 7). العتبات نفس اللي التنبيهات القديمة بتستخدمها. */
enum class BudgetState { SAFE, WARNING, EXCEEDED }

data class BudgetProgress(
    val spentMinor: Long,
    val limitMinor: Long,
    val state: BudgetState,
    /** نسبة الاستخدام (0.0 = مفيش صرف، 1.0 = وصل للحد بالظبط، >1 = تخطى). */
    val ratio: Double
) {
    val remainingMinor: Long get() = limitMinor - spentMinor
    val percentUsed: Int get() = (ratio * 100).toInt()
}

const val BUDGET_WARNING_RATIO = 0.8

/**
 * حالة ميزانية واحدة. [limitMinor] = 0 يعني مفيش ميزانية محددة، وساعتها
 * الحالة SAFE والنسبة 0 (بدل قسمة على صفر).
 */
fun budgetProgress(spentMinor: Long, limitMinor: Long): BudgetProgress {
    if (limitMinor <= 0L) {
        return BudgetProgress(spentMinor, 0L, BudgetState.SAFE, 0.0)
    }
    // النسبة بتستخدم Double للعرض بس — كل المبالغ نفسها فضلت Long.
    val ratio = spentMinor.toDouble() / limitMinor
    val state = when {
        ratio >= 1.0 -> BudgetState.EXCEEDED
        ratio >= BUDGET_WARNING_RATIO -> BudgetState.WARNING
        else -> BudgetState.SAFE
    }
    return BudgetProgress(spentMinor, limitMinor, state, ratio)
}
