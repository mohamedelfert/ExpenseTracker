package com.localexpense.tracker.data

data class SourceTotal(
    val bankName: String,
    val total: Long            // إجمالي بوحدات صغرى (قروش)
)