package com.localexpense.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.localexpense.tracker.R
import com.localexpense.tracker.money.formatMinor

/**
 * نقطة موحّدة لكل إشعارات التطبيق (بدل ما كل مكان يعرّف الـ channel بتاعه
 * لوحده زي ما كان الحال قبل كده في SmsReceiver). بيغطي الأنواع دي:
 * 1. إشعار "تم تسجيل مصروف جديد" - بيظهر فور التقاط عملية بنكية.
 * 2. إشعار "تنبيه ميزانية" - بيظهر لما فئة معينة تقرب أو تتخطى حد الميزانية.
 * 3. إشعار "مصدر جديد" - بيظهر لما رسالة تتقرا من مرسل مش في سجل المصادر
 *    المعروف ولا في قواعد المستخدم، عشان يقدر يضيف قاعدة له بنفسه.
 *
 * كله محلي، بيستخدم NotificationManager العادي بتاع أندرويد، ومفيش أي اتصال
 * بأي سيرفر أو خدمة خارجية.
 */
object NotificationHelper {

    const val CHANNEL_EXPENSE_CAPTURED = "expense_tracker_channel"
    const val CHANNEL_BUDGET_ALERT = "budget_alert_channel"
    const val CHANNEL_UNKNOWN_SOURCE = "unknown_source_channel"

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

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UNKNOWN_SOURCE,
                "مصادر جديدة",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه لما رسالة توصل من بنك أو محفظة مش في قايمة المصادر المعروفة"
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

    /**
     * تنبيه "لقينا مصدر جديد": رسالة اتسجلت لكن مرسلها مش في سجل المصادر
     * المعروف (TransactionSources) ولا في قواعد المستخدم المفعّلة. الهدف
     * إن المستخدم يعرف إن دقة القراءة لبنك ده مش مضمونة، ويقدر يفتح "قواعد
     * الرسائل" ويضيف قاعدة له بنفسه لو حابب.
     *
     * بينادى مرة واحدة بس لكل مرسل (شوف UnknownSourceTracker) عشان ميتكررش
     * الإشعار مع كل رسالة جديدة من نفس البنك غير المتعرّف عليه.
     */
    fun showUnknownSourceNotification(context: Context, sender: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                context,
                sender.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_UNKNOWN_SOURCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("لقينا مصدر جديد 🏦")
            .setContentText("رسالة من \"$sender\" اتسجّلت لكن مش من بنك متعرّف عليه رسميًا")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "رسالة من \"$sender\" اتسجّلت لكن مش من بنك متعرّف عليه رسميًا، فالمبلغ " +
                        "أو اسم الجهة ممكن يكونوا مش دقيقين. افتح \"قواعد الرسائل\" من الإعدادات " +
                        "وضيفله قاعدة عشان الدقة تبقى أعلى في المرات الجاية."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        pendingIntent?.let { builder.setContentIntent(it) }

        NotificationManagerCompat.from(context).notify(
            ("unknown_source_$sender").hashCode(),
            builder.build()
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        // على أندرويد 13+ لازم إذن POST_NOTIFICATIONS وقت التشغيل. لو مرفوض،
        // بنتجاهل الإشعار بأمان بدل ما نعمل crash.
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}