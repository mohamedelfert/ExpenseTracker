package com.localexpense.tracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * تنبيهات الدفعات القادمة في الخلفية (المرحلة 13: "Reminder").
 *
 * AlarmManager مش WorkManager: التنبيه ده فحص يومي واحد لقراءة صفوف من قاعدة
 * بيانات محلية، و AlarmManager موجود في النظام نفسه — مفيش داعي لمكتبة زيادة
 * علشان مهمة واحدة بسيطة.
 *
 * ponytail: setInexactRepeating (النظام بيجمّع التنبيهات لتوفير البطارية)،
 * فالتنبيه ممكن يتأخر شوية عن 9 صباحًا. لو التوقيت لازم يبقى مضبوط بالدقيقة،
 * الترقية هي setExactAndAllowWhileIdle مع إذن SCHEDULE_EXACT_ALARM.
 */
object PaymentReminderScheduler {

    private const val REQUEST_CODE = 8801
    private const val HOUR_OF_DAY = 9

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, PaymentReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
