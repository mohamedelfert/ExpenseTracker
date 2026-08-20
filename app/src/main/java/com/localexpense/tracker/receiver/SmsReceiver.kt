package com.localexpense.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.notification.BudgetAlertChecker
import com.localexpense.tracker.notification.NotificationHelper
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // دمج جميع أجزاء الرسالة في نص واحد
                    val sender = messages[0].originatingAddress ?: ""
                    val body = messages.joinToString("") { it.messageBody ?: "" }
                    val timestamp = messages[0].timestampMillis

                    val db = AppDatabase.getDatabase(context)
                    val customRules = db.smsRuleDao().getEnabledRules()

                    val expense = SmsParser.parseSms(sender, body, timestamp, customRules)
                    if (expense != null) {
                        val dao = db.expenseDao()

                        // الريبو هو اللي بيعمل فحص التكرار + التصنيف + تسجيل
                        // الجهة، عشان مسار الـ SMS والإشعار والاستيراد يفضلوا
                        // متطابقين في السلوك.
                        val saved = ExpenseRepository(context).captureTransaction(expense)
                        if (saved != null) {
                            NotificationHelper.showExpenseCapturedNotification(
                                context, saved.amountMinor, saved.merchant, saved.bankName
                            )
                            BudgetAlertChecker.checkAndNotify(
                                context, dao, db.budgetDao(), saved.categoryName, saved.timestamp
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}