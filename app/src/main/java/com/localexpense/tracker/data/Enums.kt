package com.localexpense.tracker.data

/**
 * مصدر الحركة: منين اتسجلت. مفيد في الفلاتر والتصدير وفي تشخيص التكرار
 * (نفس العملية ممكن توصل مرة من الـ SMS ومرة من إشعار تطبيق البنك).
 */
enum class TransactionSource {
    MANUAL,
    SMS,
    NOTIFICATION,
    IMPORT,
    RECURRING
}

/** نوع الحساب/المحفظة. */
enum class AccountType {
    BANK,
    CASH,
    WALLET,
    CREDIT_CARD,
    OTHER
}

/**
 * تكرار الدفعات الدورية والاشتراكات. CUSTOM بيستخدم [RecurringExpense.intervalDays].
 */
enum class Frequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM
}
