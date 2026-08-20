package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ميزانية شهرية. الميزانية الكلية للشهر (المرحلة 7) متخزّنة كصف واحد بالمفتاح
 * [OVERALL_KEY] بدل جدول أو عمود جديد — كل استعلامات الميزانيات بتفضل زي ما هي،
 * والفلترة عليها سطر واحد.
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey
    val categoryName: String,
    val limitMinor: Long           // حد الميزانية بوحدات صغرى (قروش)
) {
    companion object {
        const val OVERALL_KEY = "__OVERALL__"
    }
}
