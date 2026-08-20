package com.localexpense.tracker.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * قفل التطبيق (المرحلة 16): رقم سري (PIN) + بصمة/وجه.
 *
 * **الـ PIN مش متخزّن أبدًا كنص.** بنخزّن ملح عشوائي (salt) + ناتج PBKDF2-
 * HMAC-SHA256 بـ 120 ألف تكرار، والمقارنة بـ MessageDigest.isEqual (وقت ثابت).
 * كل ده من مكتبة الجافا القياسية — مفيش أي مكتبة تشفير اتضافت.
 *
 * ملاحظة على نطاق الحماية: القفل ده بيمنع الوصول للواجهة على جهاز مفتوح.
 * حماية البيانات نفسها جاية من تشفير قاعدة البيانات (SQLCipher + Android
 * Keystore) اللي شغّال أصلاً، مش من الـ PIN.
 */
object AppLock {

    private const val PREFS = "app_lock"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val KEY_TIMEOUT = "lock_timeout_seconds"

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    /** مهلة القفل بعد الخروج من التطبيق. */
    enum class Timeout(val seconds: Int, val label: String) {
        IMMEDIATE(0, "فورًا"),
        ONE_MINUTE(60, "بعد دقيقة"),
        FIVE_MINUTES(300, "بعد 5 دقايق");

        companion object {
            fun fromSeconds(seconds: Int): Timeout =
                entries.firstOrNull { it.seconds == seconds } ?: IMMEDIATE
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPinSet(context: Context): Boolean = prefs(context).getString(KEY_HASH, null) != null

    fun isLockEnabled(context: Context): Boolean = isPinSet(context)

    fun setPin(context: Context, pin: String) {
        require(pin.length >= 4) { "الرقم السري لازم يكون 4 أرقام على الأقل" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs(context).edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash.toHex())
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_SALT, null)?.fromHex() ?: return false
        val expected = p.getString(KEY_HASH, null)?.fromHex() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_SALT).remove(KEY_HASH).remove(KEY_BIOMETRIC)
            .apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun timeout(context: Context): Timeout =
        Timeout.fromSeconds(prefs(context).getInt(KEY_TIMEOUT, 0))

    fun setTimeout(context: Context, timeout: Timeout) {
        prefs(context).edit().putInt(KEY_TIMEOUT, timeout.seconds).apply()
    }

    /**
     * هل لازم نطلب المصادقة؟ [backgroundedAt] = آخر لحظة التطبيق خرج فيها
     * للخلفية (0 = أول تشغيل).
     */
    fun shouldLock(context: Context, backgroundedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!isLockEnabled(context)) return false
        if (backgroundedAt == 0L) return true
        return now - backgroundedAt >= timeout(context).seconds * 1000L
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
