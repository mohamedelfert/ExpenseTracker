package com.localexpense.tracker.data

import androidx.room.TypeConverter

/**
 * محوّلات Room. [TransactionType] متخزّن كنص (اسم القيمة). أي قيمة غير معروفة
 * (مثلاً من نسخة أحدث اترجعت) بترجع EXPENSE بأمان بدل ما تعمل crash.
 */
class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)
}
