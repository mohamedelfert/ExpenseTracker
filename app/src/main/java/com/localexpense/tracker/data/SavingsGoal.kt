package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * هدف ادخاري: المستخدم بيحدد اسم وهدف (مبلغ بالقروش) ويضيف مبالغ بمرور الوقت.
 * التقدم بيتحسب كنسبة: savedMinor / targetMinor.
 *
 * مفيش تاريخ نهاية إجباري — الهدف مفتوح المدة. لو المستخدم حب يحدد تاريخ
 * بنخزّنه عشان نعرض countdown، لكنه اختياري.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long = 0L,
    val iconName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deadlineAt: Long? = null,
    val isArchived: Boolean = false
) {
    val progress: Float get() = if (targetMinor > 0) (savedMinor.toFloat() / targetMinor).coerceIn(0f, 1f) else 0f
    val percentDone: Int get() = (progress * 100).toInt()
    val remainingMinor: Long get() = (targetMinor - savedMinor).coerceAtLeast(0L)
    val isComplete: Boolean get() = savedMinor >= targetMinor
}
