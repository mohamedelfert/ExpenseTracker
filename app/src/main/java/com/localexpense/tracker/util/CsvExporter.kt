package com.localexpense.tracker.util

import com.localexpense.tracker.data.Account
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.money.minorToPlainDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    /**
     * تصدير CSV (المرحلة 18).
     *
     * [includeRawText] افتراضيًا **false**: نص رسالة البنك الخام فيه بيانات
     * حساسة (أرقام بطاقات جزئية، أرصدة)، والـ spec بيقول ما يتصدّرش إلا لو
     * المستخدم اختار كده صريح من شاشة التصدير.
     */
    fun exportToCsv(
        expenses: List<Expense>,
        accounts: List<Account> = emptyList(),
        includeRawText: Boolean = false
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val accountNames = accounts.associate { it.id to it.name }
        val builder = StringBuilder()

        val header = mutableListOf(
            "ID", "Date", "Amount", "Currency", "Type", "Merchant", "Category",
            "Account", "Bank", "Source", "ReferenceID", "Note", "Verified"
        )
        if (includeRawText) header += "RawText"
        builder.append(header.joinToString(",")).append('\n')

        for (expense in expenses) {
            val row = mutableListOf(
                expense.id.toString(),
                dateFormat.format(Date(expense.timestamp)),
                minorToPlainDecimal(expense.amountMinor), // رقم عشري خام بدون فواصل آلاف
                expense.currency,
                expense.type.name,
                escapeCsv(expense.merchant),
                escapeCsv(expense.categoryName),
                escapeCsv(expense.accountId?.let { accountNames[it] } ?: ""),
                escapeCsv(expense.bankName),
                expense.source.name,
                escapeCsv(expense.referenceId),
                escapeCsv(expense.note),
                if (expense.isVerified) "yes" else "no"
            )
            if (includeRawText) row += escapeCsv(expense.rawBody.replace("\n", " "))
            builder.append(row.joinToString(",")).append('\n')
        }

        return builder.toString()
    }

    /** تقرير مجمّع بسيط: صفوف "المفتاح، المبلغ" — للفئات أو الجهات أو الشهور. */
    fun aggregateToCsv(title: String, rows: List<Pair<String, Long>>): String {
        val builder = StringBuilder()
        builder.append("$title,Amount\n")
        rows.forEach { (key, amountMinor) ->
            builder.append("${escapeCsv(key)},${minorToPlainDecimal(amountMinor)}\n")
        }
        return builder.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
