package com.localexpense.tracker.data

import android.content.Context

/**
 * إعدادات خفيفة متخزّنة في SharedPreferences — الحاجات اللي مش محتاجة جدول
 * ولا استعلام: الرؤى المخفية، خيارات التصدير، عتبة الحركات الشاذة.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)

    /** مفاتيح الرؤى اللي المستخدم خفاها (المرحلة 11: الرؤى قابلة للإخفاء). */
    var dismissedInsights: Set<String>
        get() = prefs.getStringSet(KEY_DISMISSED, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_DISMISSED, value).apply() }

    fun dismissInsight(key: String) {
        dismissedInsights = dismissedInsights + key
    }

    fun clearDismissedInsights() {
        prefs.edit().remove(KEY_DISMISSED).apply()
    }

    /** تصدير نص الرسائل الخام في CSV — إيقافه هو الافتراضي (بيانات حساسة). */
    var includeRawTextInExport: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_RAW, false)
        set(value) { prefs.edit().putBoolean(KEY_INCLUDE_RAW, value).apply() }

    /** مضاعف كشف الحركة الشاذة (3 = العملية أعلى من 3 أمثال متوسط الفئة). */
    var anomalyMultiplier: Float
        get() = prefs.getFloat(KEY_ANOMALY, 3f)
        set(value) { prefs.edit().putFloat(KEY_ANOMALY, value.coerceIn(1.5f, 10f)).apply() }

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_BACKUP, value).apply() }

    /** استخدام ألوان النظام (Material You) في أندرويد 12+ بدلاً من الألوان الثابتة. */
    var useDynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) { prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply() }

    private companion object {
        const val KEY_DISMISSED = "dismissed_insights"
        const val KEY_INCLUDE_RAW = "include_raw_text"
        const val KEY_ANOMALY = "anomaly_multiplier"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
    }
}
