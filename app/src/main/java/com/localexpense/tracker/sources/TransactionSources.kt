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
 *
 * ملحوظة مهمة عن الباكيدجات المضافة الجديدة:
 * أنماط المرسل (senderPatterns) اتبنت على شكل الـ Sender ID المعروف اللي
 * البنوك المصرية بتستخدمه في رسائل الـ SMS عادةً (إنجليزي و/أو عربي)، وهي
 * الجزء الأهم والأضمن لأن قراءة الـ SMS هي المسار الأساسي في التطبيق.
 * باكيدجات تطبيقات الموبايل (packages) اتحطّت بأفضل معرفة متاحة، لكن
 * جوجل بلاي بيغيّر أسماء الباكيدجات أحيانًا مع كل تحديث كبير، فلازم
 * تتأكد من الاسم الحقيقي على جهازك قبل ما تعتمد على قراءة الإشعارات
 * (مش الـ SMS) لأي بنك جديد هنا. طريقة التأكد: افتح "معلومات التطبيق"
 * لتطبيق البنك على موبايلك من الإعدادات، أو استخدم أمر
 * `adb shell dumpsys package | grep -i <اسم البنك>` وموبايلك متوصل بالكمبيوتر.
 * لو الباكيدج غلط، أسوأ حالة إن التطبيق مش هيقرا إشعارات البنك ده —
 * قراءة الـ SMS هتفضل شغالة عادي لأنها مش معتمدة على الباكيدج.
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
        // ===== البنوك الموجودة أصلاً =====
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
        ),

        // ===== بنوك جديدة (المرحلة دي) =====
        TransactionSourceSpec(
            bankName = "HSBC Egypt",
            packages = setOf("com.htsu.hsbcpersonalbanking"),
            senderPatterns = listOf("HSBC")
        ),
        TransactionSourceSpec(
            bankName = "AAIB",
            packages = setOf("com.aaib.aaibmobile"),
            senderPatterns = listOf("AAIB", "العربي الأفريقي", "العربى الافريقى")
        ),
        TransactionSourceSpec(
            bankName = "Banque du Caire",
            packages = setOf("com.banqueducaire.mobile"),
            senderPatterns = listOf("BDC", "بنك القاهرة", "CAIRO ?BANK")
        ),
        TransactionSourceSpec(
            bankName = "ADIB Egypt",
            packages = setOf("com.adib.mobile.eg"),
            senderPatterns = listOf("ADIB", "أبوظبي الإسلامي", "ابوظبي الاسلامي")
        ),
        TransactionSourceSpec(
            bankName = "Al Baraka Bank Egypt",
            packages = setOf("com.albaraka.egypt.mobile"),
            senderPatterns = listOf("AL ?BARAKA", "البركة")
        ),
        TransactionSourceSpec(
            bankName = "Attijariwafa Bank Egypt",
            packages = setOf("com.attijariwafa.egypt.mobile"),
            senderPatterns = listOf("ATTIJARI", "التجاري وفا")
        ),
        TransactionSourceSpec(
            bankName = "Credit Agricole Egypt",
            packages = setOf("com.creditagricole.egypt.mobile"),
            senderPatterns = listOf("CAE", "كريدي أجريكول", "كريدى اجريكول")
        ),
        TransactionSourceSpec(
            bankName = "Emirates NBD Egypt",
            packages = setOf("com.enbd.egypt.mobile"),
            senderPatterns = listOf("ENBD", "الإمارات دبي الوطني", "الامارات دبى الوطنى")
        ),
        TransactionSourceSpec(
            bankName = "SAIB",
            packages = setOf("com.saib.mobile"),
            senderPatterns = listOf("SAIB", "العربية الدولية")
        ),
        TransactionSourceSpec(
            bankName = "Suez Canal Bank",
            packages = setOf("com.suezcanalbank.mobile"),
            senderPatterns = listOf("SCB", "قناة السويس")
        ),
        TransactionSourceSpec(
            bankName = "Housing and Development Bank",
            packages = setOf("com.hdb.mobile"),
            senderPatterns = listOf("HDB", "التعمير والإسكان", "التعمير و الاسكان")
        ),
        TransactionSourceSpec(
            bankName = "NBK Egypt",
            packages = setOf("com.nbk.egypt.mobile"),
            senderPatterns = listOf("NBK", "الكويت الوطني")
        ),
        TransactionSourceSpec(
            bankName = "Egyptian Gulf Bank",
            packages = setOf("com.egbank.mobile"),
            senderPatterns = listOf("EGBANK", "المصري الخليجي", "المصرى الخليجى")
        ),

        // ===== محافظ إلكترونية إضافية =====
        TransactionSourceSpec(
            bankName = "أورنج كاش",
            packages = setOf("com.orange.money.eg"),
            senderPatterns = listOf("ORANGE ?CASH", "أورنج كاش", "اورنج كاش"),
            defaultType = TransactionType.EXPENSE
        ),
        TransactionSourceSpec(
            bankName = "اتصالات كاش",
            packages = setOf("com.etisalat.cash.eg"),
            senderPatterns = listOf("ETISALAT ?CASH", "اتصالات كاش"),
            defaultType = TransactionType.EXPENSE
        ),
        TransactionSourceSpec(
            bankName = "we pay",
            packages = setOf("com.te.wepay"),
            senderPatterns = listOf("WE ?PAY", "وي باي"),
            defaultType = TransactionType.EXPENSE
        ),
        TransactionSourceSpec(
            bankName = "فوري",
            packages = setOf("com.fawry.fawrypay"),
            senderPatterns = listOf("FAWRY", "فوري"),
            defaultType = TransactionType.EXPENSE
        ),
        TransactionSourceSpec(
            bankName = "أمان",
            packages = setOf("com.contact.aman"),
            senderPatterns = listOf("AMAN", "أمان"),
            defaultType = TransactionType.EXPENSE
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