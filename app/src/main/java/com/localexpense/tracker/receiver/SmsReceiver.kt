package com.localexpense.tracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.insertIfNotDuplicate
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

                    val expense = SmsParser.parseSms(sender, body, timestamp)
                    if (expense != null) {
                        val db = AppDatabase.getDatabase(context)
                        val dao = db.expenseDao()

                        if (dao.insertIfNotDuplicate(expense)) {
                            showNotification(context, expense.amount, expense.merchant, expense.bankName)
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

    private fun showNotification(context: Context, amount: Double, merchant: String, bankName: String) {
        val channelId = "expense_tracker_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات المصروفات",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تم تسجيل مصروف جديد 💳")
            .setContentText("خصم $amount ج.م - $merchant ($bankName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}