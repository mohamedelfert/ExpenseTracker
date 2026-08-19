package com.localexpense.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase as SQLCipherLoader
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Expense::class, Category::class, SmsRule::class, Budget::class, RecurringExpense::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun smsRuleDao(): SmsRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // بيحمّل مكتبة SQLCipher الأصلية (native) - لازم قبل أي استخدام تاني.
                SQLCipherLoader.loadLibs(context)

                // مفتاح تشفير عشوائي محمي بـ Android Keystore، محلي بالكامل
                // (راجع SecurePassphraseProvider للتفاصيل).
                val passphrase = SecurePassphraseProvider.getOrCreatePassphrase(context)

                // لو فيه قاعدة بيانات قديمة غير مشفّرة من نسخة سابقة من
                // التطبيق، بيتم تحويلها لمشفّرة مرة واحدة بس مع الحفاظ الكامل
                // على البيانات (راجع DatabaseEncryptionMigration).
                DatabaseEncryptionMigration.ensureEncrypted(context, passphrase)

                val factory = SupportFactory(passphrase.toByteArray(Charsets.UTF_8))

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                CoroutineScope(Dispatchers.IO).launch {
                    seedDefaultCategoriesIfEmpty(instance.categoryDao())
                }

                instance
            }
        }

        private suspend fun seedDefaultCategoriesIfEmpty(dao: CategoryDao) {
            if (dao.count() > 0) return

            val defaults = listOf(
                "سوبر ماركت", "فواتير وخدمات", "مواصلات ووقود", "مطاعم وكافيهات",
                "تسوق", "صحة وعلاج", "ترفيه", "تحويلات", "سحب نقدي", "عام"
            ).map { Category(name = it, isBuiltIn = true) }

            dao.insertAll(defaults)
        }
    }
}
