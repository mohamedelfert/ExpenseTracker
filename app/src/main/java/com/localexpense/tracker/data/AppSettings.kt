package com.localexpense.tracker.data

import android.content.Context
import java.util.Calendar

/**
 * المدى الزمني اللي بيتفحص من الرسائل لما تسحب الشاشة لتحت (Pull to refresh)
 * في الرئيسية. افتراضيًا آخر 3 شهور.
 */
enum class SmsSyncRange {
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    SPECIFIC_YEAR;

    companion object {
        fun fromStorageKey(key: String?): SmsSyncRange =
            entries.firstOrNull { it.name == key } ?: LAST_3_MONTHS
    }
}

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

    /** استخدام وضع AMOLED الداكن (الخلفية سوداء بالكامل). */
    var useAmoledMode: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_MODE, false)
        set(value) { prefs.edit().putBoolean(KEY_AMOLED_MODE, value).apply() }

    /** مدى استيراد الرسائل لما تسحب الرئيسية لتحت (المزامنة السريعة). */
    var smsSyncRange: SmsSyncRange
        get() = SmsSyncRange.fromStorageKey(prefs.getString(KEY_SYNC_RANGE, null))
        set(value) { prefs.edit().putString(KEY_SYNC_RANGE, value.name).apply() }

    /** السنة المختارة لما يكون مدى المزامنة SPECIFIC_YEAR. */
    var smsSyncYear: Int
        get() = prefs.getInt(KEY_SYNC_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        set(value) { prefs.edit().putInt(KEY_SYNC_YEAR, value).apply() }

    /**
     * بداية ونهاية المدى الزمني المطلوب فحصه بناءً على [smsSyncRange] الحالي.
     * النهاية null معناها "لحد دلوقتي" — عشان المزامنة تجيب أي رسالة جديدة
     * ولو اتأخرت في الوصول للجهاز.
     */
    fun smsSyncTimeRange(): Pair<Long, Long?> {
        val cal = Calendar.getInstance()
        return when (smsSyncRange) {
            SmsSyncRange.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis to null
            }
            SmsSyncRange.LAST_3_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis to null
            }
            SmsSyncRange.LAST_6_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis to null
            }
            SmsSyncRange.SPECIFIC_YEAR -> {
                val year = smsSyncYear
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val end = if (year >= currentYear) {
                    null
                } else {
                    cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }
                start to end
            }
        }
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed_insights"
        const val KEY_INCLUDE_RAW = "include_raw_text"
        const val KEY_ANOMALY = "anomaly_multiplier"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
        const val KEY_AMOLED_MODE = "use_amoled_mode"
        const val KEY_SYNC_RANGE = "sms_sync_range"
        const val KEY_SYNC_YEAR = "sms_sync_year"
    }
}
