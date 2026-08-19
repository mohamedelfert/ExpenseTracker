package com.localexpense.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.localexpense.tracker.R

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
        amount: Double,
        merchant: String,
        bankName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_EXPENSE_CAPTURED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تم تسجيل مصروف جديد 💳")
            .setContentText("خصم $amount ج.م - $merchant ($bankName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showBudgetAlertNotification(
        context: Context,
        categoryName: String,
        spent: Double,
        limit: Double,
        exceeded: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = if (exceeded) "⚠️ تخطيت ميزانية \"$categoryName\"" else "🔔 قربت من ميزانية \"$categoryName\""
        val text = if (exceeded) {
            "صرفت %.2f ج.م من أصل %.2f ج.م المحددة الشهر ده".format(spent, limit)
        } else {
            "صرفت %.2f ج.م من أصل %.2f ج.م (أكتر من 80%%)".format(spent, limit)
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
            "budget_$categoryName".hashCode(),
            notification
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        // على أندرويد 13+ لازم إذن POST_NOTIFICATIONS وقت التشغيل. لو مرفوض،
        // بنتجاهل الإشعار بأمان بدل ما نعمل crash.
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
