package com.localexpense.tracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * الترقية 5 -> 6: تحويل كل المبالغ من Double إلى وحدات صغرى صحيحة (Long)،
 * وإضافة عمودَي [currency] و [type] لجدول المصروفات.
 *
 * بنعيد بناء الجداول (create-copy-drop-rename) لأن DROP COLUMN غير موثوق على
 * minSdk 26، والتحويل بيتم بـ ROUND قبل CAST عشان 125.50 تبقى 12550 مضبوطة
 * (مش 12549 من خطأ تمثيل عشري).
 *
 * مهم: صيغة CREATE TABLE هنا لازم تطابق مخطط Room المولّد للنسخة 6 (راجع
 * app/schemas/...6.json بعد أول build). اختبار Migration5to6Test بيتحقق من ده
 * آليًا عن طريق MigrationTestHelper.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // expenses
        db.execSQL(
            """
            CREATE TABLE expenses_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amountMinor INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'EGP',
                type TEXT NOT NULL DEFAULT 'EXPENSE',
                merchant TEXT NOT NULL,
                bankName TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                rawBody TEXT NOT NULL,
                categoryName TEXT NOT NULL DEFAULT 'عام'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO expenses_new (id, amountMinor, currency, type, merchant, bankName, timestamp, rawBody, categoryName)
            SELECT id, CAST(ROUND(amount * 100) AS INTEGER), 'EGP', 'EXPENSE', merchant, bankName, timestamp, rawBody, categoryName
            FROM expenses
            """.trimIndent()
        )
        db.execSQL("DROP TABLE expenses")
        db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")

        // budgets
        db.execSQL(
            """
            CREATE TABLE budgets_new (
                categoryName TEXT NOT NULL,
                limitMinor INTEGER NOT NULL,
                PRIMARY KEY(categoryName)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO budgets_new (categoryName, limitMinor)
            SELECT categoryName, CAST(ROUND(limitAmount * 100) AS INTEGER)
            FROM budgets
            """.trimIndent()
        )
        db.execSQL("DROP TABLE budgets")
        db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")

        // recurring_expenses
        db.execSQL(
            """
            CREATE TABLE recurring_expenses_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amountMinor INTEGER NOT NULL,
                merchant TEXT NOT NULL,
                bankName TEXT NOT NULL,
                categoryName TEXT NOT NULL,
                dayOfMonth INTEGER NOT NULL,
                lastAddedMonth TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO recurring_expenses_new (id, amountMinor, merchant, bankName, categoryName, dayOfMonth, lastAddedMonth)
            SELECT id, CAST(ROUND(amount * 100) AS INTEGER), merchant, bankName, categoryName, dayOfMonth, lastAddedMonth
            FROM recurring_expenses
            """.trimIndent()
        )
        db.execSQL("DROP TABLE recurring_expenses")
        db.execSQL("ALTER TABLE recurring_expenses_new RENAME TO recurring_expenses")
    }
}
