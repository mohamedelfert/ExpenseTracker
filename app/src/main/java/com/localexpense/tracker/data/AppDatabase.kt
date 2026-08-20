package com.localexpense.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.localexpense.tracker.util.CrashLog
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

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

        private const val DB_NAME = "expense_tracker_db"

        fun getDatabase(context: Context): AppDatabase {
            // applicationContext من أول سطر: الدالة دي بتتنادى من
            // BroadcastReceiver و NotificationListenerService كمان، ومينفعش
            // سياق قصير العمر يوصل لتحويل التشفير ولا لملف قاعدة البيانات.
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                // إعادة الفحص **جوه** القفل: من غيرها كل خيط بيدخل القفل كان
                // بيبني نسخة جديدة ويكتب فوق INSTANCE، فكان ممكن يبقى فيه
                // أكتر من اتصال مفتوح على نفس الملف المشفّر في نفس الوقت
                // (الواجهة و NotificationListenerService بيفتحوا مع بعض عند
                // التشغيل).
                INSTANCE ?: buildDatabase(appContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            // بيحمّل مكتبة SQLCipher الأصلية (native) - لازم قبل أي استخدام
            // تاني، وبالأخص قبل DatabaseEncryptionMigration تحت. حزمة
            // sqlcipher-android مفيهاش loadLibs() زي الحزمة القديمة،
            // فالتحميل بيتم يدويًا (System.loadLibrary) - ده مطلوب فعلاً،
            // مفيش أي كلاس جوه المكتبة بيعمله لوحده.
            System.loadLibrary("sqlcipher")

            // مفتاح تشفير عشوائي محمي بـ Android Keystore، محلي بالكامل
            // (راجع SecurePassphraseProvider للتفاصيل).
            val passphrase = SecurePassphraseProvider.getOrCreatePassphrase(appContext)

            // لو فيه قاعدة بيانات قديمة غير مشفّرة من نسخة سابقة من
            // التطبيق، بيتم تحويلها لمشفّرة مرة واحدة بس مع الحفاظ الكامل
            // على البيانات (راجع DatabaseEncryptionMigration).
            DatabaseEncryptionMigration.ensureEncrypted(appContext, passphrase)

            moveAsideUnreadableDatabase(appContext, passphrase)

            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

            val instance = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                // ترقيات حقيقية تحافظ على البيانات في كل المسارات:
                // 1-4 -> 5 ترقية استكشافية (مخططها مجهول، راجع
                // LegacyMigrations)، 5->6 تحويل المبالغ لوحدات صغرى،
                // 6->7 كل مخطط المراحل 3-20.
                .addMigrations(*LEGACY_MIGRATIONS, MIGRATION_5_6, MIGRATION_6_7)
                // شبكة أمان أخيرة: لو ظهرت نسخة قديمة معندهاش مسار ترقية
                // (نسخة تجريبية قديمة مثلاً)، إعادة إنشاء القاعدة أحسن من
                // crash على كل فتح. النسخ 1-4 بقى ليها مسار حقيقي فوق،
                // فالسطر ده عمليًا مش بيشتغل ليها.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                // الكوروتين ده مش تابع لأي scope بيتعامل مع الأخطاء، فأي
                // استثناء جواه كان بيوصل للـ handler العام ويقفل التطبيق عند
                // كل فتح. التصنيفات الافتراضية حاجة كمالية - مستحقّش انهيار.
                runCatching { seedDefaultCategoriesIfEmpty(instance.categoryDao()) }
                    .onFailure { CrashLog.recordNonFatal(appContext, "seedDefaultCategories", it) }
            }

            return instance
        }

        /**
         * ملف قاعدة بيانات مشفّر بمفتاح مش موجود خلاص (المستخدم غيّر قفل
         * الشاشة فالـ Keystore لغى المفتاح، أو التطبيق اتثبّت فوق بيانات
         * قديمة) معناه إن Room هيرمي استثناء أول قراءة والتطبيق يقفل عند كل
         * فتح من غير أي طريقة للخروج من الحالة دي.
         *
         * بننقل الملف على جنب (**مش** بنمسحه) عشان التطبيق يفتح بقاعدة
         * جديدة، والملف القديم يفضل على القرص لو ظهرت طريقة استرجاع.
         */
        private fun moveAsideUnreadableDatabase(appContext: Context, passphrase: String) {
            val dbFile = appContext.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return
            if (DatabaseEncryptionMigration.isReadableWithPassphrase(dbFile, passphrase)) return
            // لسه ممكن يكون ملف غير مشفّر و ensureEncrypted فشل يحوّله لسبب
            // مؤقت. ساعتها منلمسوش خالص: البيانات سليمة والمحاولة بتتكرر في
            // التشغيلة الجاية.
            //
            // الفحص هنا على ترويسة الملف نفسها، **مش** بمحاولة فتح بمفتاح
            // فاضي: سلوك المفتاح الفاضي في sqlcipher-android مش متأكدين منه،
            // ولو طلع بيفشل على ملف غير مشفّر كنا هننقل بيانات المستخدم
            // السليمة على جنب ونفقدها. ترويسة SQLite حقيقة ثابتة في صيغة
            // الملف نفسه ومستقلة عن أي مكتبة.
            if (looksLikePlainSqlite(dbFile)) return

            val movedTo = File(dbFile.parentFile, "$DB_NAME.unreadable-${System.currentTimeMillis()}")
            val moved = runCatching { dbFile.renameTo(movedTo) }.getOrDefault(false)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                runCatching { File(dbFile.path + suffix).delete() }
            }
            CrashLog.recordNonFatal(
                appContext,
                if (moved) "قاعدة البيانات مش قابلة للفتح بالمفتاح الحالي - اتنقلت لـ ${movedTo.name} وبدأنا واحدة جديدة"
                else "قاعدة البيانات مش قابلة للفتح بالمفتاح الحالي وفشل نقلها على جنب",
                IllegalStateException("database unreadable with current passphrase")
            )
        }

        /**
         * ملف SQLite غير مشفّر بيبدأ دايمًا بالـ 16 بايت
         * `SQLite format 3\u0000`. الملف المشفّر بـ SQLCipher بيبدأ ببيانات
         * عشوائية من أول بايت، فالفحص ده بيفرّق بين الاتنين بشكل قاطع.
         */
        private fun looksLikePlainSqlite(dbFile: File): Boolean = runCatching {
            val header = ByteArray(16)
            val read = dbFile.inputStream().use { it.read(header) }
            read == header.size && header.toString(Charsets.US_ASCII) == "SQLite format 3\u0000"
        }.getOrDefault(false)

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
