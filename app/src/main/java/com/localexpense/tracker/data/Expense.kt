package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val source: String,          // e.g. "البنك الأهلي", "InstaPay", "يدوي"
    val timestampMillis: Long,   // date + time extracted from the message
    val rawMessage: String,      // original SMS text, kept for auditing
    val category: String = "غير مصنف",
    val isConfirmed: Boolean = true, // false if parsing confidence was low
    val createdAt: Long = System.currentTimeMillis()
)
