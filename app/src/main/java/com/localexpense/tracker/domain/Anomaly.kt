package com.localexpense.tracker.domain

/**
 * كشف الحركات الشاذة (المرحلة 11).
 *
 * القاعدة: العملية تعتبر شاذة لو مبلغها >= [thresholdMultiplier] × متوسط
 * نفس الفئة، **وبس** لو عندنا عدد كافي من العمليات السابقة ([minSamples])
 * عشان المتوسط يبقى ليه معنى. من غير الشرط التاني أول عمليتين في أي فئة
 * جديدة كانوا هيطلعوا "شاذين" وده إنذار كذّاب.
 */
const val DEFAULT_ANOMALY_MULTIPLIER = 3.0
const val DEFAULT_ANOMALY_MIN_SAMPLES = 5

fun isAnomalous(
    amountMinor: Long,
    categoryAverageMinor: Double?,
    categorySampleCount: Int,
    thresholdMultiplier: Double = DEFAULT_ANOMALY_MULTIPLIER,
    minSamples: Int = DEFAULT_ANOMALY_MIN_SAMPLES
): Boolean {
    if (categoryAverageMinor == null || categoryAverageMinor <= 0.0) return false
    if (categorySampleCount < minSamples) return false
    return amountMinor >= categoryAverageMinor * thresholdMultiplier
}
