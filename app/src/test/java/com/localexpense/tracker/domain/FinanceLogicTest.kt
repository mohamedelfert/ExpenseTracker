package com.localexpense.tracker.domain

import com.localexpense.tracker.data.Frequency
import com.localexpense.tracker.data.MerchantRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات المنطق المالي (المطلوبة في الـ spec). كلها JVM خالص — مفيش أي
 * تبعية على أندرويد، لأن كل الحساب في domain/ دوال صافية.
 */
class FinanceLogicTest {

    // ===== أنواع الحركات وصافي التدفق (المراحل 1 و 6) =====

    @Test
    fun `net cash flow excludes transfers and adds refunds`() {
        val summary = MonthSummary(
            incomeMinor = 2_500_000,   // 25,000.00
            expenseMinor = 1_800_000,  // 18,000.00
            refundMinor = 50_000,      // 500.00
            transferMinor = 999_999    // لازم متأثرش على أي حساب
        )
        assertEquals(750_000, summary.netCashFlowMinor)   // 25000 - 18000 + 500
        assertEquals(1_750_000, summary.netSpentMinor)    // 18000 - 500
        assertEquals(750_000, summary.remainingMinor)
    }

    @Test
    fun `spending only month gives negative net flow`() {
        val summary = MonthSummary(expenseMinor = 100_000)
        assertEquals(-100_000, summary.netCashFlowMinor)
    }

    // ===== الميزانية: 0% / 80% / 100% / أكتر (المرحلة 7) =====

    @Test
    fun `budget states at thresholds`() {
        assertEquals(BudgetState.SAFE, budgetProgress(0, 100_000).state)
        assertEquals(BudgetState.SAFE, budgetProgress(79_999, 100_000).state)
        assertEquals(BudgetState.WARNING, budgetProgress(80_000, 100_000).state)
        assertEquals(BudgetState.EXCEEDED, budgetProgress(100_000, 100_000).state)
        assertEquals(BudgetState.EXCEEDED, budgetProgress(150_000, 100_000).state)
        assertEquals(50, budgetProgress(50_000, 100_000).percentUsed)
        assertEquals(-50_000, budgetProgress(150_000, 100_000).remainingMinor)
    }

    @Test
    fun `no budget set never divides by zero`() {
        val progress = budgetProgress(50_000, 0)
        assertEquals(BudgetState.SAFE, progress.state)
        assertEquals(0.0, progress.ratio, 0.0)
    }

    // ===== التوقّع (المرحلة 10) =====

    @Test
    fun `forecast projects from daily average`() {
        // صرف 12,000 في 20 يوم من شهر 30 يوم = 600 يوميًا = 18,000 متوقّع
        val result = forecast(netSpentMinor = 1_200_000, daysElapsed = 20, daysInMonth = 30)
        assertEquals(60_000, result.dailyAverageMinor)
        assertEquals(1_800_000, result.projectedMinor)
        assertEquals(10, result.daysRemaining)
        assertNull(result.projectedOverBudgetMinor)
    }

    @Test
    fun `forecast flags budget overrun`() {
        val result = forecast(1_200_000, 20, 30, budgetLimitMinor = 1_680_000)
        assertEquals(120_000L, result.projectedOverBudgetMinor)   // 18,000 - 16,800
    }

    @Test
    fun `forecast handles full month and zero spending`() {
        val full = forecast(900_000, 30, 30)
        assertEquals(0, full.daysRemaining)
        assertEquals(900_000, full.projectedMinor)

        val zero = forecast(0, 5, 30)
        assertEquals(0, zero.dailyAverageMinor)
        assertEquals(0, zero.projectedMinor)
    }

    @Test
    fun `forecast clamps impossible day counts`() {
        // يوم 0 أو يوم أكبر من الشهر ما يعملوش قسمة على صفر ولا نتيجة سلبية
        assertEquals(100_000, forecast(100_000, 0, 30).dailyAverageMinor)
        assertEquals(0, forecast(100_000, 45, 30).daysRemaining)
    }

    // ===== المقارنة الشهرية (المرحلة 9) =====

    @Test
    fun `comparison reports increase decrease and new categories`() {
        val previous = mapOf("مطاعم" to 420_000L, "مواصلات" to 210_000L, "تسوق" to 100_000L)
        val current = mapOf("مطاعم" to 510_000L, "مواصلات" to 170_000L, "صحة" to 80_000L)
        val comparison = compareMonths(previous, current)

        val food = comparison.changes.first { it.categoryName == "مطاعم" }
        assertEquals(90_000, food.deltaMinor)
        assertEquals(21, food.changePercent!!.toInt())

        val transport = comparison.changes.first { it.categoryName == "مواصلات" }
        assertEquals(-19, transport.changePercent!!.toInt())

        val health = comparison.changes.first { it.categoryName == "صحة" }
        assertTrue(health.isNew)
        assertNull(health.changePercent)              // مفيش أساس للمقارنة

        assertEquals("مطاعم", comparison.biggestIncrease?.categoryName)
        // تسوق اختفت الشهر ده (100,000 -> 0) فهي أكبر نقصان
        assertEquals("تسوق", comparison.biggestDecrease?.categoryName)
    }

    @Test
    fun `comparison with no previous month`() {
        val comparison = compareMonths(emptyMap(), mapOf("عام" to 50_000L))
        assertFalse(comparison.hasPrevious)
        assertNull(comparison.totalChangePercent)
    }

    @Test
    fun `percent change from zero is unknown not infinite`() {
        assertNull(percentChange(0, 1000))
        assertEquals(-100.0, percentChange(1000, 0)!!, 0.001)
    }

