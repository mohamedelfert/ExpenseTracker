package com.localexpense.tracker.domain

import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.parseAmountMinor

/**
 * "اسأل عن مصروفاتك" (المرحلة 19) — مساعد **محلي بالكامل**: مفيش نموذج،
 * مفيش إنترنت، مفيش مفتاح API، ومفيش أي بيانات بتسيب الجهاز.
 *
 * الأهم معماريًا: الدالة دي مبتحسبش أي رقم مالي. كل الأرقام جاهزة جوه
 * [FinancialContext] (محسوبة بتجميعات SQL في محرّك التحليلات)، والمساعد
 * وظيفته يفهم السؤال ويختار الرقم الصح ويصيغه جملة. ده بالظبط شرط الـ spec:
 * "الـ AI مسؤول عن الشرح، ومحرّك التحليلات مسؤول عن الحساب".
 *
 * التعرّف على السؤال بالكلمات المفتاحية (عربي + إنجليزي). لو مش فاهم، بيرجّع
 * ملخص الشهر بدل ما يخمّن.
 */
object Assistant {

    val sampleQuestions = listOf(
        "صرفت كام على الأكل الشهر ده؟",
        "إيه اللي زاد عن الشهر اللي فات؟",
        "أكبر مصروفاتي فين؟",
        "أقدر أصرف 3000 كمان الشهر ده؟",
        "متوقّع أصرف كام بنهاية الشهر؟",
        "إيه صافي دخلي الشهر ده؟"
    )

