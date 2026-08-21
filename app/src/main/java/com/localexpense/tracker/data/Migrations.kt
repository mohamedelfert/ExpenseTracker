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

/**
 * الترقية 6 -> 7: كل ما تحتاجه المراحل 3 حتى 20 من مخطط، في ترقية واحدة.
 *
 * إضافية بالكامل (ADD COLUMN / CREATE TABLE / CREATE INDEX) — مفيش أي إعادة
 * بناء لجدول ولا نسخ بيانات، فبيانات النسخة 6 محفوظة بحكم التكوين نفسه.
 * السبب في ترقية واحدة بدل خمستاشر: كل ترقية مكتوبة بإيد ومش متبنية محليًا
 * هي مخاطرة identity-hash مستقلة، فبنقلّل العدد لواحدة تتراجع مرة واحدة قصاد
 * app/schemas/7.json بعد أول build.
 *
 * ملاحظة على DEFAULT: لازم تطابق `@ColumnInfo(defaultValue = ...)` في الـ
 * entity حرفيًا، وأسماء الفهارس لازم تطابق أسماء Room المولّدة
 * (`index_<table>_<column>`)، وإلا Room بيرمي خطأ أول ما يفتح القاعدة.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ===== expenses: أعمدة الحركة الكاملة =====
        db.execSQL("ALTER TABLE expenses ADD COLUMN merchantId INTEGER")
        db.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER")
        db.execSQL("ALTER TABLE expenses ADD COLUMN toAccountId INTEGER")
        db.execSQL("ALTER TABLE expenses ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL("ALTER TABLE expenses ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN referenceId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses ADD COLUMN rawHash TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN installmentId INTEGER")
        db.execSQL("ALTER TABLE expenses ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        // الصفوف القديمة: وقت التسجيل = وقت العملية، والمصدر بيتخمّن من البيانات
        // الموجودة فعلاً (مفيش عمود قديم بيقول المصدر).
        db.execSQL("UPDATE expenses SET createdAt = timestamp, updatedAt = timestamp")
        db.execSQL(
            """
            UPDATE expenses SET source = CASE
                WHEN bankName = 'يدوي' THEN 'MANUAL'
                WHEN rawBody LIKE 'Recurring:%' THEN 'RECURRING'
                WHEN rawBody = '' THEN 'MANUAL'
                ELSE 'SMS'
            END
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_timestamp ON expenses (timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_type ON expenses (type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_categoryName ON expenses (categoryName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_merchant ON expenses (merchant)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_accountId ON expenses (accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_merchantId ON expenses (merchantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_referenceId ON expenses (referenceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_rawHash ON expenses (rawHash)")

        // ===== recurring_expenses: تكرارات غير شهرية + اشتراكات =====
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN frequency TEXT NOT NULL DEFAULT 'MONTHLY'")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN nextDueDate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN accountId INTEGER")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN isSubscription INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN reminderDaysBefore INTEGER NOT NULL DEFAULT 1")

        // ===== accounts =====
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                currency TEXT NOT NULL DEFAULT 'EGP',
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // ===== merchants =====
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchants (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                categoryName TEXT NOT NULL DEFAULT '',
                icon TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_normalizedName ON merchants (normalizedName)")

        // ===== merchant_rules =====
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                pattern TEXT NOT NULL,
                categoryName TEXT NOT NULL,
                priority INTEGER NOT NULL DEFAULT 10,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rules_pattern ON merchant_rules (pattern)")

        // ===== installments =====
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS installments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                totalMinor INTEGER NOT NULL,
                installmentMinor INTEGER NOT NULL,
                count INTEGER NOT NULL,
                paidCount INTEGER NOT NULL DEFAULT 0,
                startDate INTEGER NOT NULL,
                nextDueDate INTEGER NOT NULL,
                merchant TEXT NOT NULL DEFAULT '',
                categoryName TEXT NOT NULL DEFAULT 'عام',
                accountId INTEGER,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

/**
 * الترقية 7 -> 8: جدول أهداف الادخار (Savings Goals Tracker).
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS savings_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                targetMinor INTEGER NOT NULL,
                savedMinor INTEGER NOT NULL DEFAULT 0,
                iconName TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                deadlineAt INTEGER,
                isArchived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
