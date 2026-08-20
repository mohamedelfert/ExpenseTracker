package com.localexpense.tracker.parser

import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.data.TransactionSource
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.util.rawMessageHash

object SmsParser {

    /**
     * [customRules] هي القواعد اللي المستخدم ضافها بنفسه من شاشة "قواعد
     * الرسائل" (مخزّنة في جدول sms_rules). بتتفحص الأول وبالترتيب، ولو
     * قاعدة منها مطابقة (المرسل + كلمة الخصم + استخراج مبلغ ناجح) بتاخد
     * الأولوية على المنطق الثابت تحت. ده بيسمح للمستخدم إنه يضيف/يظبط بنك
     * جديد أو صيغة رسالة معينة من غير ما يحتاج يعدّل الكود.
     *
     * [source] بيتحدد من اللي بينادي: SMS للبرودكاست، NOTIFICATION لإشعارات
     * تطبيقات البنوك، IMPORT للاستيراد اليدوي من صندوق الوارد.
     */
    fun parseSms(
        sender: String,
        body: String,
        timestamp: Long,
        customRules: List<SmsRule> = emptyList(),
        source: TransactionSource = TransactionSource.SMS
    ): Expense? {
        parseWithCustomRules(sender, body, timestamp, customRules, source)?.let { return it }

        // 1. نوع العملية: خصم (مصروف)، إيداع (دخل)، أو استرداد.
        val type = detectType(body) ?: return null

        // 2. استخراج المبلغ (دعم العملة المصرية EGP, LE, ج.م)
        val amountRegex = Regex("""(?:مبلغ|EGP|LE|LE\.|ج\.م|جم)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(body) ?: Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:EGP|LE|ج\.م|جم)""", RegexOption.IGNORE_CASE).find(body)
        val amountMinor = amountMatch?.groupValues?.getOrNull(1)?.let { parseAmountMinor(it) } ?: return null

        // 4. استخراج اسم الجهة (Merchant)
        val merchant = spec?.merchantPattern
            ?.let { pattern -> firstGroup(pattern, body)?.takeIf { it.isNotBlank() } }
            ?: extractMerchant(body)

        // 5. تحديد اسم البنك أو المحفظة من سجل المصادر
        val bankName = spec?.bankName ?: extractBankName(sender, body)

        // 6. التحديد التلقائي للفئة (Category)
        val categoryName = detectCategory(body, merchant)

        return Expense(
            amountMinor = amountMinor,
            type = type,
            merchant = merchant,
            bankName = bankName,
            timestamp = timestamp,
            rawBody = body,
            categoryName = categoryName,
            source = source,
            referenceId = extractReference(body),
            rawHash = rawMessageHash(sender, body)
        )
    }

