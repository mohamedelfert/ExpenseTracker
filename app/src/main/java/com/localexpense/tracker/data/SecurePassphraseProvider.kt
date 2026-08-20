package com.localexpense.tracker.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.localexpense.tracker.util.CrashLog
import java.security.Key
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * بيدير "مفتاح" تشفير قاعدة بيانات SQLCipher المحلية.
 *
 * كل حاجة هنا محلية بالكامل وملهاش أي علاقة بالإنترنت:
 * 1. أول مرة يتفتح فيها التطبيق، بيتولّد passphrase عشوائي (256-bit) باستخدام
 *    SecureRandom.
 * 2. الـ passphrase ده بيتشفّر بمفتاح تاني (wrapping key) مولّد ومحفوظ جوه
 *    Android Keystore - المفتاح ده نفسه مش قابل للاستخراج من الجهاز إطلاقًا
 *    (non-exportable)، حتى لو حد عمل روت للجهاز.
 * 3. الناتج المشفّر (مش المفتاح الخام) هو بس اللي بيتخزن في SharedPreferences
 *    عادية، عشان حتى لو حد قرا الملف ده مباشرة مش هيلاقي حاجة مفيدة من غير
 *    مفتاح الـ Keystore.
 *
 * النتيجة: قاعدة بيانات المصروفات (بيانات مالية حساسة) بقت مشفّرة على القرص،
 * والمفتاح نفسه محمي بأمان الهاردوير بتاع الجهاز، وكله من غير أي سيرفر خارجي.
 */
object SecurePassphraseProvider {

    private const val PREFS_NAME = "secure_db_prefs"
    private const val PREF_KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_KEY_IV = "passphrase_iv"
    private const val KEYSTORE_ALIAS = "expense_tracker_db_wrap_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PASSPHRASE_BYTE_LENGTH = 32 // 256-bit

    /**
     * بيرجع نفس الـ passphrase في كل مرة (بعد فك تشفيره)، أو بيولّد واحد جديد
     * أول مرة بس. الـ passphrase ده نص عادي (Base64) عشان يتستخدم مباشرة في
     * جمل SQL الخاصة بـ SQLCipher (ATTACH ... KEY 'passphrase').
     */
    @Synchronized
    fun getOrCreatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncrypted = prefs.getString(PREF_KEY_ENCRYPTED_PASSPHRASE, null)
        val existingIv = prefs.getString(PREF_KEY_IV, null)

        if (existingEncrypted != null && existingIv != null) {
            // فك التشفير ممكن يفشل لأسباب خارجة عن إرادتنا: مفتاح الـ Keystore
            // بيتلغي لو المستخدم غيّر قفل الشاشة، ولو التطبيق اتثبّت فوق بيانات
            // قديمة (reinstall من غير مسح البيانات) الـ prefs بتفضل موجودة
            // والمفتاح بيبقى مروّح. قبل كده الاستثناء ده كان بيطلع من كونستركتور
            // الـ ViewModel فالتطبيق كان بيقفل على طول عند كل فتح.
            //
            // الحل: نسجّل السبب ونولّد مفتاح جديد. البيانات القديمة المشفّرة
            // بالمفتاح المفقود مش قابلة للاسترجاع رياضيًا، وملف قاعدة البيانات
            // بيتنقل على جنب (مش بيتمسح) في AppDatabase.getDatabase.
            val decrypted = runCatching { decryptPassphrase(existingEncrypted, existingIv) }
            decrypted.getOrNull()?.let { return it }
            CrashLog.recordNonFatal(
                context,
                "SecurePassphraseProvider: فشل فك تشفير مفتاح قاعدة البيانات - بيتولّد مفتاح جديد",
                decrypted.exceptionOrNull() ?: IllegalStateException("unknown")
            )
            // لازم نمسح مفتاح الـ Keystore نفسه: لو اتلغى
            // (KeyPermanentlyInvalidatedException) بيفضل موجود بالاسم بس مش
            // صالح للاستخدام، فـ getOrCreateWrappingKey كان هيرجّعه تاني
            // والتشفير بالمفتاح الجديد كان هيفشل ويقفل التطبيق برضه.
            deleteWrappingKey()
        }

        val newPassphrase = generateRandomPassphrase()
        val (encrypted, iv) = encryptPassphrase(newPassphrase)
        prefs.edit()
            .putString(PREF_KEY_ENCRYPTED_PASSPHRASE, encrypted)
            .putString(PREF_KEY_IV, iv)
            .apply()
        return newPassphrase
    }

    private fun generateRandomPassphrase(): String {
        val randomBytes = ByteArray(PASSPHRASE_BYTE_LENGTH)
        SecureRandom().nextBytes(randomBytes)
        // Base64 URL-safe عشان يطلع نص من غير أي حروف ممكن تعمل مشاكل جوه جمل SQL
        return Base64.encodeToString(
            randomBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /** بيشيل مفتاح التغليف من الـ Keystore عشان اللي بعده يتولّد نضيف. */
    private fun deleteWrappingKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEYSTORE_ALIAS)
        }
    }

    private fun getOrCreateWrappingKey(): Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(passphrase: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val encryptedBytes = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivEncoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        return encoded to ivEncoded
    }

    private fun decryptPassphrase(encrypted: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val decryptedBytes = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
