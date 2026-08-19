package com.localexpense.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Expense::class, Category::class, SmsRule::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun smsRuleDao(): SmsRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
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
