package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * حساب أو محفظة (بنك، كاش، محفظة موبايل، بطاقة ائتمان). اختياري تمامًا:
 * التطبيق بيشتغل بالكامل من غير ما المستخدم يعرّف أي حساب، وساعتها
 * [Expense.accountId] بيفضل null.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.BANK,
    @ColumnInfo(defaultValue = "EGP")
    val currency: String = "EGP",
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0
)
