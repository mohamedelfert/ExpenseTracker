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
 * اختبار ترقية 5 -> 6. لازم يتشغّل على جهاز/محاكي (instrumented).
 *
 * بيتحقق من حاجتين:
 * 1) بيانات النسخة 5 بتتحوّل صح لوحدات صغرى (125.5 -> 12550، 5000.0 -> 500000).
 * 2) المخطط الناتج مطابق تمامًا لمخطط Room المولّد للنسخة 6 (runMigrationsAndValidate
 *    بيرمي لو فيه أي اختلاف — ده بيمسك خطأ عدم تطابق الـ DDL/الـ defaults اللي
 *    ممكن يعمل crash وقت التشغيل الحقيقي).
 *
 * ملاحظة: بننشئ النسخة 5 يدويًا لأن exportSchema كان false وقتها فمفيش JSON
 * للنسخة 5. لو الـ validate اشتكى، طابق الـ CREATE TABLE هنا و/أو في
 * MIGRATION_5_6 مع app/schemas/..._6.json المولّد بعد أول build.
 */
@RunWith(AndroidJUnit4::class)
class Migration5to6Test {

    private val testDb = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate5To6_convertsAmounts_andValidatesSchema() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = ctx.getDatabasePath(testDb)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // إنشاء قاعدة بيانات نسخة 5 يدويًا بنفس مخطط النسخة القديمة.
        val v5 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v5.execSQL(
            "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amount REAL NOT NULL, merchant TEXT NOT NULL, bankName TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL, rawBody TEXT NOT NULL, categoryName TEXT NOT NULL DEFAULT 'عام')"
        )
        v5.execSQL(
            "CREATE TABLE budgets (categoryName TEXT NOT NULL, limitAmount REAL NOT NULL, " +
                "PRIMARY KEY(categoryName))"
        )
        v5.execSQL(
            "CREATE TABLE recurring_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amount REAL NOT NULL, merchant TEXT NOT NULL, bankName TEXT NOT NULL, " +
                "categoryName TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, lastAddedMonth TEXT NOT NULL)"
        )
        v5.execSQL(
            "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, isBuiltIn INTEGER NOT NULL)"
        )
        v5.execSQL(
            "CREATE TABLE sms_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bankName TEXT NOT NULL, senderPattern TEXT NOT NULL, debitKeywordPattern TEXT NOT NULL, " +
                "amountPattern TEXT NOT NULL, merchantPattern TEXT NOT NULL, isEnabled INTEGER NOT NULL, " +
                "isBuiltIn INTEGER NOT NULL)"
        )
        v5.execSQL(
            "INSERT INTO expenses (amount, merchant, bankName, timestamp, rawBody, categoryName) " +
                "VALUES (125.5, 'Talabat', 'CIB', 1000, 'raw', 'عام')"
        )
        v5.execSQL("INSERT INTO budgets (categoryName, limitAmount) VALUES ('عام', 5000.0)")
        v5.version = 5
        v5.close()

        // تطبيق الترقية + التحقق من مطابقة المخطط للنسخة 6 المولّدة.
        val db = helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_5_6)

        db.query("SELECT amountMinor, currency, type FROM expenses").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(12550L, c.getLong(0))
            assertEquals("EGP", c.getString(1))
            assertEquals("EXPENSE", c.getString(2))
        }
        db.query("SELECT limitMinor FROM budgets").use { c ->
            c.moveToFirst()
            assertEquals(500000L, c.getLong(0))
        }
    }
}
