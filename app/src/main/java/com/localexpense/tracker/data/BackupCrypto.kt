package com.localexpense.tracker.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * تشفير ملف النسخة الاحتياطية بكلمة سر يختارها المستخدم — منفصل تمامًا عن
 * تشفير قاعدة البيانات بـ SQLCipher (اللي بيستخدم مفتاح مخزّن في الجهاز نفسه).
 * هنا المفتاح مبني بالكامل من كلمة السر، فمفيش أي حاجة متخزنة تسمح بفك
 * التشفير غير كلمة السر اللي المستخدم فاكرها.
 *
 * AES-256-GCM + PBKDF2WithHmacSHA256 - كله متاح جوه javax.crypto/java.security
 * القياسية في أندرويد، فمفيش أي مكتبة اتضافت.
 */
object BackupCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    // توصية OWASP الحالية لـ PBKDF2-HMAC-SHA256 (2023+).
    const val ITERATIONS = 210_000

    data class Encrypted(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val iterations: Int = ITERATIONS
    )

    fun encrypt(plainText: String, password: String): Encrypted {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Encrypted(salt, iv, ciphertext, ITERATIONS)
    }

    /** بيرمي [WrongPasswordException] لو كلمة السر غلط أو الملف اتلعب فيه. */
    fun decrypt(encrypted: Encrypted, password: String): String {
        val key = deriveKey(password, encrypted.salt, encrypted.iterations)
        val cipher = Cipher.getInstance(ALGORITHM)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.iv))
            val plain = cipher.doFinal(encrypted.ciphertext)
            return String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM بيتحقق من سلامة البيانات كجزء من فك التشفير - أي كلمة سر
            // غلط أو تلاعب في الملف بيرمي استثناء هنا، مش نص فاسد.
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    class WrongPasswordException :
        Exception("كلمة السر غلط، أو الملف تالف")
}
