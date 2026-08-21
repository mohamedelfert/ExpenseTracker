package com.localexpense.tracker.data

import android.content.Context

/**
 * بيتتبّع عناوين المرسلين اللي اتبعتلهم إشعار "لقينا مصدر جديد" قبل كده،
 * عشان المستخدم ميتصدّرش بنفس الإشعار كل مرة توصل رسالة تانية من نفس
 * المرسل غير المتعرّف عليه. مخزّن في SharedPreferences عادي — مش محتاج
 * جدول Room كامل ولا migration لحاجة بالحجم والعمر ده.
 *
 * الحد الأقصى [MAX_TRACKED] موجود كحماية بسيطة من نمو غير محدود لو حصل
 * (نادرًا) توارد رسائل من عناوين عشوائية كتير على التوالي.
 */
object UnknownSourceTracker {
    private const val PREFS = "unknown_source_tracker"
    private const val KEY_NOTIFIED = "notified_senders"
    private const val MAX_TRACKED = 100

    fun hasBeenNotified(context: Context, sender: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sender in (prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet())
    }

    fun markNotified(context: Context, sender: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()).toMutableSet()
        current.add(sender)

        val trimmed = if (current.size > MAX_TRACKED) {
            current.toList().takeLast(MAX_TRACKED).toSet()
        } else {
            current
        }

        prefs.edit().putStringSet(KEY_NOTIFIED, trimmed).apply()
    }
}