package com.localexpense.tracker.util

/**
 * Egyptian keyboards often type Arabic-Indic (٠١٢٣٤٥٦٧٨٩) or Persian (۰۱۲۳۴۵۶۷۸۹)
 * digits by default. String.toDoubleOrNull() only understands Western digits (0-9)
 * and silently returns null on anything else, which made manual entry look like it
 * "did nothing". This normalises any of the three digit sets (plus Arabic decimal
 * separators) to a plain Western-digit string before parsing.
 */
fun normalizeDigits(input: String): String {
    val builder = StringBuilder(input.length)
    for (c in input) {
        val normalized = when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')          // Arabic-Indic
            in '۰'..'۹' -> '0' + (c - '۰')          // Persian
            '،', '٫' -> '.'                          // Arabic decimal/thousand separators
            ',' -> ' '                                // drop plain thousand separators
            else -> c
        }
        if (normalized != ' ') builder.append(normalized)
    }
    return builder.toString().trim()
}

/**
 * يحوّل نص المبلغ لوحدات صغرى صحيحة (قروش) من غير أي حساب عشري (Double) —
 * ده مسار الفلوس، فلازم يفضل صحيح 100%.
 *
 * أمثلة: "125.50" -> 12550، "125.5" -> 12550، "125" -> 12500،
 * "1,250.75" -> 125075، أكتر من رقمين عشريين بيتقصّوا: "1.999" -> 199.
 * أي نص مش رقم صالح بيرجّع null.
 */
fun parseAmountMinor(input: String): Long? {
    val s = normalizeDigits(input).trim()
    if (s.isEmpty()) return null

    val negative = s.startsWith("-")
    val body = s.removePrefix("-").removePrefix("+")
    if (body.isEmpty()) return null

    val parts = body.split(".")
    if (parts.size > 2) return null

    val intPart = parts[0].ifEmpty { "0" }
    val fracRaw = if (parts.size == 2) parts[1] else ""

    if (!intPart.all { it.isDigit() }) return null
    if (fracRaw.isNotEmpty() && !fracRaw.all { it.isDigit() }) return null

    val frac2 = when {
        fracRaw.isEmpty() -> "00"
        fracRaw.length == 1 -> fracRaw + "0"
        else -> fracRaw.substring(0, 2) // نقطع أي أرقام عشرية زيادة عن رقمين
    }

    val major = intPart.toLongOrNull() ?: return null
    val minor = frac2.toLongOrNull() ?: return null
    val total = major * 100 + minor
    return if (negative) -total else total
}
