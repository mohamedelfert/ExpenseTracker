package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,          // المبلغ بوحدات صغرى (قروش)
    val merchant: String,
    val bankName: String,
    val categoryName: String,
    val dayOfMonth: Int,
    val lastAddedMonth: String // Format: "yyyy-MM" to track if it was added this month
)
