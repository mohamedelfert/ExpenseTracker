package com.localexpense.tracker.money

/**
 * تنسيق مركزي للمبالغ: كل عرض للمبلغ في التطبيق بيمرّ من هنا عشان الشكل يفضل
 * متسق ومن غير أي حساب عشري في مكان تاني.
 *
 * [formatMinor]: للعرض في الواجهة — بفواصل آلاف ورمز العملة (مثال: "1,250.50 ج.م").
 * [minorToPlainDecimal]: رقم عشري خام من غير فواصل ولا رمز — للتصدير (CSV) وخانات الإدخال.
 */

private fun symbolFor(currency: String): String = when (currency.uppercase()) {
    "EGP" -> "ج.م"
    else -> currency
}

private fun group(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder(s.length + s.length / 3)
    val len = s.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return sb.toString()
}

fun formatMinor(
    amountMinor: Long,
    currency: String = Money.DEFAULT_CURRENCY,
    withDecimals: Boolean = true,
    withSymbol: Boolean = true
): String {
    val negative = amountMinor < 0
    val abs = if (negative) -amountMinor else amountMinor
    val major = abs / 100
    val minor = abs % 100
    val number = if (withDecimals) {
        "${group(major)}.${minor.toString().padStart(2, '0')}"
    } else {
        group(major)
    }
    val sign = if (negative) "-" else ""
    val symbol = if (withSymbol) " ${symbolFor(currency)}" else ""
    return "$sign$number$symbol"
}

/** رقم عشري خام بدون فواصل ولا رمز عملة — للـ CSV وخانات التعديل. */
fun minorToPlainDecimal(amountMinor: Long): String {
    val negative = amountMinor < 0
    val abs = if (negative) -amountMinor else amountMinor
    return "${if (negative) "-" else ""}${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
