package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey
    val categoryName: String,
    val limitAmount: Double
)
