package com.localexpense.tracker.notification

import android.content.Context
import com.localexpense.tracker.data.Budget
import com.localexpense.tracker.data.BudgetDao
import com.localexpense.tracker.data.ExpenseDao
import com.localexpense.tracker.domain.BudgetState
import com.localexpense.tracker.domain.budgetProgress
import com.localexpense.tracker.domain.forecast
import com.localexpense.tracker.util.dayOfMonth
import com.localexpense.tracker.util.daysInMonth
import com.localexpense.tracker.util.monthKey
import com.localexpense.tracker.util.monthRange

/**
 * بيتفحص بعد كل حركة جديدة: هل الفئة دي — أو الميزانية الكلية — قربت أو
 * تخطّت الحد المحدد الشهر ده؟ ولو المعدل الحالي بيوصل لتخطي الميزانية بنهاية
 * الشهر، بيطلع تنبيه استباقي (المرحلة 7، بند 24).
 *
 * كل مستوى تنبيه بيظهر مرة واحدة بس لكل فئة لكل شهر (متخزّن في
 * SharedPreferences) — عشان المستخدم ميتقصفش بإشعارات مكررة مع كل عملية.
 *
 * كله على الجهاز: قراءة من Room + prefs، مفيش أي اتصال بالإنترنت.
 */
object BudgetAlertChecker {

    private const val PREFS_NAME = "budget_alert_state"

    suspend fun checkAndNotify(
        context: Context,
        expenseDao: ExpenseDao,
        budgetDao: BudgetDao,
        categoryName: String,
        transactionTimestamp: Long
    ) {
        val range = monthRange(transactionTimestamp)
        val monthKey = monthKey(transactionTimestamp)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. ميزانية الفئة
        budgetDao.getBudget(categoryName)?.takeIf { it.limitMinor > 0L }?.let { budget ->
            val spent = expenseDao.getCategoryTotalBetween(categoryName, range.start, range.end) ?: 0L
            notifyIfNeeded(context, prefs, monthKey, categoryName, spent, budget.limitMinor, transactionTimestamp)
        }

        // 2. الميزانية الكلية للشهر
        budgetDao.getBudget(Budget.OVERALL_KEY)?.takeIf { it.limitMinor > 0L }?.let { budget ->
            val spent = expenseDao.getTotalBetween(range.start, range.end) ?: 0L
            notifyIfNeeded(context, prefs, monthKey, OVERALL_LABEL, spent, budget.limitMinor, transactionTimestamp)
        }
    }

    private const val OVERALL_LABEL = "الميزانية الكلية"

    private fun notifyIfNeeded(
        context: Context,
        prefs: android.content.SharedPreferences,
        monthKey: String,
        label: String,
        spentMinor: Long,
        limitMinor: Long,
        timestamp: Long
    ) {
        val progress = budgetProgress(spentMinor, limitMinor)
        val daysRemaining = forecast(
            netSpentMinor = spentMinor,
            daysElapsed = dayOfMonth(timestamp),
            daysInMonth = daysInMonth(timestamp),
            budgetLimitMinor = limitMinor
        ).daysRemaining

        val level = when (progress.state) {
            BudgetState.EXCEEDED -> "exceeded"
            BudgetState.WARNING -> "warning"
            BudgetState.SAFE -> return
        }

        val prefKey = "${level}_${label}_$monthKey"
        if (prefs.getBoolean(prefKey, false)) return

        NotificationHelper.showBudgetAlertNotification(
            context = context,
            label = label,
            spentMinor = spentMinor,
            limitMinor = limitMinor,
            exceeded = progress.state == BudgetState.EXCEEDED,
            percentUsed = progress.percentUsed,
            daysRemaining = daysRemaining
        )
        prefs.edit().putBoolean(prefKey, true).apply()
    }
}
