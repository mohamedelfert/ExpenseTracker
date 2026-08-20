package com.localexpense.tracker.util

import java.security.MessageDigest

/**
 * بصمة ثابتة لنص الرسالة — بتُخزَّن في Expense.rawHash وبتُستخدم لمنع تسجيل
 * نفس الرسالة مرتين (المرحلة 17). SHA-256 من مكتبة الجافا القياسية.
 *
 * التطبيع (تصغير + تجميع المسافات) عشان اختلاف مسافة أو حالة حرف في نفس
 * الرسالة ما ينتجش بصمة مختلفة.
 */
fun rawMessageHash(sender: String, body: String): String {
    val normalized = "${sender.trim().lowercase()}|${body.trim().lowercase().replace(Regex("\\s+"), " ")}"
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
