package com.localexpense.tracker.parser

import android.content.Context
import android.provider.Telephony
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.SmsRule

data class ImportResult(
    val scanned: Int,
    val imported: Int
)

object SmsImporter {

    /**
     * Reads the device's SMS inbox (requires READ_SMS, already granted at this point),
     * runs every enabled rule against each message, and returns the expenses that would
     * be created — skipping any message whose exact text is already in [existingRawMessages]
     * so re-running the import never creates duplicates.
     */
    fun scanInbox(
        context: Context,
        rules: List<SmsRule>,
        existingRawMessages: Set<String>
    ): Pair<ImportResult, List<Expense>> {
        val found = mutableListOf<Expense>()
        var scanned = 0

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                scanned++
                val sender = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)

                if (body in existingRawMessages) continue

                val parsed = SmsParser.parse(sender, body, rules) ?: continue

                found.add(
                    Expense(
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        source = parsed.source,
                        timestampMillis = date,
                        rawMessage = body,
                        isConfirmed = parsed.isConfirmed
                    )
                )
            }
        }

        return ImportResult(scanned = scanned, imported = found.size) to found
    }
}
