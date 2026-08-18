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

fun parseAmount(input: String): Double? = normalizeDigits(input).toDoubleOrNull()
