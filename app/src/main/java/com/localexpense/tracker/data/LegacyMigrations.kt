package com.localexpense.tracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ترقية النسخ القديمة (1 حتى 4) لمخطط النسخة 5 — آخر ثغرة كانت لسه بتضيّع
 * بيانات مستخدمين.
 *
 * المشكلة: النسخ دي اتشحنت وقت ما `exportSchema` كان false ومفيش git history
 * ليها، فمخططها **مجهول تمامًا**. الحل القديم كان
 * `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)` — يعني مسح بيانات أي حد لسه
 * على نسخة قديمة.
 *
 * الحل هنا: ترقية **استكشافية**. مش بنفترض شكل الجدول، بنقراه فعلاً وقت
 * التشغيل (`sqlite_master` + `PRAGMA table_info`)، وبننسخ الأعمدة الموجودة بس،
 * وأي عمود ناقص بياخد قيمة افتراضية. أسماء الأعمدة والجداول المحتملة مجرّبة
 * بالترتيب، لأن الإصدارات القديمة ممكن كانت بتسمّيها بأي حاجة.
 *
 * قاعدة أساسية: **الدالة دي عمرها ما ترمي استثناء.** أي جدول يفشل نسخه بيتعمل
 * فاضي بشكل النسخة 5 — نفس نتيجة الحل القديم بس لجدول واحد بدل القاعدة كلها،
 * لأن تطبيق بيعمل crash على كل فتح أسوأ بكتير من جدول ناقص.
 *
 * بعد ما توصل لـ 5، الترقيتين العاديتين (5→6 و 6→7) بيكملوا الباقي.
 */

private val EXPENSE_TABLES = listOf("expenses", "expense", "transactions", "transaction_table")
private val BUDGET_TABLES = listOf("budgets", "budget")
private val RECURRING_TABLES = listOf("recurring_expenses", "recurring", "recurring_expense")

private val AMOUNT_COLUMNS = listOf("amount", "amountValue", "value", "total", "price", "cost")
private val LIMIT_COLUMNS = listOf("limitAmount", "limit_amount", "amount", "limitValue", "monthlyLimit")
private val ID_COLUMNS = listOf("id", "_id")
private val MERCHANT_COLUMNS = listOf("merchant", "place", "vendor", "title", "name", "description")
private val BANK_COLUMNS = listOf("bankName", "bank_name", "bank", "source", "sender")
private val TIME_COLUMNS = listOf("timestamp", "date", "createdAt", "created_at", "time", "millis")
private val BODY_COLUMNS = listOf("rawBody", "raw_body", "body", "message", "rawMessage", "sms")
private val CATEGORY_COLUMNS = listOf("categoryName", "category_name", "category")

/** الترقيات الأربعة الجاهزة للتسجيل في Room. */
val LEGACY_MIGRATIONS: Array<Migration> = arrayOf(
    legacyMigrationToV5(1),
    legacyMigrationToV5(2),
    legacyMigrationToV5(3),
    legacyMigrationToV5(4)
)

fun legacyMigrationToV5(fromVersion: Int): Migration = object : Migration(fromVersion, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        salvageExpenses(db)
        salvageBudgets(db)
        salvageRecurring(db)
        ensureTable(db, "categories", CREATE_CATEGORIES_V5)
        ensureTable(db, "sms_rules", CREATE_SMS_RULES_V5)
    }
}

// ===== أدوات استكشاف المخطط =====

private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
    runCatching {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.moveToFirst() }
    }.getOrDefault(false)

private fun findTable(db: SupportSQLiteDatabase, candidates: List<String>): String? =
    candidates.firstOrNull { tableExists(db, it) }

private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
    runCatching {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }
    }.getOrDefault(emptySet())

/**
 * تعبير SELECT لعمود: أول اسم موجود من [candidates]، وإلا [fallbackLiteral]
 * (لازم يكون قيمة SQL حرفية: رقم أو نص بين علامتين).
 */
private fun pick(available: Set<String>, candidates: List<String>, fallbackLiteral: String): String =
    candidates.firstOrNull { it in available }?.let { "`$it`" } ?: fallbackLiteral

// ===== مخطط النسخة 5 (لازم يطابق اللي MIGRATION_5_6 بيتوقعه) =====

private const val CREATE_EXPENSES_V5 =
    "CREATE TABLE IF NOT EXISTS expenses_v5 (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, " +
        "merchant TEXT NOT NULL, bankName TEXT NOT NULL, timestamp INTEGER NOT NULL, " +
        "rawBody TEXT NOT NULL, categoryName TEXT NOT NULL DEFAULT 'عام')"

private const val CREATE_BUDGETS_V5 =
    "CREATE TABLE IF NOT EXISTS budgets_v5 (" +
        "categoryName TEXT NOT NULL, limitAmount REAL NOT NULL, PRIMARY KEY(categoryName))"

private const val CREATE_RECURRING_V5 =
    "CREATE TABLE IF NOT EXISTS recurring_v5 (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, " +
        "merchant TEXT NOT NULL, bankName TEXT NOT NULL, categoryName TEXT NOT NULL, " +
        "dayOfMonth INTEGER NOT NULL, lastAddedMonth TEXT NOT NULL)"

private const val CREATE_CATEGORIES_V5 =
    "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isBuiltIn INTEGER NOT NULL)"

