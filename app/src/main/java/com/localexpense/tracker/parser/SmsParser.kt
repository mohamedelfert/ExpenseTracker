package com.localexpense.tracker.parser

import com.localexpense.tracker.data.Expense

object SmsParser {

    fun parseSms(sender: String, body: String, timestamp: Long): Expense? {
        // 1. التحقق من أن الرسالة تتضمن عملية خصم أو سحب أو شراء أو تحويل صادرة
        val isDebit = body.contains("خصم", true) ||
                body.contains("شراء", true) ||
                body.contains("سحب", true) ||
                body.contains("تحويل", true) ||
                body.contains("Debited", true) ||
                body.contains("Purchase", true) ||
                body.contains("Paid", true) ||
                body.contains("Deducted", true)

        if (!isDebit) return null

        // 2. استخراج المبلغ (دعم العملة المصرية EGP, LE, ج.م)
        val amountRegex = Regex("""(?:مبلغ|EGP|LE|LE\.|ج\.م|جم)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(body) ?: Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:EGP|LE|ج\.م|جم)""", RegexOption.IGNORE_CASE).find(body)
        val amount = amountMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return null

        // 3. استخراج اسم الجهة (Merchant)
        val merchant = extractMerchant(body)

        // 4. تحديد اسم البنك أو المحفظة
        val bankName = extractBankName(sender, body)

        // 5. التحديد التلقائي للفئة (Category)
        val categoryName = detectCategory(body, merchant)

        return Expense(
            amount = amount,
            merchant = merchant,
            bankName = bankName,
            timestamp = timestamp,
            rawBody = body,
            categoryName = categoryName
        )
    }

    private fun extractMerchant(body: String): String {
        // عمليات السحب النقدي من الـ ATM
        if (body.contains("سحب", true) || body.contains("ATM", true) || body.contains("ماكينة", true)) {
            return "سحب نقدي (ATM)"
        }

        // عمليات التحويل (InstaPay / تحويلات بنكية)
        val transferMatch = Regex("""(?:تحويل|إلى|to|لـ)\s+([A-Za-z0-9\u0600-\u06FF ._-]{3,25})""", RegexOption.IGNORE_CASE).find(body)
        if (transferMatch != null && (body.contains("تحويل") || body.contains("InstaPay") || body.contains("Transfer"))) {
            val name = transferMatch.groupValues[1].trim()
            if (!name.contains("حساب", true) && !name.contains("بطاقة", true)) {
                return "تحويل إلى: $name"
            }
        }

        // الشراء لدى التُّجار (البنوك المصرية: "لدى", "في", "at", "عند", "من")
        val merchantRegex = Regex("""(?:لدى|في|at|عند|من)\s+([A-Za-z0-9\u0600-\u06FF ._*-]{3,30})""", RegexOption.IGNORE_CASE)
        val match = merchantRegex.find(body)

        if (match != null) {
            var extracted = match.groupValues[1].trim()
            val blacklist = listOf("بطاقة", "حساب", "رقم", "المباشر", "الائتماني", "card", "account")
            if (blacklist.none { extracted.startsWith(it, ignoreCase = true) }) {
                extracted = extracted.split("\n")[0].split(".")[0]
                return extracted.take(25)
            }
        }

        return "جهة غير محددة"
    }

    private fun extractBankName(sender: String, body: String): String {
        val s = sender.uppercase()
        val b = body.uppercase()
        return when {
            s.contains("AHLY") || s.contains("NBE") || b.contains("الأهلي") || b.contains("ALAHLY") -> "Bank-AlAhly"
            s.contains("MISR") || b.contains("بنك مصر") -> "Banque Misr"
            s.contains("CIB") || b.contains("CIB") -> "CIB"
            s.contains("FAISAL") || b.contains("فيصل") -> "Faisal Bank"
            s.contains("INSTAPAY") || b.contains("INSTAPAY") || b.contains("إنستاباي") -> "InstaPay"
            s.contains("VF-CASH") || b.contains("فودافون كاش") -> "فودافون كاش"
            s.contains("QNB") -> "QNB"
            s.contains("ALEX") || b.contains("الإسكندرية") -> "Bank Alex"
            else -> sender.ifBlank { "بنك آخر" }
        }
    }

    private fun detectCategory(body: String, merchant: String): String {
        val text = "$body $merchant".lowercase()
        return when {
            text.contains("سحب") || text.contains("atm") -> "سحب نقدي"
            text.contains("تحويل") || text.contains("instapay") -> "تحويلات"
            text.contains("supermarket") || text.contains("hyper") || text.contains("ماركت") || text.contains("كارفور") || text.contains("خير زمان") -> "سوبر ماركت"
            text.contains("restaurant") || text.contains("mcdonald") || text.contains("kfc") || text.contains("مطعم") || text.contains("كافيه") || text.contains("cafe") -> "مطاعم وكافيهات"
            text.contains("gas") || text.contains("petrol") || text.contains("بنزين") || text.contains("chillout") || text.contains("uber") || text.contains("أوبر") -> "وقود ومواصلات"
            text.contains("vodafone") || text.contains("orange") || text.contains("we") || text.contains("fawry") || text.contains("فوري") || text.contains("فاتورة") -> "فواتير وخدمات"
            text.contains("pharmacy") || text.contains("صيدلية") || text.contains("علاج") -> "صحة وعلاج"
            else -> "عام"
        }
    }
}