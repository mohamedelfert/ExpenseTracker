package com.localexpense.tracker.util

import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.money.minorToPlainDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun exportToCsv(expenses: List<Expense>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val builder = StringBuilder()

        // Header
        builder.append("ID,Date,Amount,Currency,Type,Merchant,Bank,Category,RawText\n")

        // Rows
        for (expense in expenses) {
            val dateStr = dateFormat.format(Date(expense.timestamp))
            val amount = minorToPlainDecimal(expense.amountMinor) // رقم عشري خام بدون فواصل آلاف
            // Escape CSV fields
            val merchant = escapeCsv(expense.merchant)
            val bank = escapeCsv(expense.bankName)
            val category = escapeCsv(expense.categoryName)
            val raw = escapeCsv(expense.rawBody.replace("\n", " "))

            builder.append("${expense.id},$dateStr,$amount,${expense.currency},${expense.type},$merchant,$bank,$category,$raw\n")
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
