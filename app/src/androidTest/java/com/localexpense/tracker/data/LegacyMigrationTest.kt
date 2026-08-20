package com.localexpense.tracker.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * الترقية الاستكشافية من النسخ القديمة (1-4). لازم تتشغّل على جهاز/محاكي.
 *
 * مخطط النسخ دي مجهول فعلاً، فالاختبار بيقلّد أسوأ حالة معقولة: أسماء أعمدة
 * مختلفة (`place` بدل `merchant`، `bank` بدل `bankName`، `date` بدل
 * `timestamp`)، أعمدة ناقصة (مفيش فئة ولا نص رسالة)، وجداول ناقصة بالكامل
 * (مفيش categories ولا sms_rules).
 *
 * المطلوب إثباته: البيانات بتوصل للنسخة 7 سليمة، والمخطط النهائي مطابق للي
 * Room مولّده (runMigrationsAndValidate بيتحقق من ده).
 */
@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {

    private val testDb = "legacy-migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun createLegacyDatabase(version: Int, build: SQLiteDatabase.() -> Unit) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = ctx.getDatabasePath(testDb)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).apply {
            build()
            this.version = version
            close()
        }
    }

    @Test
    fun legacyV3WithDifferentColumnNames_isSalvaged() {
        createLegacyDatabase(3) {
            // أسماء تانية خالص، وأعمدة ناقصة
            execSQL(
                "CREATE TABLE expenses (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "amount REAL NOT NULL, place TEXT, bank TEXT, date INTEGER)"
            )
            execSQL("INSERT INTO expenses (amount, place, bank, date) VALUES (125.5, 'Talabat', 'CIB', 1000)")
            execSQL("INSERT INTO expenses (amount, place, bank, date) VALUES (40.0, 'Uber', 'NBE', 2000)")
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 7, true, *LEGACY_MIGRATIONS, MIGRATION_5_6, MIGRATION_6_7
        )

        // الصفوف وصلت، والمبالغ اتحولت لوحدات صغرى في 5->6
        db.query("SELECT amountMinor, merchant, bankName, timestamp, categoryName, source FROM expenses ORDER BY timestamp").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(12550L, c.getLong(0))
            assertEquals("Talabat", c.getString(1))
            assertEquals("CIB", c.getString(2))
            assertEquals(1000L, c.getLong(3))
            assertEquals("عام", c.getString(4))      // العمود الناقص خد الافتراضي
            c.moveToNext()
            assertEquals(4000L, c.getLong(0))
        }

        // الجداول الناقصة اتعملت، وجداول النسخة 7 موجودة
        listOf("categories", "sms_rules", "budgets", "recurring_expenses",
               "accounts", "merchants", "merchant_rules", "installments").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun legacyV1WithNoRecognisableTables_stillOpens() {
        // أسوأ حالة: مفيش أي جدول نعرفه. المطلوب: مفيش crash، والمخطط سليم.
        createLegacyDatabase(1) {
            execSQL("CREATE TABLE some_old_junk (id INTEGER PRIMARY KEY, note TEXT)")
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 7, true, *LEGACY_MIGRATIONS, MIGRATION_5_6, MIGRATION_6_7
        )

        db.query("SELECT COUNT(*) FROM expenses").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun legacyV4WithBudgetsAndRecurring_keepsThem() {
        createLegacyDatabase(4) {
            execSQL(
                "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "amount REAL NOT NULL, merchant TEXT NOT NULL, bankName TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, rawBody TEXT NOT NULL, categoryName TEXT NOT NULL)"
            )
            execSQL("CREATE TABLE budgets (category TEXT PRIMARY KEY NOT NULL, amount REAL NOT NULL)")
            execSQL("INSERT INTO budgets (category, amount) VALUES ('مطاعم', 500.0)")
            execSQL(
                "CREATE TABLE recurring_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "amount REAL NOT NULL, name TEXT, bank TEXT, category TEXT, day INTEGER)"
            )
            execSQL("INSERT INTO recurring_expenses (amount, name, bank, category, day) VALUES (350.0, 'Netflix', 'CIB', 'ترفيه', 25)")
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 7, true, *LEGACY_MIGRATIONS, MIGRATION_5_6, MIGRATION_6_7
        )

        db.query("SELECT limitMinor FROM budgets WHERE categoryName = 'مطاعم'").use { c ->
            c.moveToFirst()
            assertEquals(50000L, c.getLong(0))
        }
        db.query("SELECT amountMinor, merchant, dayOfMonth, frequency FROM recurring_expenses").use { c ->
            c.moveToFirst()
            assertEquals(35000L, c.getLong(0))
            assertEquals("Netflix", c.getString(1))
            assertEquals(25, c.getInt(2))
            assertEquals("MONTHLY", c.getString(3))
        }
    }
}