    fun answer(question: String, context: FinancialContext): String {
        val q = normalizeMerchant(question)   // نفس التطبيع: حروف بس، من غير تشكيل
        val raw = question.lowercase()

        // 1. سؤال عن فئة بالاسم: "صرفت كام على المطاعم؟"
        val category = context.categoryTotals.keys.firstOrNull { name ->
            val normalized = normalizeMerchant(name)
            normalized.isNotEmpty() && q.contains(normalized)
        }
        if (category != null) {
            val total = context.categoryTotals[category] ?: 0L
            val previous = context.previousCategoryTotals[category] ?: 0L
            val change = percentChange(previous, total)
            val budget = context.categoryBudget(category)
            return buildString {
                append("صرفت ${formatMinor(total)} على \"$category\" في ${context.monthLabel}.")
                if (change != null) {
                    val dir = if (change >= 0) "أكتر" else "أقل"
                    append(" ده $dir من الشهر اللي فات بـ ${kotlin.math.abs(change).toInt()}% ")
                    append("(${formatMinor(previous)}).")
                }
                if (budget.limitMinor > 0L) {
                    append(" استخدمت ${budget.percentUsed}% من ميزانية الفئة (${formatMinor(budget.limitMinor)}).")
                }
            }
        }

        // 2. المقارنة بالشهر السابق
        if (q.contains("زاد") || q.contains("قل") || q.contains("مقارنه") || q.contains("الشهر اللي فات") ||
            raw.contains("compare") || raw.contains("increase")
        ) {
            if (!context.comparison.hasPrevious) return "مفيش بيانات للشهر اللي فات عشان نقارن بيها."
            val up = context.comparison.biggestIncrease
            val down = context.comparison.biggestDecrease
            return buildString {
                append("إجمالي صرفك ${formatMinor(context.summary.netSpentMinor)} مقابل ")
                append("${formatMinor(context.previousNetSpentMinor)} الشهر اللي فات.")
                if (up != null) append(" أكبر زيادة: \"${up.categoryName}\" (+${formatMinor(up.deltaMinor)}).")
                if (down != null) append(" أكبر نقصان: \"${down.categoryName}\" (${formatMinor(down.deltaMinor)}).")
            }
        }

        // 3. أكبر المصروفات / أعلى الجهات
        if (q.contains("اكبر") || q.contains("اعلي") || q.contains("جهه") || q.contains("تاجر") ||
            raw.contains("biggest") || raw.contains("merchant") || raw.contains("most")
        ) {
            if (context.topMerchants.isEmpty()) return "مفيش حركات مسجلة الشهر ده."
            return "أعلى جهات الصرف في ${context.monthLabel}: " +
                context.topMerchants.take(5)
                    .joinToString("، ") { (name, total) -> "$name (${formatMinor(total)})" } + "."
        }

        // 4. "أقدر أصرف كام كمان؟" — سؤال قدرة، محتاج مبلغ أو ميزانية
        if (q.contains("اقدر") || q.contains("اصرف كمان") || raw.contains("afford") || q.contains("متبقي")) {
            val asked = parseAmountMinor(question.filter { it.isDigit() || it == '.' })
            val budget = context.overallBudget
            if (budget.limitMinor <= 0L) {
                return "مفيش ميزانية شهرية كلية محددة، فمش بنقدر نقول \"تقدر\" أو \"لأ\". " +
                    "صرفك الحالي ${formatMinor(context.summary.netSpentMinor)} " +
                    "والمتوقّع بنهاية الشهر ${formatMinor(context.forecast.projectedMinor)}."
            }
            val remaining = budget.remainingMinor
            return if (asked != null && asked > 0L) {
                if (asked <= remaining) {
                    "أيوه. فاضل من ميزانية الشهر ${formatMinor(remaining)}، فمبلغ ${formatMinor(asked)} " +
                        "بيسيب ${formatMinor(remaining - asked)}."
                } else {
                    "لأ. فاضل ${formatMinor(remaining)} بس، فـ ${formatMinor(asked)} هيخلّيك تتخطى " +
                        "الميزانية بـ ${formatMinor(asked - remaining)}."
                }
            } else {
                "فاضل من ميزانية الشهر ${formatMinor(remaining)} من إجمالي ${formatMinor(budget.limitMinor)}."
            }
        }

        // 5. التوقّع
        if (q.contains("متوقع") || q.contains("توقع") || q.contains("بنهايه الشهر") || raw.contains("forecast")) {
            val f = context.forecast
            return buildString {
                append("مر ${f.daysElapsed} يوم وفاضل ${f.daysRemaining}. ")
                append("صرفت ${formatMinor(f.netSpentMinor)} بمتوسط ${formatMinor(f.dailyAverageMinor)} يوميًا، ")
                append("فالمتوقّع بنهاية الشهر ${formatMinor(f.projectedMinor)}.")
                f.projectedOverBudgetMinor?.takeIf { it > 0L }?.let {
                    append(" ده أعلى من ميزانيتك بـ ${formatMinor(it)}.")
                }
            }
        }

        // 6. الدخل والصافي
        if (q.contains("دخل") || q.contains("صافي") || raw.contains("income") || raw.contains("cash flow")) {
            val s = context.summary
            return "في ${context.monthLabel}: دخل ${formatMinor(s.incomeMinor)}، " +
                "مصروفات ${formatMinor(s.expenseMinor)}، استرداد ${formatMinor(s.refundMinor)}، " +
                "والصافي ${formatMinor(s.netCashFlowMinor)}. (التحويلات مش محسوبة في الصافي.)"
        }

        // 7. الاشتراكات والأقساط
        if (q.contains("اشتراك") || q.contains("قسط") || q.contains("اقساط") || raw.contains("subscription")) {
            return "التزاماتك الشهرية الثابتة: اشتراكات ${formatMinor(context.subscriptionsMonthlyMinor)} " +
                "وأقساط ${formatMinor(context.installmentsMonthlyMinor)}."
        }

        // 8. مفهمتش السؤال — بنرجّع ملخص بدل ما نخمّن
        val s = context.summary
        return "مش متأكد من السؤال. ملخص ${context.monthLabel}: صرف صافي " +
            "${formatMinor(s.netSpentMinor)}، دخل ${formatMinor(s.incomeMinor)}، " +
            "متوقّع بنهاية الشهر ${formatMinor(context.forecast.projectedMinor)}. " +
            "جرّب تسأل عن فئة بالاسم، أو عن التوقّع، أو أعلى جهات الصرف."
    }
}
