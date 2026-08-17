package com.localexpense.tracker.parser

import com.localexpense.tracker.data.SmsRule

data class ParsedExpense(
    val amount: Double,
    val merchant: String,
    val source: String,
    /** true only when both the amount and a merchant/place were found */
    val isConfirmed: Boolean
)

object SmsParser {

    /**
     * Tries every enabled rule against [sender] and [body].
     * Returns the first rule whose sender pattern matches AND whose debit
     * keyword is present in the body, with whatever amount/merchant it can
     * extract. Returns null if no rule's sender pattern matches at all
     * (message is ignored, e.g. OTP codes, promos, incoming-transfer notices).
     */
    fun parse(sender: String, body: String, rules: List<SmsRule>): ParsedExpense? {
        for (rule in rules) {
            if (!safeMatches(rule.senderPattern, sender) && !safeContains(rule.senderPattern, body)) {
                continue
            }
            if (!safeContains(rule.debitKeywordPattern, body)) {
                // Sender matched but this looks like a deposit/incoming/OTP message, not an expense.
                continue
            }

            val amount = extractAmount(rule.amountPattern, body) ?: continue
            val merchant = extractMerchant(rule.merchantPattern, body)

            return ParsedExpense(
                amount = amount,
                merchant = merchant ?: "غير محدد",
                source = rule.bankName,
                isConfirmed = merchant != null
            )
        }
        return null
    }

    private fun extractAmount(pattern: String, body: String): Double? {
        if (pattern.isBlank()) return null
        return try {
            val match = Regex(pattern).find(body) ?: return null
            val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return null
            raw.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMerchant(pattern: String, body: String): String? {
        if (pattern.isBlank()) return null
        return try {
            val match = Regex(pattern).find(body) ?: return null
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun safeMatches(pattern: String, value: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Regex(pattern).containsMatchIn(value)
        } catch (e: Exception) {
            false
        }
    }

    private fun safeContains(pattern: String, value: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Regex(pattern).containsMatchIn(value)
        } catch (e: Exception) {
            false
        }
    }
}
