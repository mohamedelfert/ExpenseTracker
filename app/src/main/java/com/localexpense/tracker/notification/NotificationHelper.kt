package com.localexpense.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.localexpense.tracker.R
import com.localexpense.tracker.money.formatMinor

/**
 * نقطة موحّدة لكل إشعارات التطبيق (بدل ما كل مكان يعرّف الـ channel بتاعه
 * لوحده زي ما كان الحال قبل كده في SmsReceiver). بيغطي نوعين:
 * 1. إشعار "تم تسجيل مصروف جديد" - بيظهر فور التقاط عملية بنكية.
 * 2. إشعار "تنبيه ميزانية" - بيظهر لما فئة معينة تقرب أو تتخطى حد الميزانية.
 *
 * كله محلي، بيستخدم NotificationManager العادي بتاع أندرويد، ومفيش أي اتصال
 * بأي سيرفر أو خدمة خارجية.
 */
object NotificationHelper {

    const val CHANNEL_EXPENSE_CAPTURED = "expense_tracker_channel"
    const val CHANNEL_BUDGET_ALERT = "budget_alert_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXPENSE_CAPTURED,
                "إشعارات المصروفات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه فوري لما التطبيق يسجّل عملية بنكية جديدة تلقائيًا"
            }
        )

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET_ALERT,
                "تنبيهات الميزانية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه لما مصروفات فئة معينة تقرب أو تتخطى الميزانية المحددة لها"
            }
        )
    }

    fun showExpenseCapturedNotification(
        context: Context,
        amountMinor: Long,
        merchant: String,
        bankName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_EXPENSE_CAPTURED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تم تسجيل مصروف جديد 💳")
            .setContentText("خصم ${formatMinor(amountMinor)} - $merchant ($bankName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showBudgetAlertNotification(
        context: Context,
        label: String,
        spentMinor: Long,
        limitMinor: Long,
        exceeded: Boolean,
        percentUsed: Int,
        daysRemaining: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = if (exceeded) "⚠️ تخطيت \"$label\"" else "🔔 قربت من \"$label\""
        val text = if (exceeded) {
            "صرفت ${formatMinor(spentMinor)} من أصل ${formatMinor(limitMinor)} وفاضل $daysRemaining يوم في الشهر"
        } else {
            "استخدمت $percentUsed% (${formatMinor(spentMinor)} من ${formatMinor(limitMinor)}) وفاضل $daysRemaining يوم"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // نفس الـ id لكل فئة عشان لو ظهر تنبيه "قربت" وبعدين "تخطيت" في نفس
        // الشهر، الثاني يستبدل الأول بدل ما يتراكموا إشعارات كتير.
        NotificationManagerCompat.from(context).notify(
            "budget_$label".hashCode(),
            notification
        )
    }

    /**
     * تنبيه استباقي: بمعدل الصرف الحالي، الميزانية دي هتتخطى قبل نهاية الشهر
     * (المرحلة 7، بند 24). الرقم المعروض حساب بسيط: المتوسط اليومي × أيام
     * الشهر — مش تنبؤ ذكي، وينفع المستخدم يعمله بنفسه على ورقة.
     */
    fun showForecastAlertNotification(
        context: Context,
        label: String,
        projectedMinor: Long,
        limitMinor: Long,
        overMinor: Long,
        daysRemaining: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📈 متوقّع تتخطى \"$label\"")
            .setContentText(
                "بمعدل صرفك الحالي المتوقّع ${formatMinor(projectedMinor)} مقابل حد " +
                    "${formatMinor(limitMinor)} — زيادة ${formatMinor(overMinor)} وفاضل $daysRemaining يوم"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("forecast_$label".hashCode(), notification)
    }

    /**
     * تنبيه بدفعة قادمة (اشتراك/دورية/قسط).
     *
     * بيتنادى من الفحص اليومي في PaymentReminderReceiver (AlarmManager)، فبيوصل
     * كمان والتطبيق مقفول.
     */
    fun showUpcomingPaymentNotification(
        context: Context,
        name: String,
        amountMinor: Long,
        daysUntil: Int
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val when_ = when (daysUntil) {
            0 -> "النهاردة"
            1 -> "بكرة"
            else -> "بعد $daysUntil يوم"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📅 دفعة قادمة: $name")
            .setContentText("${formatMinor(amountMinor)} مستحقة $when_")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify("upcoming_$name".hashCode(), notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        // على أندرويد 13+ لازم إذن POST_NOTIFICATIONS وقت التشغيل. لو مرفوض،
        // بنتجاهل الإشعار بأمان بدل ما نعمل crash.
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