    // ===== كشف الحركات الشاذة (المرحلة 11) =====

    @Test
    fun `anomaly needs both a high amount and enough history`() {
        // 1,500 مقابل متوسط 120 و 10 عمليات = شاذة
        assertTrue(isAnomalous(150_000, 12_000.0, 10))
        // نفس المبلغ بس عمليتين تاريخ بس = مش شاذة (إنذار كذّاب)
        assertFalse(isAnomalous(150_000, 12_000.0, 2))
        // مبلغ عادي
        assertFalse(isAnomalous(15_000, 12_000.0, 10))
        // مفيش متوسط
        assertFalse(isAnomalous(150_000, null, 10))
    }

    // ===== محرّك التصنيف (المرحلة 5) =====

    @Test
    fun `categorizer follows a fixed priority`() {
        val rules = listOf(
            MerchantRule(id = 1, pattern = "talabat", categoryName = "مطاعم", priority = 10),
            MerchantRule(id = 2, pattern = "talabat", categoryName = "توصيل", priority = 50)
        )

        // الربط الصريح للجهة بيكسب أي قاعدة
        assertEquals(
            CategorySource.MERCHANT_MAPPING,
            categorize("TALABAT", rules, explicitMerchantCategory = "أكل").source
        )

        // الأولوية الأعلى بتكسب
        val byRule = categorize("TALABAT*CAIRO", rules)
        assertEquals("توصيل", byRule.categoryName)
        assertEquals(CategorySource.MERCHANT_RULE, byRule.source)

        // القاعدة المعطّلة بتتجاهل
        val disabled = rules.map { it.copy(isEnabled = false) }
        assertEquals(CategorySource.KEYWORD, categorize("TALABAT", disabled, keywordCategory = "مطاعم").source)

        // مفيش أي مطابقة = الفئة الافتراضية
        assertEquals(DEFAULT_CATEGORY, categorize("جهة مجهولة", emptyList()).categoryName)
    }

    @Test
    fun `merchant normalizer collapses variants`() {
        assertEquals(normalizeMerchant("Talabat"), normalizeMerchant("TALABAT*1234"))
        assertEquals(normalizeMerchant("طلبات"), normalizeMerchant("  طلبات.  "))
        assertEquals("كارفور", normalizeMerchant("كَارفور"))
    }

    // ===== التكرار (المراحل 12 و 13) =====

    @Test
    fun `monthly equivalent converts frequencies`() {
        assertEquals(35_000, monthlyEquivalentMinor(35_000, Frequency.MONTHLY))
        assertEquals(120_000, monthlyEquivalentMinor(1_440_000, Frequency.YEARLY))
        assertEquals(300_000, monthlyEquivalentMinor(10_000, Frequency.DAILY))
        assertEquals(30_000, monthlyEquivalentMinor(10_000, Frequency.CUSTOM, intervalDays = 10))
    }

    @Test
    fun `next due date advances by frequency`() {
        val start = 1_700_000_000_000L
        // مقارنات ترتيب مش فروق بالملي ثانية: التوقيت الصيفي بيخلي "أسبوع"
        // 167 أو 169 ساعة أحيانًا، والاختبار مش المفروض يفشل بسبب كده.
        assertTrue(nextDueDate(start, Frequency.DAILY) > start)
        assertTrue(nextDueDate(start, Frequency.WEEKLY) > nextDueDate(start, Frequency.DAILY))
        assertTrue(nextDueDate(start, Frequency.YEARLY) > nextDueDate(start, Frequency.MONTHLY))
        assertTrue(nextDueDate(start, Frequency.CUSTOM, 5) > nextDueDate(start, Frequency.DAILY))
    }

    // ===== المساعد المحلي (المرحلة 19) =====

    @Test
    fun `assistant answers from precomputed numbers only`() {
        val context = FinancialContext(
            monthLabel = "أغسطس 2026",
            summary = MonthSummary(incomeMinor = 2_500_000, expenseMinor = 1_800_000),
            categoryTotals = mapOf("مطاعم" to 510_000L),
            previousCategoryTotals = mapOf("مطاعم" to 420_000L),
            topMerchants = listOf("طلبات" to 185_000L),
            forecast = forecast(1_800_000, 20, 30, 2_000_000),
            overallBudgetMinor = 2_000_000
        )

        assertTrue(Assistant.answer("صرفت كام على مطاعم؟", context).contains("مطاعم"))
        assertTrue(Assistant.answer("أكبر مصروفاتي فين؟", context).contains("طلبات"))
        assertTrue(Assistant.answer("إيه صافي دخلي؟", context).contains("دخل"))
        // سؤال مش مفهوم بيرجّع ملخص، مش تخمين
        assertTrue(Assistant.answer("بلابلا", context).contains("ملخص"))
    }

    @Test
    fun `insights are generated from real data only`() {
        val context = FinancialContext(
            monthLabel = "أغسطس 2026",
            summary = MonthSummary(expenseMinor = 1_800_000),
            categoryTotals = mapOf("مطاعم" to 510_000L),
            previousCategoryTotals = mapOf("مطاعم" to 300_000L),
            forecast = forecast(1_800_000, 20, 30, 1_500_000),
            overallBudgetMinor = 1_500_000,
            categoryBudgets = mapOf("مطاعم" to 500_000L)
        )
        val insights = generateInsights(context)

        assertTrue(insights.any { it.key.startsWith("cat_up_") })
        assertTrue(insights.any { it.level == InsightLevel.ALERT })
        // مفيش رؤية مكررة بنفس المفتاح
        assertEquals(insights.map { it.key }.distinct().size, insights.size)
    }
}
