package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * مشترى مقسّط (لابتوب بـ 24,000 على 12 قسط).
 *
 * مهم: إجمالي المشترى [totalMinor] **مش** بيتسجّل كحركة مالية. اللي بيتسجّل
 * كحركة هو القسط الشهري بس (كل صف حركة بيتوسم بـ Expense.installmentId)،
 * وكده تحليلات الشهر مبتعدّش نفس الفلوس مرتين.
 */
@Entity(tableName = "installments")
data class Installment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val totalMinor: Long,
    val installmentMinor: Long,
    val count: Int,
    @ColumnInfo(defaultValue = "0")
    val paidCount: Int = 0,
    val startDate: Long,
    val nextDueDate: Long,
    @ColumnInfo(defaultValue = "")
    val merchant: String = "",
    @ColumnInfo(defaultValue = "عام")
    val categoryName: String = "عام",
    val accountId: Long? = null,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0
) {
    val remainingCount: Int get() = (count - paidCount).coerceAtLeast(0)
    val remainingMinor: Long get() = remainingCount * installmentMinor
    val paidMinor: Long get() = paidCount * installmentMinor
}
