package com.localexpense.tracker.data

import androidx.room.TypeConverter

/**
 * محوّلات Room. كل الـ enums متخزّنة كنص (اسم القيمة). أي قيمة غير معروفة
 * (مثلاً من نسخة أحدث اترجعت) بترجع القيمة الافتراضية بأمان بدل ما تعمل crash.
 */
class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)

    @TypeConverter
    fun fromTransactionSource(source: TransactionSource): String = source.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource =
        runCatching { TransactionSource.valueOf(value) }.getOrDefault(TransactionSource.MANUAL)

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.OTHER)

    @TypeConverter
    fun fromFrequency(frequency: Frequency): String = frequency.name

    @TypeConverter
    fun toFrequency(value: String): Frequency =
        runCatching { Frequency.valueOf(value) }.getOrDefault(Frequency.MONTHLY)
}
