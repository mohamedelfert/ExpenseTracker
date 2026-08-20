package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * دفعة دورية. **الاشتراك (نتفليكس مثلاً) هو نفس الشيء بـ [isSubscription] = true**
 * — نفس الجدول ونفس المُجدوِل وفلاج واحد، بدل جدول تاني بنفس الأعمدة بالظبط.
 *
 * [dayOfMonth] و [lastAddedMonth] متسيبين زي ما هم عشان المنطق الشهري القديم
 * يفضل شغّال؛ [nextDueDate] هو الأساس للتكرارات غير الشهرية.
 */
@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,          // المبلغ بوحدات صغرى (قروش)
    val merchant: String,
    val bankName: String,
    val categoryName: String,
    val dayOfMonth: Int,
    val lastAddedMonth: String, // "yyyy-MM" - آخر شهر اتسجلت فيه (للتكرار الشهري)

    // ===== المضاف في النسخة 7 =====
    @ColumnInfo(defaultValue = "")
    val name: String = "",                  // فاضي = استخدم [merchant]
    @ColumnInfo(defaultValue = "MONTHLY")
    val frequency: Frequency = Frequency.MONTHLY,
    @ColumnInfo(defaultValue = "30")
    val intervalDays: Int = 30,             // بيستخدم مع Frequency.CUSTOM بس
    @ColumnInfo(defaultValue = "0")
    val nextDueDate: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
    val accountId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val isSubscription: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val reminderDaysBefore: Int = 1
) {
    val displayName: String get() = name.ifBlank { merchant }
}
