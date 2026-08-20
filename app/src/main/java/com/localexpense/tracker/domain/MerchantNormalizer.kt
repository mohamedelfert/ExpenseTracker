package com.localexpense.tracker.domain

/**
 * تطبيع اسم الجهة عشان "TALABAT*CAIRO"، "Talabat.com"، و "talabat  " كلهم
 * يبقوا مفتاح واحد. بيشيل: حالة الأحرف، التشكيل العربي، علامات الترقيم،
 * الأرقام (أرقام الفروع/البطاقات)، والمسافات الزيادة.
 *
 * التطبيع مقصود إنه بسيط ومحدّد سلفًا (مفيش مطابقة تقريبية/fuzzy) عشان
 * التصنيف يفضل قابل للتفسير: نفس الاسم دايمًا بيدي نفس المفتاح.
 */
private val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
private val NON_WORD = Regex("[^\\p{L} ]")     // بيسيب الحروف (عربي/لاتيني) والمسافة بس
private val MULTI_SPACE = Regex(" +")

fun normalizeMerchant(name: String): String =
    name.lowercase()
        .replace(ARABIC_DIACRITICS, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(NON_WORD, " ")
        .replace(MULTI_SPACE, " ")
        .trim()
