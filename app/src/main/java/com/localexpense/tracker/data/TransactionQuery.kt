package com.localexpense.tracker.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** ترتيب نتائج البحث (المرحلة 3). قائمة مقفولة — مفيش نص ترتيب جاي من برة. */
enum class TransactionSort(val label: String, internal val sql: String) {
    NEWEST("الأحدث", "timestamp DESC"),
    OLDEST("الأقدم", "timestamp ASC"),
    AMOUNT_DESC("الأعلى مبلغًا", "amountMinor DESC"),
    AMOUNT_ASC("الأقل مبلغًا", "amountMinor ASC"),
    MERCHANT_ASC("الجهة أ-ي", "merchant ASC"),
    MERCHANT_DESC("الجهة ي-أ", "merchant DESC")
}

/**
 * فلاتر البحث. كلها اختيارية وبتتجمع مع بعضها بـ AND.
 */
data class TransactionFilter(
    val text: String = "",
    val startTime: Long? = null,
    val endTime: Long? = null,
    val types: Set<TransactionType> = emptySet(),
    val categoryName: String? = null,
    val merchant: String? = null,
    val accountId: Long? = null,
    val bankName: String? = null,
    val source: TransactionSource? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val sort: TransactionSort = TransactionSort.NEWEST,
    val limit: Int = 500
) {
    val isEmpty: Boolean
        get() = text.isBlank() && startTime == null && endTime == null && types.isEmpty() &&
            categoryName == null && merchant == null && accountId == null && bankName == null &&
            source == null && minAmountMinor == null && maxAmountMinor == null
}

/**
 * باني استعلام البحث. كل القيم بتتمرّر كمعاملات مربوطة (`?`) — مفيش أي
 * تركيب نصي لقيم جاية من المستخدم، فمفيش SQL injection. الجزء الوحيد اللي
 * بيتركّب نصيًا هو ORDER BY وهو جاي من [TransactionSort] بس.
 */
object TransactionQuery {

    fun build(filter: TransactionFilter): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (filter.text.isNotBlank()) {
            val like = "%${filter.text.trim()}%"
            // البحث بيغطي: الجهة، الفئة، البنك، الملاحظة، رقم المرجع، ونص الرسالة الخام.
            val textColumns = listOf("merchant", "categoryName", "bankName", "note", "referenceId", "rawBody")
            val conditions = textColumns.map { "$it LIKE ?" }.toMutableList()
            textColumns.forEach { _ -> args += like }

            // ولو اللي المستخدم كتبه رقم صالح، بنطابقه كمبلغ كمان.
            val asAmount = com.localexpense.tracker.util.parseAmountMinor(filter.text)
            if (asAmount != null) {
                conditions += "amountMinor = ?"
                args += asAmount
            }

            where += "(${conditions.joinToString(" OR ")})"
        }

        filter.startTime?.let { where += "timestamp >= ?"; args += it }
        filter.endTime?.let { where += "timestamp <= ?"; args += it }

        if (filter.types.isNotEmpty()) {
            where += "type IN (${filter.types.joinToString(",") { "?" }})"
            filter.types.forEach { args += it.name }
        }

        filter.categoryName?.let { where += "categoryName = ?"; args += it }
        filter.merchant?.let { where += "merchant = ?"; args += it }
        filter.accountId?.let { where += "accountId = ?"; args += it }
        filter.bankName?.let { where += "bankName = ?"; args += it }
        filter.source?.let { where += "source = ?"; args += it.name }
        filter.minAmountMinor?.let { where += "amountMinor >= ?"; args += it }
        filter.maxAmountMinor?.let { where += "amountMinor <= ?"; args += it }

        val whereSql = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val sql = "SELECT * FROM expenses $whereSql ORDER BY ${filter.sort.sql} LIMIT ${filter.limit}"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
