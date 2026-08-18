package com.localexpense.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.expenseDao()

                    for (sms in messages) {
                        val sender = sms.originatingAddress ?: ""
                        val body = sms.messageBody ?: ""
                        val timestamp = sms.timestampMillis

                        val expense = SmsParser.parseSms(sender, body, timestamp)
                        if (expense != null) {
                            if (dao.exists(expense.rawBody, expense.timestamp) == 0) {
                                dao.insertExpense(expense)
                            }
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