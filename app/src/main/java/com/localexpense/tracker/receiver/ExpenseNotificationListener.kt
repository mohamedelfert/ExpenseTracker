package com.localexpense.tracker.receiver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.insertIfNotDuplicate
import com.localexpense.tracker.notification.BudgetAlertChecker
import com.localexpense.tracker.notification.NotificationHelper
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseNotificationListener : NotificationListenerService() {

    // استخدام Set + تطابق تام (بدل .contains) عشان نمنع أي تطبيق خبيث بإيسم
    // باكيدج بيحتوي جزئيًا على اسم بنك حقيقي (مثلاً "com.cib.cibmobile.fake")
    // من إنه يتقبل هنا ويقدر يبعت بيانات مصروفات مزيفة.
    private val validPackages = setOf(
        "com.cib.cibmobile", // CIB
        "com.nbe.nbebm", // NBE
        "com.alexbank.alexmobile", // Bank Alex
        "com.faisalbank.faisalmobile", // Faisal
        "com.vodafone.vfeapp", // Vodafone Cash
        "com.qnbalahli.mobile", // QNB
        "com.egyptianbanks.instapay", // InstaPay
        "com.android.mms", // SMS fallback
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging" // Samsung Messages
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        
        val packageName = sbn.packageName
        if (packageName !in validPackages) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        
        val fullBody = "$title $text"
        val sender = title.ifBlank { packageName } // use title as sender, fallback to package

        val timestamp = sbn.postTime

        // نفس منطق منع التكرار المستخدم في SmsReceiver - مهم جدًا هنا
        // لأن نفس العملية البنكية غالبًا بتوصل كإشعار SMS وكإشعار تطبيق
        // البنك مع بعض، فمن غيره كانت هتتسجل مرتين.
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val customRules = db.smsRuleDao().getEnabledRules()

            val expense = SmsParser.parseSms(
                sender, fullBody, timestamp, customRules,
                source = com.localexpense.tracker.data.TransactionSource.NOTIFICATION
            )
            if (expense != null) {
                val dao = db.expenseDao()

                if (dao.insertIfNotDuplicate(expense)) {
                    NotificationHelper.showExpenseCapturedNotification(
                        applicationContext, expense.amountMinor, expense.merchant, expense.bankName
                    )
                    BudgetAlertChecker.checkAndNotify(
                        applicationContext, dao, db.budgetDao(), expense.categoryName, expense.timestamp
                    )
                }
            }
        }
    }
}
