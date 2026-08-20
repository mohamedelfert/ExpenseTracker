package com.localexpense.tracker.receiver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.notification.BudgetAlertChecker
import com.localexpense.tracker.notification.NotificationHelper
import com.localexpense.tracker.parser.SmsParser
import com.localexpense.tracker.sources.TransactionSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseNotificationListener : NotificationListenerService() {

    // قائمة الباكيدجات المسموحة جاية من سجل المصادر (sources/TransactionSources)
    // — تطابق تام (مش contains) عشان أي تطبيق خبيث باسم باكيدج بيحتوي جزئيًا
    // على اسم بنك حقيقي (مثلاً "com.cib.cibmobile.fake") ما يقدرش يدخل هنا
    // ويبعت بيانات مصروفات مزيفة.
    private val validPackages = TransactionSources.ALLOWED_PACKAGES

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        
        val packageName = sbn.packageName
        if (packageName !in validPackages) return

        // بعض تطبيقات البنوك بتحط CharSequence مش String في الـ extras، و
        // getString ساعتها بترمي ClassCastException. getCharSequence أأمن.
        val extras = sbn.notification?.extras ?: return
        val title = runCatching { extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
            .getOrNull() ?: ""
        val text = runCatching { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() }
            .getOrNull() ?: ""
        
        val fullBody = "$title $text"
        val sender = title.ifBlank { packageName } // use title as sender, fallback to package

        val timestamp = sbn.postTime

        // نفس منطق منع التكرار المستخدم في SmsReceiver - مهم جدًا هنا
        // لأن نفس العملية البنكية غالبًا بتوصل كإشعار SMS وكإشعار تطبيق
        // البنك مع بعض، فمن غيره كانت هتتسجل مرتين.
        // try/catch مطلوب: الكوروتين ده بيشتغل في CoroutineScope مستقل، وأي
        // استثناء جواه (قاعدة بيانات، Regex غلط في قاعدة مستخدم، إشعار بشكل
        // غير متوقع) كان بيوصل للـ default handler ويقفل التطبيق كله — والمستخدم
        // بيشوفه كـ "التطبيق خرج لوحده بعد ما فعّلت إذن الإشعارات".
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val customRules = db.smsRuleDao().getEnabledRules()

            val expense = SmsParser.parseSms(
                sender, fullBody, timestamp, customRules,
                source = com.localexpense.tracker.data.TransactionSource.NOTIFICATION
            )
            if (expense != null) {
                val dao = db.expenseDao()

                // نفس نقطة الدخول المستخدمة في SmsReceiver و SmsImporter.
                val saved = ExpenseRepository(applicationContext).captureTransaction(expense)
                if (saved != null) {
                    NotificationHelper.showExpenseCapturedNotification(
                        applicationContext, saved.amountMinor, saved.merchant, saved.bankName
                    )
                    BudgetAlertChecker.checkAndNotify(
                        applicationContext, dao, db.budgetDao(), saved.categoryName, saved.timestamp
                    )
                }
            } catch (e: Exception) {
                // إشعار واحد فشل مش سبب يقفل التطبيق.
                e.printStackTrace()
            }
        }
    }
}
