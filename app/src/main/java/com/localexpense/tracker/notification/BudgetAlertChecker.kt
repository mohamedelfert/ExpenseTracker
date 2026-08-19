package com.localexpense.tracker.notification

import android.content.Context
import com.localexpense.tracker.data.BudgetDao
import com.localexpense.tracker.data.ExpenseDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * بيتفحص بعد كل مصروف جديد: هل الفئة دي وصلت أو تخطّت الميزانية المحددة
 * ليها الشهر ده؟ لو آه، بيطلع إشعار محلي واحد بس لكل مستوى (80% و 100%)
 * لكل فئة لكل شهر - عشان المستخدم ميتقصفش بإشعارات مكررة مع كل عملية.
 *
 * كله بيحصل على الجهاز نفسه (قراءة من Room + SharedPreferences بسيطة لتتبع
 * الإشعارات اللي اتبعتت قبل كده) - مفيش أي اتصال بالإنترنت.
 */
object BudgetAlertChecker {

    private const val PREFS_NAME = "budget_alert_state"
    private const val WARNING_THRESHOLD = 0.8

    suspend fun checkAndNotify(
        context: Context,
        expenseDao: ExpenseDao,
        budgetDao: BudgetDao,
        categoryName: String,
        transactionTimestamp: Long
    ) {
        val budget = budgetDao.getBudget(categoryName) ?: return
        if (budget.limitAmount <= 0.0) return

        val (monthStart, monthEnd) = monthRange(transactionTimestamp)
        val spent = expenseDao.getCategoryTotalBetween(categoryName, monthStart, monthEnd) ?: 0.0
        val ratio = spent / budget.limitAmount

        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date(transactionTimestamp))
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when {
            ratio >= 1.0 -> {
                val prefKey = "exceeded_${categoryName}_$monthKey"
                if (!prefs.getBoolean(prefKey, false)) {
                    NotificationHelper.showBudgetAlertNotification(
                        context, categoryName, spent, budget.limitAmount, exceeded = true
                    )
                    prefs.edit().putBoolean(prefKey, true).apply()
                }
            }
            ratio >= WARNING_THRESHOLD -> {
                val prefKey = "warning_${categoryName}_$monthKey"
                if (!prefs.getBoolean(prefKey, false)) {
                    NotificationHelper.showBudgetAlertNotification(
                        context, categoryName, spent, budget.limitAmount, exceeded = false
                    )
                    prefs.edit().putBoolean(prefKey, true).apply()
                }
            }
        }
    }

    private fun monthRange(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.timeInMillis - 1
        return start to end
    }
}
