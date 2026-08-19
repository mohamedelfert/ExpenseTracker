package com.localexpense.tracker.receiver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.ExpenseRepository
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
        
        // Parse the notification
        val expense = SmsParser.parseSms(sender, fullBody, timestamp)
        
        if (expense != null) {
            // Save to DB
            CoroutineScope(Dispatchers.IO).launch {
                val repository = ExpenseRepository(applicationContext)
                repository.insertExpense(expense)
            }
        }
    }
}
