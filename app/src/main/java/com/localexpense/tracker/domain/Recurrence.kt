package com.localexpense.tracker.domain

import com.localexpense.tracker.data.Frequency
import java.util.Calendar

/**
 * حساب موعد الدفعة الجاية (المراحل 12 و 13). Calendar بيتعامل مع الشهور
 * القصيرة صح: 31 يناير + شهر = 28/29 فبراير (مش 3 مارس).
 */
fun nextDueDate(from: Long, frequency: Frequency, intervalDays: Int = 30): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = from }
    when (frequency) {
        Frequency.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        Frequency.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
        Frequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
        Frequency.YEARLY -> cal.add(Calendar.YEAR, 1)
        Frequency.CUSTOM -> cal.add(Calendar.DAY_OF_MONTH, intervalDays.coerceAtLeast(1))
    }
    return cal.timeInMillis
}

/**
 * أول موعد استحقاق لدفعة شهرية بيوم محدد: نفس الشهر لو اليوم لسه جاي،
 * وإلا الشهر اللي بعده. اليوم بيتقصّ على آخر يوم في الشهر (يوم 31 في فبراير
 * = 28/29).
 */
fun firstDueDateForDayOfMonth(now: Long, dayOfMonth: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    if (dayOfMonth < today) cal.add(Calendar.MONTH, 1)
    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
    return cal.timeInMillis
}

/** تحويل مبلغ دوري لمكافئه الشهري — لعرض "إجمالي الاشتراكات الشهرية". */
fun monthlyEquivalentMinor(amountMinor: Long, frequency: Frequency, intervalDays: Int = 30): Long =
    when (frequency) {
        Frequency.DAILY -> amountMinor * 30
        Frequency.WEEKLY -> amountMinor * 30 / 7
        Frequency.MONTHLY -> amountMinor
        Frequency.YEARLY -> amountMinor / 12
        Frequency.CUSTOM -> if (intervalDays <= 0) amountMinor else amountMinor * 30 / intervalDays
    }
