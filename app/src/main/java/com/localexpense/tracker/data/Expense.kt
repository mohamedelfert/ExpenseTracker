package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,                                  // المبلغ بوحدات صغرى (قروش): 125.50 ج.م = 12550
    @ColumnInfo(defaultValue = "EGP")
    val currency: String = "EGP",
    @ColumnInfo(defaultValue = "EXPENSE")
    val type: TransactionType = TransactionType.EXPENSE,
    val merchant: String,      // الجهة أو التاجر أو نوع العملية
    val bankName: String,      // اسم البنك (مثل: CIB, BanK-AlAhly, Banque Misr, FAISAL BANK)
    val timestamp: Long,       // التاريخ والوقت بالمللي ثانية
    val rawBody: String,       // النص الكامل للرسالة
    val categoryName: String = "عام"
)
