package com.localexpense.tracker.data

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * بيرحّل قاعدة بيانات موجودة من قبل (من غير تشفير - نسخة قديمة من التطبيق)
 * لقاعدة بيانات مشفّرة بـ SQLCipher، **من غير ما يفقد أي بيانات**.
 *
 * العملية بتحصل مرة واحدة بس لكل مستخدم (بيتسجل في SharedPreferences إنها
 * خلصت)، وكلها محلية على الجهاز - مفيش نت ولا سيرفر في القصة خالص.
 *
 * لو حصل أي خطأ في النص، بنسيب الملف الأصلي زي ما هو (منلمسوش قبل ما تنجح
 * العملية بالكامل)، وهيتم إعادة المحاولة تاني في المرة الجاية اللي التطبيق
 * بيفتح فيها.
 */
object DatabaseEncryptionMigration {

    private const val TAG = "DbEncryptionMigration"
    private const val PREFS_NAME = "db_migration_prefs"
    private const val KEY_ENCRYPTED_FLAG = "db_encrypted_v1"
    private const val DB_NAME = "expense_tracker_db"

    fun ensureEncrypted(context: Context, passphrase: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ENCRYPTED_FLAG, false)) return // اتعملت قبل كده

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            // تركيب جديد بالكامل - مفيش قاعدة بيانات قديمة نرحّلها
            prefs.edit().putBoolean(KEY_ENCRYPTED_FLAG, true).apply()
            return
        }

        if (isReadableWithPassphrase(dbFile, passphrase)) {
            // الملف مشفّر بالفعل (مثلاً بعد استرجاع نسخة احتياطية) - محتاجين
            // بس نسجل إن الهجرة خلصت من غير ما نلمس حاجة.
            prefs.edit().putBoolean(KEY_ENCRYPTED_FLAG, true).apply()
            return
        }

        val backupFile = File(dbFile.parentFile, "$DB_NAME.pre_encryption_backup")
        val encryptedTempFile = File(dbFile.parentFile, "$DB_NAME.encrypted_tmp")

        try {
            encryptedTempFile.delete() // احتياط لو فيه بقايا من محاولة سابقة فشلت

            // بنفتح القاعدة القديمة (غير المشفّرة) بمكتبة SQLCipher نفسها - بتقدر
            // تفتح قواعد بيانات SQLite عادية زي ما هي تمامًا لما مفيش مفتاح.
            // الـ null الأخير هو SQLiteDatabaseHook (مش محتاجينه). حزمة
            // sqlcipher-android مفيهاش overload بأربع باراميترات بس، فلازم
            // يتبعت صريح.
            val plainDb = SQLiteDatabase.openDatabase(
                dbFile.path, "", null, SQLiteDatabase.OPEN_READWRITE, null
            )

            // الـ cast ضروري: rawQuery عندها overload بـ String[] وتانية بـ
            // Object[]، و null لوحده بيبقى غامض بين الاتنين.
            val originalUserVersion = plainDb.rawQuery("PRAGMA user_version", null as Array<String>?).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

            // بنعمل نسخة احتياطية قبل أي تعديل تحسبًا لأي عطل غير متوقع
            dbFile.copyTo(backupFile, overwrite = true)

            val escapedPassphrase = passphrase.replace("'", "''")
            plainDb.execSQL(
                "ATTACH DATABASE '${encryptedTempFile.path}' AS encrypted KEY '$escapedPassphrase'"
            )
            plainDb.rawQuery("SELECT sqlcipher_export('encrypted')", null as Array<String>?).use { it.moveToFirst() }
            // Room بيعتمد على PRAGMA user_version عشان يعرف نسخة الـ schema
            // الحالية - لازم ننسخه يدويًا عشان Room متعملش هجرة/حذف بالغلط.
            plainDb.execSQL("PRAGMA encrypted.user_version = $originalUserVersion")
            plainDb.execSQL("DETACH DATABASE encrypted")
            plainDb.close()

            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            val journalFile = File(dbFile.path + "-journal")

            check(dbFile.delete()) { "تعذر حذف قاعدة البيانات القديمة غير المشفّرة" }
            walFile.delete()
            shmFile.delete()
            journalFile.delete()

            check(encryptedTempFile.renameTo(dbFile)) { "تعذر نقل قاعدة البيانات المشفّرة الجديدة لمكانها" }

            backupFile.delete() // نجحت الهجرة بالكامل، منحتاجش النسخة الاحتياطية
            prefs.edit().putBoolean(KEY_ENCRYPTED_FLAG, true).apply()
            Log.i(TAG, "تم تشفير قاعدة بيانات المصروفات بنجاح")
        } catch (e: Exception) {
            Log.e(TAG, "فشلت محاولة تشفير قاعدة البيانات - هيتم إعادة المحاولة تاني", e)
            // منمسحش الملف الأصلي أو النسخة الاحتياطية هنا، عشان بيانات
            // المستخدم تفضل سليمة ونقدر نحاول تاني في التشغيلة الجاية.
            encryptedTempFile.delete()
        }
    }

    private fun isReadableWithPassphrase(dbFile: File, passphrase: String): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.path,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null
            )
            db.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
