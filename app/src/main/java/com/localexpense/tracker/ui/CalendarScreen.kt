package com.localexpense.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.dayKey
import com.localexpense.tracker.util.monthLabel
import com.localexpense.tracker.util.monthRange
import com.localexpense.tracker.viewmodel.FinanceViewModel
import com.localexpense.tracker.viewmodel.PlansViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * التقويم المالي (المرحلة 15): شدة اللون = حجم الصرف في اليوم، والضغط على
 * يوم بيعرض حركاته. الأرقام جاية من تجميع SQL يومي (observeDailyTotalsBetween).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val finance: FinanceViewModel = viewModel()
    val plans: PlansViewModel = viewModel()
    val daily by finance.dailyTotals.collectAsStateWithLifecycle()
    val selectedDay by plans.selectedDay.collectAsStateWithLifecycle()
    val dayTransactions by plans.dayTransactions.collectAsStateWithLifecycle()
    val upcoming by plans.upcoming.collectAsStateWithLifecycle()

    val range = monthRange()
    val totalsByDay = daily.associate { it.day to it.total }
    val max = (daily.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L)

    val days = buildList {
        val cal = Calendar.getInstance().apply { timeInMillis = range.start }
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..lastDay) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            add(cal.timeInMillis)
        }
    }
    val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
    val dayFormat = SimpleDateFormat("dd MMMM", Locale("ar"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقويم — ${monthLabel(range.start)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                items(days) { timestamp ->
                    val total = totalsByDay[dayKey(timestamp)] ?: 0L
                    val intensity = (total.toFloat() / max).coerceIn(0f, 1f)
                    val isSelected = dayKey(timestamp) == dayKey(selectedDay)
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.65f * intensity)
                            )
                            .clickable { plans.selectDay(timestamp) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Calendar.getInstance().apply { timeInMillis = timestamp }
                                    .get(Calendar.DAY_OF_MONTH).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (total > 0) {
                                Text(
                                    formatMinor(total, withDecimals = false, withSymbol = false),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            SectionHeader("حركات ${dayFormat.format(Date(selectedDay))}")

            if (dayTransactions.isEmpty()) {
                EmptyState("مفيش حركات في اليوم ده")
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(dayTransactions.size) { index ->
                        val expense = dayTransactions[index]
                        AmountRow(
                            label = expense.merchant,
                            amountMinor = expense.amountMinor,
                            trailingText = "${typeLabel(expense.type)} • ${timeFormat.format(Date(expense.timestamp))}",
                            onClick = { onOpenTransaction(expense.id) }
                        )
                    }
                }
            }

            if (upcoming.isNotEmpty()) {
                HorizontalDivider()
                SectionHeader("دفعات قادمة")
                Column(Modifier.padding(bottom = 8.dp)) {
                    upcoming.take(3).forEach { payment ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(payment.name, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${formatMinor(payment.amountMinor)} • ${dayFormat.format(Date(payment.dueDate))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
