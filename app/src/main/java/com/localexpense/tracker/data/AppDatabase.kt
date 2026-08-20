package com.localexpense.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Expense::class, Category::class, SmsRule::class, Budget::class, RecurringExpense::class,
        Account::class, Merchant::class, MerchantRule::class, Installment::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun smsRuleDao(): SmsRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantDao(): MerchantDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun installmentDao(): InstallmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // بيحمّل مكتبة SQLCipher الأصلية (native) - لازم قبل أي استخدام
                // تاني، وبالأخص قبل DatabaseEncryptionMigration تحت. حزمة
                // sqlcipher-android مفيهاش loadLibs() زي الحزمة القديمة،
                // فالتحميل بيتم يدويًا (System.loadLibrary) - ده مطلوب فعلاً،
                // مفيش أي كلاس جوه المكتبة بيعمله لوحده.
                System.loadLibrary("sqlcipher")

                // مفتاح تشفير عشوائي محمي بـ Android Keystore، محلي بالكامل
                // (راجع SecurePassphraseProvider للتفاصيل).
                val passphrase = SecurePassphraseProvider.getOrCreatePassphrase(context)

                // لو فيه قاعدة بيانات قديمة غير مشفّرة من نسخة سابقة من
                // التطبيق، بيتم تحويلها لمشفّرة مرة واحدة بس مع الحفاظ الكامل
                // على البيانات (راجع DatabaseEncryptionMigration).
                DatabaseEncryptionMigration.ensureEncrypted(context, passphrase)

                val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .openHelperFactory(factory)
                    // ترقيات حقيقية تحافظ على البيانات: 5->6 تحويل المبالغ لوحدات
                    // صغرى، و 6->7 كل مخطط المراحل 3-20 (إضافي بالكامل).
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    // النسخ 1-4 القديمة معندناش مخططها (exportSchema كان false ومفيش
                    // git history)، فبنسمح بإعادة إنشاء قاعدة البيانات لها فقط - نفس
                    // سلوك fallbackToDestructiveMigration() القديم للنسخ دي بالظبط،
                    // لكن دلوقتي بيانات النسخة 5 (الأغلبية) بتتحافظ عليها فعلاً.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
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