    /**
     * نوع العملية من نص الرسالة (المرحلة 6): الاسترداد بيتفحص الأول لأن رسالة
     * الاسترداد غالبًا فيها كلمة "إيداع" كمان، والإيداع/التحويل الوارد = دخل.
     * null = الرسالة دي مش عملية مالية أصلاً (إعلان، OTP، رصيد...).
     */
    internal fun detectType(body: String): TransactionType? {
        val refundWords = listOf("استرداد", "رد مبلغ", "ارتجاع", "refund", "reversal", "reversed")
        val creditWords = listOf("إيداع", "ايداع", "اضيف", "أضيف", "راتب", "مرتب", "credited", "deposit", "salary")
        val debitWords = listOf(
            "خصم", "شراء", "سحب", "تحويل", "دفع",
            "debited", "purchase", "paid", "deducted", "withdrawal"
        )

        return when {
            refundWords.any { body.contains(it, true) } -> TransactionType.REFUND
            creditWords.any { body.contains(it, true) } -> TransactionType.INCOME
            debitWords.any { body.contains(it, true) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    /**
     * رقم مرجع العملية لو البنك بعته. ده أدق مفتاح لمنع التكرار (المرحلة 17)،
     * لأن نفس العملية بيوصل ليها نفس المرجع من كل المسارات.
     */
    internal fun extractReference(body: String): String {
        val patterns = listOf(
            Regex("""(?:مرجع|المرجع|رقم العملية|رقم المرجع)\s*[:#\-]?\s*([A-Za-z0-9]{4,24})"""),
            Regex("""(?:ref|reference|txn|trx|transaction)\s*(?:no|id|#)?\s*[:#\-]?\s*([A-Za-z0-9]{4,24})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    /**
     * نوع العملية من نص الرسالة (المرحلة 6): الاسترداد بيتفحص الأول لأن رسالة
     * الاسترداد غالبًا فيها كلمة "إيداع" كمان، والإيداع/التحويل الوارد = دخل.
     * null = الرسالة دي مش عملية مالية أصلاً (إعلان، OTP، رصيد...).
     */
    internal fun detectType(body: String): TransactionType? {
        val refundWords = listOf("استرداد", "رد مبلغ", "ارتجاع", "refund", "reversal", "reversed")
        val creditWords = listOf("إيداع", "ايداع", "اضيف", "أضيف", "راتب", "مرتب", "credited", "deposit", "salary")
        val debitWords = listOf(
            "خصم", "شراء", "سحب", "تحويل", "دفع",
            "debited", "purchase", "paid", "deducted", "withdrawal"
        )

        return when {
            refundWords.any { body.contains(it, true) } -> TransactionType.REFUND
            creditWords.any { body.contains(it, true) } -> TransactionType.INCOME
            debitWords.any { body.contains(it, true) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    /**
     * رقم مرجع العملية لو البنك بعته. ده أدق مفتاح لمنع التكرار (المرحلة 17)،
     * لأن نفس العملية بيوصل ليها نفس المرجع من كل المسارات.
     */
    internal fun extractReference(body: String): String {
        val patterns = listOf(
            Regex("""(?:مرجع|المرجع|رقم العملية|رقم المرجع)\s*[:#\-]?\s*([A-Za-z0-9]{4,24})"""),
            Regex("""(?:ref|reference|txn|trx|transaction)\s*(?:no|id|#)?\s*[:#\-]?\s*([A-Za-z0-9]{4,24})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun parseWithCustomRules(
        sender: String,
        body: String,
        timestamp: Long,
        rules: List<SmsRule>,
        source: TransactionSource
    ): Expense? {
        for (rule in rules) {
            if (!rule.isEnabled) continue

            try {
                if (rule.senderPattern.isNotBlank()) {
                    val senderRegex = Regex(rule.senderPattern, RegexOption.IGNORE_CASE)
                    // بعض البنوك بتبعت اسم مختلف في المرسل عن اللي المستخدم شافه فعلًا،
                    // فبنقبل التطابق سواء في عنوان المرسل أو في نص الرسالة نفسه.
                    if (!senderRegex.containsMatchIn(sender) && !senderRegex.containsMatchIn(body)) continue
                }

                if (rule.debitKeywordPattern.isNotBlank()) {
                    val keywordRegex = Regex(rule.debitKeywordPattern, RegexOption.IGNORE_CASE)
                    if (!keywordRegex.containsMatchIn(body)) continue
                }

                val amountRegex = Regex(rule.amountPattern, RegexOption.IGNORE_CASE)
                val amountMinor = amountRegex.find(body)
                    ?.groupValues?.getOrNull(1)
                    ?.let { parseAmountMinor(it) } ?: continue

                val merchant = if (rule.merchantPattern.isNotBlank()) {
                    Regex(rule.merchantPattern, RegexOption.IGNORE_CASE)
                        .find(body)?.groupValues?.getOrNull(1)?.trim()
                        ?.takeIf { it.isNotBlank() }
                } else null

                return Expense(
                    amountMinor = amountMinor,
                    type = detectType(body) ?: TransactionType.EXPENSE,
                    merchant = merchant ?: extractMerchant(body),
                    bankName = rule.bankName,
                    timestamp = timestamp,
                    rawBody = body,
                    categoryName = detectCategory(body, merchant ?: ""),
                    source = source,
                    referenceId = extractReference(body),
                    rawHash = rawMessageHash(sender, body)
                )
            } catch (e: Exception) {
                // Regex غلط في القاعدة دي - تجاهلها وكمّل بالقواعد اللي بعدها
                continue
            }
        }
        return null
    }

    /** أول مجموعة التقاط في نمط، أو null لو النمط غلط أو مفيش تطابق. */
    private fun firstGroup(pattern: String, body: String): String? =
        runCatching {
            Regex(pattern, RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)?.trim()
        }.getOrNull()

    /** استخراج المبلغ بالأنماط العامة (بيقبل الصيغتين: العملة قبل أو بعد الرقم). */
    internal fun extractAmountMinor(body: String): Long? {
        val labelled = Regex(
            """(?:مبلغ|EGP|LE|LE\.|ج\.م|جم)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        val trailing = Regex(
            """([\d,]+(?:\.\d{1,2})?)\s*(?:EGP|LE|ج\.م|جم)""",
            RegexOption.IGNORE_CASE
        )
        val match = labelled.find(body) ?: trailing.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.let { parseAmountMinor(it) }
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

    /**
     * اسم البنك بقى بيتحدد من سجل المصادر (sources/TransactionSources) مش من
     * سلسلة when هنا — نفس السجل اللي NotificationListener بيقرا منه
     * الباكيدجات المسموحة، فمفيش مكانين لازم يتحدّثوا مع كل بنك جديد.
     */
    private fun extractBankName(sender: String, body: String): String =
        TransactionSources.bankNameFor(sender, body)

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