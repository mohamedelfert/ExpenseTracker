package com.localexpense.tracker.domain

import com.localexpense.tracker.money.formatMinor

enum class InsightLevel { INFO, WARNING, ALERT }

/**
 * [key] ثابت لكل نوع رؤية + الشهر، عشان "إخفاء الرؤية" يفضل مخفي بعد إعادة
 * الحساب (مخزّن في SharedPreferences، مش جدول).
 */
data class Insight(
    val key: String,
    val text: String,
    val level: InsightLevel = InsightLevel.INFO
)

/**
 * محرّك الرؤى (المرحلة 11). كل رؤية بتتولّد من أرقام [FinancialContext]
 * المحسوبة سلفًا، بشروط واضحة ومحدّدة سلفًا — نفس البيانات دايمًا بتدي نفس
 * الرؤى، وكل جملة ممكن تتحقق منها بالأرقام المعروضة في الداشبورد.
 *
 * العتبات مقصودة إنها متحفّظة (تغيير >= 20%، ميزانية >= 80%) عشان الرؤى
 * تفضل قليلة ومفيدة بدل ما تبقى ضجيج.
 */
fun generateInsights(context: FinancialContext, minChangePercent: Double = 20.0): List<Insight> {
    val month = context.monthLabel
    val insights = mutableListOf<Insight>()

    // 1. أكبر زيادة ونقصان في فئة مقارنة بالشهر السابق
    if (context.comparison.hasPrevious) {
        context.comparison.biggestIncrease?.let { change ->
            val percent = change.changePercent
            if (percent != null && percent >= minChangePercent) {
                insights += Insight(
                    key = "cat_up_${change.categoryName}_$month",
                    text = "صرفك في \"${change.categoryName}\" زاد ${percent.toInt()}% عن الشهر اللي فات " +
                        "(${formatMinor(change.currentMinor)} مقابل ${formatMinor(change.previousMinor)}).",
                    level = InsightLevel.WARNING
                )
            }
        }
        context.comparison.biggestDecrease?.let { change ->
            val percent = change.changePercent
            if (percent != null && percent <= -minChangePercent) {
                insights += Insight(
                    key = "cat_down_${change.categoryName}_$month",
                    text = "صرفك في \"${change.categoryName}\" قل ${-percent.toInt()}% عن الشهر اللي فات.",
                    level = InsightLevel.INFO
                )
            }
        }
    }

    // 2. أعلى جهة صرف
    context.topMerchants.firstOrNull()?.let { (merchant, total) ->
        if (total > 0L) {
            insights += Insight(
                key = "top_merchant_${merchant}_$month",
                text = "صرفت ${formatMinor(total)} عند \"$merchant\" الشهر ده — أعلى جهة صرف عندك.",
                level = InsightLevel.INFO
            )
        }
    }

    // 3. ميزانيات الفئات اللي قربت أو تخطت
    context.categoryBudgetProgress.take(3).forEach { (name, progress) ->
        when (progress.state) {
            BudgetState.EXCEEDED -> insights += Insight(
                key = "budget_over_${name}_$month",
                text = "تخطيت ميزانية \"$name\": صرفت ${formatMinor(progress.spentMinor)} من " +
                    "${formatMinor(progress.limitMinor)}.",
                level = InsightLevel.ALERT
            )
            BudgetState.WARNING -> insights += Insight(
                key = "budget_warn_${name}_$month",
                text = "استخدمت ${progress.percentUsed}% من ميزانية \"$name\" وفاضل " +
                    "${context.forecast.daysRemaining} يوم في الشهر.",
                level = InsightLevel.WARNING
            )
            BudgetState.SAFE -> Unit
        }
    }

    // 4. توقّع تخطي الميزانية الكلية
    val over = context.forecast.projectedOverBudgetMinor
    if (over != null && over > 0L) {
        insights += Insight(
            key = "forecast_over_$month",
            text = "بمعدل صرفك الحالي، متوقّع تتخطى ميزانية الشهر بحوالي ${formatMinor(over)} " +
                "(المتوقّع ${formatMinor(context.forecast.projectedMinor)}).",
            level = InsightLevel.ALERT
        )
    } else if (context.forecast.projectedMinor > 0L) {
        insights += Insight(
            key = "forecast_$month",
            text = "متوقّع تصرف ${formatMinor(context.forecast.projectedMinor)} بنهاية الشهر " +
                "(متوسط ${formatMinor(context.forecast.dailyAverageMinor)} يوميًا).",
            level = InsightLevel.INFO
        )
    }

    // 5. تغيّر المتوسط اليومي
    val prevDaily = context.previousDailyAverageMinor
    val curDaily = context.forecast.dailyAverageMinor
    val dailyChange = percentChange(prevDaily, curDaily)
    if (dailyChange != null && kotlin.math.abs(dailyChange) >= minChangePercent) {
        val direction = if (dailyChange > 0) "زاد" else "قل"
        insights += Insight(
            key = "daily_avg_$month",
            text = "متوسط صرفك اليومي $direction من ${formatMinor(prevDaily)} إلى ${formatMinor(curDaily)}.",
            level = if (dailyChange > 0) InsightLevel.WARNING else InsightLevel.INFO
        )
    }

    // 6. صافي التدفق النقدي لما يكون فيه دخل مسجّل
    if (context.summary.incomeMinor > 0L) {
        val net = context.summary.netCashFlowMinor
        insights += Insight(
            key = "net_flow_$month",
            text = if (net >= 0L) {
                "صافي التدفق النقدي الشهر ده +${formatMinor(net)} (دخل ${formatMinor(context.summary.incomeMinor)})."
            } else {
                "صرفت أكتر من دخلك الشهر ده بـ ${formatMinor(-net)}."
            },
            level = if (net >= 0L) InsightLevel.INFO else InsightLevel.ALERT
        )
    }

    // 7. حِمل الاشتراكات والأقساط الشهري
    val fixed = context.subscriptionsMonthlyMinor + context.installmentsMonthlyMinor
    if (fixed > 0L) {
        insights += Insight(
            key = "fixed_load_$month",
            text = "التزاماتك الشهرية الثابتة (اشتراكات + أقساط) ${formatMinor(fixed)}.",
            level = InsightLevel.INFO
        )
    }

    return insights
}
