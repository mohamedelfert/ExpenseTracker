package com.localexpense.tracker.domain

/** تغيّر فئة واحدة بين شهرين. [changePercent] = null يعني مفيش صرف الشهر السابق. */
data class CategoryChange(
    val categoryName: String,
    val previousMinor: Long,
    val currentMinor: Long,
    val changePercent: Double?
) {
    val deltaMinor: Long get() = currentMinor - previousMinor
    val isIncrease: Boolean get() = deltaMinor > 0
    val isNew: Boolean get() = previousMinor == 0L && currentMinor > 0L
}

data class MonthComparison(
    val changes: List<CategoryChange>,
    val previousTotalMinor: Long,
    val currentTotalMinor: Long,
    val totalChangePercent: Double?
) {
    val increases: List<CategoryChange> get() = changes.filter { it.deltaMinor > 0 }
    val decreases: List<CategoryChange> get() = changes.filter { it.deltaMinor < 0 }
    val biggestIncrease: CategoryChange? get() = changes.maxByOrNull { it.deltaMinor }?.takeIf { it.deltaMinor > 0 }
    val biggestDecrease: CategoryChange? get() = changes.minByOrNull { it.deltaMinor }?.takeIf { it.deltaMinor < 0 }
    val hasPrevious: Boolean get() = previousTotalMinor > 0L
}

/**
 * مقارنة شهرين بالفئة (المرحلة 9). الفئات اللي ظهرت في أي شهر من الاتنين
 * بتتضمّن (فئة اختفت الشهر ده = نقصان لصفر، وفئة جديدة = زيادة من صفر).
 * الترتيب: الأكبر تغيّرًا مطلقًا الأول.
 */
fun compareMonths(
    previous: Map<String, Long>,
    current: Map<String, Long>
): MonthComparison {
    val names = (previous.keys + current.keys)
    val changes = names.map { name ->
        val prev = previous[name] ?: 0L
        val cur = current[name] ?: 0L
        CategoryChange(name, prev, cur, percentChange(prev, cur))
    }.sortedByDescending { kotlin.math.abs(it.deltaMinor) }

    val prevTotal = previous.values.sum()
    val curTotal = current.values.sum()
    return MonthComparison(
        changes = changes,
        previousTotalMinor = prevTotal,
        currentTotalMinor = curTotal,
        totalChangePercent = percentChange(prevTotal, curTotal)
    )
}
