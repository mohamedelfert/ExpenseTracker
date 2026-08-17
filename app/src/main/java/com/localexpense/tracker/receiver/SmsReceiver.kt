package com.localexpense.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Egyptian carriers sometimes split one SMS into multiple parts; merge them.
        val sender = messages.first().originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis

        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(appContext)
            val rules = db.smsRuleDao().getEnabledRules()
            val parsed = SmsParser.parse(sender, body, rules) ?: return@launch

            db.expenseDao().insert(
                Expense(
                    amount = parsed.amount,
                    merchant = parsed.merchant,
                    source = parsed.source,
                    timestampMillis = receivedAt,
                    rawMessage = body,
                    isConfirmed = parsed.isConfirmed
                )
            )
        }
    }
}
