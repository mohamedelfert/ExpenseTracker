package com.localexpense.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Expense::class, SmsRule::class, Category::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun smsRuleDao(): SmsRuleDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance

                CoroutineScope(Dispatchers.IO).launch {
                    seedDefaultRulesIfEmpty(instance.smsRuleDao())
                    seedDefaultCategoriesIfEmpty(instance.categoryDao())
                }

                instance
            }
        }

        /**
         * Best-effort starting rules for common Egyptian senders. These are
         * approximations of typical wording — open "قواعد الرسائل" > اختبار
         * على رسالة حقيقية وعدّل الـ Regex لحد ما يطابق رسايلك بالظبط.
         */
        private suspend fun seedDefaultRulesIfEmpty(dao: SmsRuleDao) {
            if (dao.count() > 0) return

            val amountGeneric = """(?:مبلغ|EGP|LE|بقيمة)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)"""
            val merchantGeneric = """(?:في|لدى|at|من)\s+([A-Za-z0-9\u0600-\u06FF ._-]{3,40})"""

            val defaults = listOf(
                SmsRule(
                    bankName = "البنك الأهلي المصري (NBE)",
                    senderPattern = "(?i)NBE|الأهلي",
                    debitKeywordPattern = "(?i)سحب|خصم|شراء|Purchase|Debit",
                    amountPattern = amountGeneric,
                    merchantPattern = merchantGeneric,
                    isBuiltIn = true
                ),
                SmsRule(
                    bankName = "بنك CIB",
                    senderPattern = "(?i)CIB",
                    debitKeywordPattern = "(?i)سحب|خصم|شراء|Purchase|Debit",
                    amountPattern = amountGeneric,
                    merchantPattern = merchantGeneric,
                    isBuiltIn = true
                ),
                SmsRule(
                    bankName = "بنك مصر",
                    senderPattern = "(?i)Banque Misr|بنك مصر|BM",
                    debitKeywordPattern = "(?i)سحب|خصم|شراء|Purchase|Debit",
                    amountPattern = amountGeneric,
                    merchantPattern = merchantGeneric,
                    isBuiltIn = true
                ),
                SmsRule(
                    bankName = "InstaPay",
                    senderPattern = "(?i)InstaPay|انستا\\s*باي",
                    debitKeywordPattern = "(?i)تم\\s*تحويل|تم\\s*سحب|تم\\s*خصم|تم\\s*دفع|Sent",
                    amountPattern = amountGeneric,
                    merchantPattern = merchantGeneric,
                    isBuiltIn = true
                )
            )

            dao.insertAll(defaults)
        }

        private suspend fun seedDefaultCategoriesIfEmpty(dao: CategoryDao) {
            if (dao.count() > 0) return

            val defaults = listOf(
                "سوبر ماركت", "فواتير", "مواصلات", "أكل وكافيهات",
                "تسوق", "صحة", "ترفيه", "غير مصنف"
            ).map { Category(name = it, isBuiltIn = true) }

            dao.insertAll(defaults)
        }
    }
}
