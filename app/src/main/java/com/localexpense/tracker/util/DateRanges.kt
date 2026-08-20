package com.localexpense.tracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * حدود الشهر/اليوم بتوقيت الجهاز. مكان واحد لحساب المدى الزمني عشان
 * الداشبورد والتنبيهات والتقارير كلهم يتفقوا على نفس بداية ونهاية الشهر.
 */
data class TimeRange(val start: Long, val end: Long)

private fun calendarAt(timestamp: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = timestamp }

fun monthRange(timestamp: Long = System.currentTimeMillis()): TimeRange {
    val cal = calendarAt(timestamp).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return TimeRange(start, cal.timeInMillis - 1)
}

/** الشهر السابق للتاريخ المُعطى. */
fun previousMonthRange(timestamp: Long = System.currentTimeMillis()): TimeRange {
    val cal = calendarAt(timestamp).apply { add(Calendar.MONTH, -1) }
    return monthRange(cal.timeInMillis)
}

/** إزاحة بالشهور: -1 = الشهر اللي فات، +1 = الشهر الجاي. */
fun monthRangeOffset(monthsFromNow: Int, timestamp: Long = System.currentTimeMillis()): TimeRange {
    val cal = calendarAt(timestamp).apply { add(Calendar.MONTH, monthsFromNow) }
    return monthRange(cal.timeInMillis)
}

fun dayRange(timestamp: Long): TimeRange {
    val cal = calendarAt(timestamp).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    return TimeRange(start, cal.timeInMillis - 1)
}

fun daysInMonth(timestamp: Long = System.currentTimeMillis()): Int =
    calendarAt(timestamp).getActualMaximum(Calendar.DAY_OF_MONTH)

fun dayOfMonth(timestamp: Long = System.currentTimeMillis()): Int =
    calendarAt(timestamp).get(Calendar.DAY_OF_MONTH)

private val arabicMonthFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))
private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

fun monthLabel(timestamp: Long = System.currentTimeMillis()): String =
    arabicMonthFormat.format(Date(timestamp))

fun dayKey(timestamp: Long): String = dayKeyFormat.format(Date(timestamp))

fun monthKey(timestamp: Long = System.currentTimeMillis()): String = monthKeyFormat.format(Date(timestamp))
