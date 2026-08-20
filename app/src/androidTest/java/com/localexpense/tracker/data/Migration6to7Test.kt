package com.localexpense.tracker.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبار ترقية 6 -> 7 (مخطط المراحل 3-20). لازم يتشغّل على جهاز/محاكي.
 *
 * بيتحقق من تلات حاجات:
 * 1) بيانات النسخة 6 بتفضل زي ما هي (الترقية إضافية بالكامل).
 * 2) الأعمدة الجديدة اتعبّت صح: createdAt = timestamp، والمصدر اتخمّن من
 *    البيانات الموجودة (يدوي / دورية / SMS).
 * 3) المخطط الناتج مطابق للنسخة 7 المولّدة من Room — runMigrationsAndValidate
 *    بيرمي لو فيه أي اختلاف في نوع أو NOT NULL أو DEFAULT أو اسم فهرس.
 *
 * النسخة 6 بتتبني بالإيد بـ SQL: exportSchema اتفتح مع النسخة 6 لكن المشروع
 * ما اتبناش وقتها، فمفيش app/schemas/6.json عشان createDatabase تستخدمه.
 * الـ DDL تحت هو مخطط النسخة 6 بالظبط (نفس اللي MIGRATION_5_6 بيوصّل له).
 */
@RunWith(AndroidJUnit4::class)
class Migration6to7Test {

    private val testDb = "migration-6-7-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_preservesData_backfillsColumns_andValidatesSchema() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = ctx.getDatabasePath(testDb)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).let { db ->
            db.execSQL(
                "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "amountMinor INTEGER NOT NULL, currency TEXT NOT NULL DEFAULT 'EGP', " +
                    "type TEXT NOT NULL DEFAULT 'EXPENSE', merchant TEXT NOT NULL, bankName TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, rawBody TEXT NOT NULL, categoryName TEXT NOT NULL DEFAULT 'عام')"
            )
            db.execSQL(
                "CREATE TABLE budgets (categoryName TEXT NOT NULL, limitMinor INTEGER NOT NULL, " +
                    "PRIMARY KEY(categoryName))"
            )
            db.execSQL(
                "CREATE TABLE recurring_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "amountMinor INTEGER NOT NULL, merchant TEXT NOT NULL, bankName TEXT NOT NULL, " +
                    "categoryName TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, lastAddedMonth TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, isBuiltIn INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE sms_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "bankName TEXT NOT NULL, senderPattern TEXT NOT NULL, debitKeywordPattern TEXT NOT NULL, " +
                    "amountPattern TEXT NOT NULL, merchantPattern TEXT NOT NULL, isEnabled INTEGER NOT NULL, " +
                    "isBuiltIn INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO expenses (amountMinor, currency, type, merchant, bankName, timestamp, rawBody, categoryName) " +
                    "VALUES (12550, 'EGP', 'EXPENSE', 'Talabat', 'CIB', 1000, 'خصم مبلغ 125.50', 'مطاعم')"
            )
            db.execSQL(
                "INSERT INTO expenses (amountMinor, currency, type, merchant, bankName, timestamp, rawBody, categoryName) " +
                    "VALUES (5000, 'EGP', 'EXPENSE', 'قهوة', 'يدوي', 2000, '', 'عام')"
            )
            db.execSQL(
                "INSERT INTO expenses (amountMinor, currency, type, merchant, bankName, timestamp, rawBody, categoryName) " +
                    "VALUES (35000, 'EGP', 'EXPENSE', 'Netflix', 'CIB', 3000, 'Recurring: Netflix', 'ترفيه')"
            )
            db.execSQL("INSERT INTO budgets (categoryName, limitMinor) VALUES ('مطاعم', 500000)")
            db.execSQL(
                "INSERT INTO recurring_expenses (amountMinor, merchant, bankName, categoryName, dayOfMonth, lastAddedMonth) " +
                    "VALUES (35000, 'Netflix', 'CIB', 'ترفيه', 25, '')"
            )
            db.version = 6
            db.close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true, MIGRATION_6_7)

        // البيانات القديمة موجودة زي ما هي
        db.query("SELECT COUNT(*) FROM expenses").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
        db.query("SELECT limitMinor FROM budgets WHERE categoryName = 'مطاعم'").use { c ->
            c.moveToFirst()
            assertEquals(500000L, c.getLong(0))
        }

        // الأعمدة الجديدة اتعبّت
        db.query("SELECT source, createdAt, updatedAt, note, referenceId, isVerified FROM expenses WHERE merchant = 'Talabat'").use { c ->
            c.moveToFirst()
            assertEquals("SMS", c.getString(0))
            assertEquals(1000L, c.getLong(1))
            assertEquals(1000L, c.getLong(2))
            assertEquals("", c.getString(3))
            assertEquals("", c.getString(4))
            assertEquals(0, c.getInt(5))
        }
        db.query("SELECT source FROM expenses WHERE merchant = 'قهوة'").use { c ->
            c.moveToFirst()
            assertEquals("MANUAL", c.getString(0))
        }
        db.query("SELECT source FROM expenses WHERE merchant = 'Netflix'").use { c ->
            c.moveToFirst()
            assertEquals("RECURRING", c.getString(0))
        }

        // الجداول الجديدة موجودة وفاضية
        listOf("accounts", "merchants", "merchant_rules", "installments").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }

        // الدوريات كسبت أعمدة التكرار بقيمها الافتراضية
        db.query("SELECT COUNT(*) FROM recurring_expenses WHERE frequency = 'MONTHLY' AND isActive = 1").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }
}