private const val CREATE_SMS_RULES_V5 =
    "CREATE TABLE IF NOT EXISTS sms_rules (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bankName TEXT NOT NULL, " +
        "senderPattern TEXT NOT NULL, debitKeywordPattern TEXT NOT NULL, " +
        "amountPattern TEXT NOT NULL, merchantPattern TEXT NOT NULL, " +
        "isEnabled INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL)"

// ===== نسخ الجداول =====

private fun salvageExpenses(db: SupportSQLiteDatabase) {
    val source = findTable(db, EXPENSE_TABLES)
    try {
        db.execSQL(CREATE_EXPENSES_V5)
        if (source != null) {
            val columns = columnsOf(db, source)
            db.execSQL(
                "INSERT INTO expenses_v5 (id, amount, merchant, bankName, timestamp, rawBody, categoryName) " +
                    "SELECT ${pick(columns, ID_COLUMNS, "NULL")}, " +
                    "CAST(${pick(columns, AMOUNT_COLUMNS, "0.0")} AS REAL), " +
                    "CAST(${pick(columns, MERCHANT_COLUMNS, "'غير محدد'")} AS TEXT), " +
                    "CAST(${pick(columns, BANK_COLUMNS, "'بنك آخر'")} AS TEXT), " +
                    "CAST(${pick(columns, TIME_COLUMNS, "0")} AS INTEGER), " +
                    "CAST(${pick(columns, BODY_COLUMNS, "''")} AS TEXT), " +
                    "CAST(${pick(columns, CATEGORY_COLUMNS, "'عام'")} AS TEXT) FROM `$source`"
            )
            db.execSQL("DROP TABLE `$source`")
        }
        db.execSQL("ALTER TABLE expenses_v5 RENAME TO expenses")
    } catch (e: Exception) {
        e.printStackTrace()
        resetTable(db, "expenses", "expenses_v5", CREATE_EXPENSES_V5)
    }
}

private fun salvageBudgets(db: SupportSQLiteDatabase) {
    val source = findTable(db, BUDGET_TABLES)
    try {
        db.execSQL(CREATE_BUDGETS_V5)
        if (source != null) {
            val columns = columnsOf(db, source)
            db.execSQL(
                "INSERT OR REPLACE INTO budgets_v5 (categoryName, limitAmount) " +
                    "SELECT CAST(${pick(columns, CATEGORY_COLUMNS + listOf("name"), "'عام'")} AS TEXT), " +
                    "CAST(${pick(columns, LIMIT_COLUMNS, "0.0")} AS REAL) FROM `$source`"
            )
            db.execSQL("DROP TABLE `$source`")
        }
        db.execSQL("ALTER TABLE budgets_v5 RENAME TO budgets")
    } catch (e: Exception) {
        e.printStackTrace()
        resetTable(db, "budgets", "budgets_v5", CREATE_BUDGETS_V5)
    }
}

private fun salvageRecurring(db: SupportSQLiteDatabase) {
    val source = findTable(db, RECURRING_TABLES)
    try {
        db.execSQL(CREATE_RECURRING_V5)
        if (source != null) {
            val columns = columnsOf(db, source)
            db.execSQL(
                "INSERT INTO recurring_v5 (id, amount, merchant, bankName, categoryName, dayOfMonth, lastAddedMonth) " +
                    "SELECT ${pick(columns, ID_COLUMNS, "NULL")}, " +
                    "CAST(${pick(columns, AMOUNT_COLUMNS, "0.0")} AS REAL), " +
                    "CAST(${pick(columns, MERCHANT_COLUMNS, "'غير محدد'")} AS TEXT), " +
                    "CAST(${pick(columns, BANK_COLUMNS, "'كاش'")} AS TEXT), " +
                    "CAST(${pick(columns, CATEGORY_COLUMNS, "'عام'")} AS TEXT), " +
                    "CAST(${pick(columns, listOf("dayOfMonth", "day_of_month", "day"), "1")} AS INTEGER), " +
                    "CAST(${pick(columns, listOf("lastAddedMonth", "last_added_month", "lastAdded"), "''")} AS TEXT) " +
                    "FROM `$source`"
            )
            db.execSQL("DROP TABLE `$source`")
        }
        db.execSQL("ALTER TABLE recurring_v5 RENAME TO recurring_expenses")
    } catch (e: Exception) {
        e.printStackTrace()
        resetTable(db, "recurring_expenses", "recurring_v5", CREATE_RECURRING_V5)
    }
}

/** آخر حل لجدول فشل نسخه: جدول فاضي بشكل النسخة 5 بدل ما القاعدة كلها تفشل. */
private fun resetTable(
    db: SupportSQLiteDatabase,
    finalName: String,
    tempName: String,
    createTempSql: String
) {
    runCatching {
        db.execSQL("DROP TABLE IF EXISTS `$tempName`")
        db.execSQL("DROP TABLE IF EXISTS `$finalName`")
        db.execSQL(createTempSql)
        db.execSQL("ALTER TABLE `$tempName` RENAME TO `$finalName`")
    }
}

/** جدول موجود في النسخة 5 لكن ممكن ميكونش موجود في النسخة القديمة. */
private fun ensureTable(db: SupportSQLiteDatabase, name: String, createSql: String) {
    if (tableExists(db, name)) return
    runCatching { db.execSQL(createSql) }
}
