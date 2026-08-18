package com.localexpense.tracker.parser

import com.localexpense.tracker.data.Expense
import java.util.regex.Pattern

object SmsParser {

    fun parseSms(sender: String, body: String, timestamp: Long): Expense? {
        val bankName = identifyBank(sender, body) ?: return null
        val amount = extractAmount(body) ?: return null
        val merchant = extractMerchant(body)

        return Expense(
            amount = amount,
            merchant = merchant,
            bankName = bankName,
            timestamp = timestamp,
            rawBody = body
        )
    }

    private fun identifyBank(sender: String, body: String): String? {
        val s = sender.uppercase()
        val b = body.uppercase()

        return when {
            s.contains("CIB") || b.contains("CIB") -> "CIB"
            s.contains("ALAHLY") || s.contains("NBE") || b.contains("البنك الأهلي") -> "BanK-AlAhly"
            s.contains("MISR") || b.contains("بنك مصر") -> "Banque Misr"
            s.contains("FAISAL") || b.contains("فيصل") -> "FAISAL BANK"
            s.contains("VF-CASH") || b.contains("فودافون كاش") -> "Vodafone Cash"
            isTransactionMessage(body) -> if (sender.isNotBlank()) sender else "بنك آخر"
            else -> null
        }
    }

    private fun isTransactionMessage(body: String): Boolean {
        val keywords = listOf("خصم", "سحب", "شراء", "تم خصم", "تحويل", "EGP", "مبلغ", "بطاقة", "شراء بـ", "عملية")
        return keywords.any { body.contains(it, ignoreCase = true) }
    }

    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("(?:EGP|ج\\.م|جم|LE)\\s*([\\d,]+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*(?:EGP|ج\\.م|جم|LE)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:مبلغ|بـ|بيمة)\\s*([\\d,]+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val cleanNum = matcher.group(1)?.replace(",", "") ?: continue
                return cleanNum.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractMerchant(body: String): String {
        val pattern = Pattern.compile("(?:لدى|عند|من|at|to)\\s+([A-Za-z0-9\\s_\\-أ-ي]+?)(?=\\s*(?:بـ|بطاقة|في|بتاريخ|EGP|ج\\.م|\\d|\\.|$))", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            val found = matcher.group(1)?.trim()
            if (!found.isNullOrBlank() && found.length > 2) return found
        }

        return when {
            body.contains("سحب آلي", ignoreCase = true) || body.contains("ATM", ignoreCase = true) -> "سحب آلي (ATM)"
            body.contains("شراء", ignoreCase = true) -> "عملية شراء"
            body.contains("تحويل", ignoreCase = true) -> "عملية تحويل"
            body.contains("خصم", ignoreCase = true) -> "عملية خصم"
            else -> "عملية بنكية"
        }
    }
}