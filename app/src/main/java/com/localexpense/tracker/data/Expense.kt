package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * جدول الحركات المالية. الاسم لسه `Expense` (وجدول `expenses`) لأنه هو نفسه
 * جدول كل الأنواع: مصروف، دخل، تحويل، استرداد — راجع [type]. تغيير الاسم كان
 * هيمس كل ملف في المشروع من غير أي فايدة للمستخدم.
 *
 * كل الحقول الجديدة ليها قيمة افتراضية على مستوى الـ SQL كمان (راجع
 * MIGRATION_6_7) عشان صفوف النسخة 6 تفضل صالحة من غير إعادة بناء الجدول.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index("timestamp"),
        Index("type"),
        Index("categoryName"),
        Index("merchant"),
        Index("accountId"),
        Index("merchantId"),
        Index("referenceId"),
        Index("rawHash")
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,                                  // المبلغ بوحدات صغرى (قروش): 125.50 ج.م = 12550
    @ColumnInfo(defaultValue = "EGP")
    val currency: String = "EGP",
    @ColumnInfo(defaultValue = "EXPENSE")
    val type: TransactionType = TransactionType.EXPENSE,
    val merchant: String,      // الجهة أو التاجر أو نوع العملية (النص المعروض)
    val bankName: String,      // اسم البنك (مثل: CIB, Bank-AlAhly, Banque Misr)
    val timestamp: Long,       // التاريخ والوقت بالمللي ثانية
    val rawBody: String,       // النص الكامل للرسالة (مش بيتصدّر في CSV إلا بطلب صريح)
    val categoryName: String = "عام",

    // ===== الحقول المضافة في النسخة 7 =====

    /** ربط بجدول [Merchant] لما الجهة تتعرّف؛ null = لسه متعرفتش. */
    val merchantId: Long? = null,
    /** الحساب اللي اتخصم/اتودع منه؛ null مسموح - الحسابات اختيارية بالكامل. */
    val accountId: Long? = null,
    /** حساب الوصول في التحويلات فقط (type = TRANSFER). */
    val toAccountId: Long? = null,
    @ColumnInfo(defaultValue = "MANUAL")
    val source: TransactionSource = TransactionSource.MANUAL,
    @ColumnInfo(defaultValue = "")
    val note: String = "",
    /** رقم مرجع العملية لو البنك بعته في الرسالة - أقوى مفتاح لمنع التكرار. */
    @ColumnInfo(defaultValue = "")
    val referenceId: String = "",
    @ColumnInfo(defaultValue = "0")
    val isVerified: Boolean = false,
    /** بصمة ثابتة لنص الرسالة (مصدر + نص) - بتمنع تسجيل نفس الرسالة مرتين. */
    @ColumnInfo(defaultValue = "")
    val rawHash: String = "",
    /** لو الصف ده قسط من [Installment] معيّن. إجمالي المشترى نفسه مش بيتسجّل كحركة. */
    val installmentId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0
) {
    /** المبلغ بإشارته الحسابية: الدخل والاسترداد موجب، المصروف سالب، التحويل صفر. */
    val signedMinor: Long
        get() = when (type) {
            TransactionType.INCOME, TransactionType.REFUND -> amountMinor
            TransactionType.EXPENSE -> -amountMinor
            TransactionType.TRANSFER -> 0L
        }
}
