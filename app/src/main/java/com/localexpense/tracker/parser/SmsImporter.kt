package com.localexpense.tracker.parser

import android.content.Context
import android.provider.Telephony
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.insertIfNotDuplicate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsImporter {

    /**
     * لو [startMillis]/[endMillis] اتحددوا، هيتم فلترة رسائل الـ SMS على مستوى
     * الاستعلام نفسه (مش بعد القراءة) عشان الاستيراد يكون أسرع ومياخدش داتا
     * من سنين فاتت من غير ما المستخدم يطلبها. null في أي طرف يعني من غير حد
     * (من الأول / لحد النهارده).
     */
    suspend fun importAllSms(
        context: Context,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        if (startMillis != null) {
            selectionParts += "${Telephony.Sms.DATE} >= ?"
            selectionArgs += startMillis.toString()
        }
        if (endMillis != null) {
            selectionParts += "${Telephony.Sms.DATE} <= ?"
            selectionArgs += endMillis.toString()
        }
        val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection,
            selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
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
                    if (dao.insertIfNotDuplicate(expense)) {
                        newAdded++
                    }
                }
            }
        }

        Pair(totalExamined, newAdded)
    }
}
