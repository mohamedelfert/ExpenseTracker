package com.localexpense.tracker.domain

/** دفعة قادمة (دورية أو اشتراك أو قسط) — للداشبورد والتقويم. */
data class UpcomingPayment(
    val name: String,
    val amountMinor: Long,
    val dueDate: Long,
    val kind: UpcomingKind
)

enum class UpcomingKind { RECURRING, SUBSCRIPTION, INSTALLMENT }

/**
 * كل أرقام الشهر محسوبة مرة واحدة من محرّك التحليلات (كلها تجميعات SQL).
 *
 * ده **المصدر الوحيد** للأرقام في: الداشبورد، الرؤى، التقارير، والمساعد
 * (المرحلة 19). المساعد بيقرا من الكائن ده بس ومش بيحسب أي حاجة بنفسه —
 * ده اللي بيضمن قاعدة الـ spec: "الـ AI ما يحسبش أرقام مالية".
 */
data class FinancialContext(
    val monthLabel: String,
    val summary: MonthSummary,
    val categoryTotals: Map<String, Long> = emptyMap(),
    val previousCategoryTotals: Map<String, Long> = emptyMap(),
    val topMerchants: List<Pair<String, Long>> = emptyList(),
    val forecast: ForecastResult,
    val overallBudgetMinor: Long = 0,
    val categoryBudgets: Map<String, Long> = emptyMap(),
    val previousNetSpentMinor: Long = 0,
    val previousDailyAverageMinor: Long = 0,
    val subscriptionsMonthlyMinor: Long = 0,
    val installmentsMonthlyMinor: Long = 0,
    val upcoming: List<UpcomingPayment> = emptyList()
) {
    val comparison: MonthComparison by lazy { compareMonths(previousCategoryTotals, categoryTotals) }

    val overallBudget: BudgetProgress by lazy {
        budgetProgress(summary.netSpentMinor, overallBudgetMinor)
    }

    fun categoryBudget(categoryName: String): BudgetProgress =
        budgetProgress(categoryTotals[categoryName] ?: 0L, categoryBudgets[categoryName] ?: 0L)

    /** الفئات اللي ليها ميزانية، مرتّبة بالأعلى استخدامًا. */
    val categoryBudgetProgress: List<Pair<String, BudgetProgress>> by lazy {
        categoryBudgets.filterValues { it > 0L }
            .map { (name, _) -> name to categoryBudget(name) }
            .sortedByDescending { it.second.ratio }
    }
}
