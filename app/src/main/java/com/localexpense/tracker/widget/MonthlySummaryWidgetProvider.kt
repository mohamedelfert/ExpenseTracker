package com.localexpense.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.localexpense.tracker.MainActivity
import com.localexpense.tracker.R
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.CrashLog
import com.localexpense.tracker.util.monthLabel
import com.localexpense.tracker.util.monthRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ويدجت الشاشة الرئيسية: إجمالي مصروف الشهر الحالي.
 *
 * بيقرا من نفس قاعدة البيانات المشفّرة مباشرة — مفتاح الـ Keystore متاح
 * للعملية دايمًا، فمفيش تعارض مع قفل التطبيق (القفل بيحمي الواجهة، مش
 * المفتاح).
 *
 * مصدرين للتحديث:
 * 1. كل 30 دقيقة من النظام (updatePeriodMillis في monthly_summary_widget_info).
 * 2. فورًا بعد أي إضافة/تعديل/حذف حركة — [refreshNow] بتتنادى من
 *    ExpenseRepository في كل نقطة تغيير.
 */
class MonthlySummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // الاستعلام suspend وقاعدة البيانات مشفّرة، فمينفعش نقرا على الـ main
        // thread. بنرسم الشكل فورًا بـ "—" وبعدين نحدّثه لما الرقم يوصل.
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, null))
        }
        CoroutineScope(Dispatchers.IO).launch { refreshNow(context) }
    }

    companion object {

        /**
         * بيحدّث كل نسخ الويدجت الموجودة على الشاشة. **لازم تتنادى من thread
         * خلفي** (بتقرا من قاعدة البيانات). لو المستخدم مضافش الويدجت خالص
         * بترجع فورًا من غير أي استعلام.
         *
         * كل حاجة جواها ملفوفة: فشل تحديث ويدجت مستحقّش يوقع عملية حفظ حركة.
         */
        suspend fun refreshNow(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, MonthlySummaryWidgetProvider::class.java)
                )
                if (ids.isEmpty()) return

                val range = monthRange()
                val total = AppDatabase.getDatabase(appContext)
                    .expenseDao()
                    .getTotalBetween(range.start, range.end) ?: 0L

                val views = buildViews(appContext, total)
                ids.forEach { manager.updateAppWidget(it, views) }
            }.onFailure { CrashLog.recordNonFatal(context, "MonthlySummaryWidget.refresh", it) }
        }

        private fun buildViews(context: Context, totalMinor: Long?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_monthly_summary)
            views.setTextViewText(
                R.id.widget_total,
                if (totalMinor == null) "—" else formatMinor(totalMinor, withDecimals = false)
            )
            views.setTextViewText(R.id.widget_month, monthLabel())
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
