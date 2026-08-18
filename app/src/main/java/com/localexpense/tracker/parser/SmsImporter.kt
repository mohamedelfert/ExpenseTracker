package com.localexpense.tracker.parser

import android.content.Context
import android.provider.Telephony
import com.localexpense.tracker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsImporter {

    suspend fun importAllSms(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        var totalExamined = 0
        var newAdded = 0

        val db = AppDatabase.getDatabase(context)
        val dao = db.expenseDao()

        cursor?.use { c ->
            val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)

            while (c.moveToNext()) {
                totalExamined++
                val sender = c.getString(addressIndex) ?: ""
                val body = c.getString(bodyIndex) ?: ""
                val timestamp = c.getLong(dateIndex)

                val expense = SmsParser.parseSms(sender, body, timestamp)
                if (expense != null) {
                    if (dao.exists(body, timestamp) == 0) {
                        dao.insertExpense(expense)
                        newAdded++
                    }
                }
            }
        }

        Pair(totalExamined, newAdded)
    }
}