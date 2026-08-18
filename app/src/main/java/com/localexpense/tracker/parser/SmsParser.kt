package com.localexpense.tracker.parser

import com.localexpense.tracker.data.Expense

object SmsParser {

    fun parseSms(sender: String, body: String, timestamp: Long): Expense? {
        // التحقق من أن الرسالة تحتوي على كلمات خصم أو شراء أو دفع
        val isDebit = body.contains("خصم", true) || 
                      body.contains("شراء", true) || 
                      body.contains("تم سحب", true) || 
                      body.contains("تم تحويل", true) || 
                      body.contains("Debited", true) || 
                      body.contains("Purchase", true) || 
                      body.contains("Paid", true)

        if (!isDebit) return null

        // 1. استخراج المبلغ
        val amountRegex = Regex("""(?:مبلغ|EGP|LE|LE\.|ج\.م|جم)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(body) ?: Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:EGP|LE|ج\.م)""", RegexOption.IGNORE_CASE).find(body)
        
        val amount = amountMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return null

        // 2. استخراج الجهة (Merchant)
        val merchantRegex = Regex("""(?:في|لدى|at|to)\s+([A-Za-z0-9\u0600-\u06FF ._-]{3,30})""", RegexOption.IGNORE_CASE)
        val merchant = merchantRegex.find(body)?.groupValues?.getOrNull(1)?.trim() ?: "جهة غير محددة"

        // 3. تحديد اسم البنك
        val bankName = when {
            sender.contains("CIB", true) || body.contains("CIB", true) -> "CIB"
            sender.contains("NBE", true) || body.contains("الأهلي", true) -> "البنك الأهلي"
            sender.contains("InstaPay", true) || body.contains("إنستاباي", true) -> "InstaPay"
            sender.contains("BM", true) || body.contains("مصر", true) -> "بنك مصر"
            sender.contains("VF-Cash", true) || body.contains("فودافون كاش", true) -> "فودافون كاش"
            sender.contains("QNB", true) -> "QNB"
            else -> sender.ifBlank { "بنك آخر" }
        }

        return Expense(
            amount = amount,
            merchant = merchant,
            bankName = bankName,
            timestamp = timestamp,
            rawBody = body,
            categoryName = "عام"
        )
    }
}