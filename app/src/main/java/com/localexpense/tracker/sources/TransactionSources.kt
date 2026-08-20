package com.localexpense.tracker.sources

import com.localexpense.tracker.data.TransactionType

/**
 * تعريف مصدر عمليات (بنك أو محفظة) في مكان واحد — المرحلة 17، بند 37.
 *
 * قبل كده كان اسم البنك بيتحدد بسلسلة `when` جوه SmsParser، وقائمة باكيدجات
 * التطبيقات المسموحة مكتوبة تاني جوه NotificationListener. دلوقتي كل مصدر
 * بيتعرّف مرة واحدة هنا: الباكيدج، نمط المرسل، وأنماط الاستخراج الخاصة به
 * لو موجودة — والاتنين بيقروا من نفس السجل.
 *
 * الإضافة الجديدة لبنك = صف في [ALL] بس. القواعد اللي المستخدم بيضيفها من
 * الشاشة (جدول sms_rules) بتفضل ليها الأولوية على السجل ده، فتقدر تعدّل أي
 * سلوك من غير كود.
 */
data class TransactionSourceSpec(
    /** الاسم المعروض والمخزّن في `Expense.bankName`. */
    val bankName: String,
    /** باكيدجات تطبيقات المصدر (لقراءة الإشعارات). */
    val packages: Set<String> = emptySet(),
    /** أنماط تتطابق مع عنوان المرسل أو نص الرسالة للتعرّف على المصدر. */
    val senderPatterns: List<String> = emptyList(),
    /** نمط مبلغ خاص بالمصدر ده (مجموعة التقاط واحدة)، أو null لاستخدام العام. */
    val amountPattern: String? = null,
    /** نمط جهة خاص بالمصدر (مجموعة التقاط واحدة)، أو null للمنطق العام. */
    val merchantPattern: String? = null,
    /** نوع الحركة الافتراضي لو نص الرسالة مش حاسم (المحافظ غالبًا تحويلات). */
    val defaultType: TransactionType? = null
) {
    private val senderRegexes: List<Regex> by lazy {
        senderPatterns.map { Regex(it, RegexOption.IGNORE_CASE) }
    }

    fun matches(sender: String, body: String): Boolean =
        senderRegexes.any { it.containsMatchIn(sender) || it.containsMatchIn(body) }
}

object TransactionSources {

    val ALL: List<TransactionSourceSpec> = listOf(
        TransactionSourceSpec(
            bankName = "Bank-AlAhly",
            packages = setOf("com.nbe.nbebm", "com.nbe.mobilebanking"),
            senderPatterns = listOf("AHLY", "NBE", "الأهلي", "ALAHLY")
        ),
        TransactionSourceSpec(
            bankName = "Banque Misr",
            packages = setOf("com.bm.bmmobile", "eg.com.banquemisr.bmonline"),
            senderPatterns = listOf("MISR", "بنك مصر")
        ),
        TransactionSourceSpec(
            bankName = "CIB",
            packages = setOf("com.cib.cibmobile"),
            senderPatterns = listOf("CIB")
        ),
        TransactionSourceSpec(
            bankName = "QNB",
            packages = setOf("com.qnbalahli.mobile", "com.qnb.alahli"),
            senderPatterns = listOf("QNB")
        ),
        TransactionSourceSpec(
            bankName = "Faisal Bank",
            packages = setOf("com.faisalbank.faisalmobile"),
            senderPatterns = listOf("FAISAL", "فيصل")
        ),
        TransactionSourceSpec(
            bankName = "Bank Alex",
            packages = setOf("com.alexbank.alexmobile"),
            senderPatterns = listOf("ALEX", "الإسكندرية")
        ),
        TransactionSourceSpec(
            bankName = "فودافون كاش",
            packages = setOf("com.vodafone.vfeapp", "com.vodafone.vodafonecash"),
            senderPatterns = listOf("VF-?CASH", "فودافون كاش", "VODAFONE CASH")
        ),
        TransactionSourceSpec(
            bankName = "InstaPay",
            packages = setOf("com.egyptianbanks.instapay"),
            senderPatterns = listOf("INSTAPAY", "إنستاباي", "انستاباي")
        )
    )

    /** تطبيقات الرسائل: مسموحة كمصدر إشعارات لأن رسالة البنك بتوصل من خلالها. */
    val MESSAGING_PACKAGES: Set<String> = setOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    /**
     * الباكيدجات المسموح قراءة إشعاراتها. تطابق تام (مش contains) عشان تطبيق
     * خبيث اسمه فيه اسم بنك حقيقي ما يقدرش يدخل بيانات مزيفة.
     */
    val ALLOWED_PACKAGES: Set<String> =
        ALL.flatMap { it.packages }.toSet() + MESSAGING_PACKAGES

    fun forPackage(packageName: String): TransactionSourceSpec? =
        ALL.firstOrNull { packageName in it.packages }

    /** التعرّف على المصدر من عنوان المرسل أو نص الرسالة. */
    fun resolve(sender: String, body: String): TransactionSourceSpec? =
        ALL.firstOrNull { it.matches(sender, body) }

    /** اسم البنك للحركة: المصدر المعروف، وإلا عنوان المرسل زي ما هو. */
    fun bankNameFor(sender: String, body: String): String =
        resolve(sender, body)?.bankName ?: sender.ifBlank { "بنك آخر" }
}
