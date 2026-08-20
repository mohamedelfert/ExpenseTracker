package com.localexpense.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.notification.NotificationHelper
import com.localexpense.tracker.notification.PaymentReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * الفحص اليومي للدفعات القادمة (اشتراكات، دوريات، أقساط). بيشتغل من غير ما
 * التطبيق يكون مفتوح — راجع [PaymentReminderScheduler].
 *
 * كل دفعة بتتنبّه مرة واحدة بس لكل استحقاق (المفتاح متخزّن في prefs)، وبنراعي
 * [reminderDaysBefore] المحدد لكل دفعة.
 */
class PaymentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // إعادة الجدولة بعد إعادة تشغيل الجهاز (الإنذارات بتتشال عند الـ boot).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PaymentReminderScheduler.schedule(context)
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val dayMillis = 24L * 60 * 60 * 1000

                val repository = ExpenseRepository(appContext)
                for (payment in repository.upcomingPayments(withinDays = 30, now = now)) {
                    val daysUntil = ((payment.dueDate - now) / dayMillis).toInt()
                    if (daysUntil < 0) continue

                    val threshold = repository.reminderDaysBefore(payment.name)
                    if (daysUntil > threshold) continue

                    val key = "reminded_${payment.name}_${payment.dueDate}"
                    if (prefs.getBoolean(key, false)) continue

                    NotificationHelper.showUpcomingPaymentNotification(
                        appContext, payment.name, payment.amountMinor, daysUntil
                    )
                    prefs.edit().putBoolean(key, true).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "payment_reminders"
    }
}
