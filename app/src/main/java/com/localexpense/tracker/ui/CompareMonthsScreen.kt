package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.viewmodel.FinanceViewModel

/**
 * المقارنة الشهرية (المرحلة 9): كل فئة، الشهر السابق مقابل الحالي، ونسبة
 * التغيير. الفئة الجديدة بتظهر "جديد" بدل نسبة لا نهائية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareMonthsScreen(onBack: () -> Unit) {
    val finance: FinanceViewModel = viewModel()
    val context by finance.financialContext.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مقارنة الشهور") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        val ctx = context
        if (ctx == null) {
            EmptyState("جاري الحساب...", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val comparison = ctx.comparison
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(ctx.monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "الشهر ده ${formatMinor(comparison.currentTotalMinor)} — " +
                            "الشهر اللي فات ${formatMinor(comparison.previousTotalMinor)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    comparison.totalChangePercent?.let { change ->
                        Text(
                            if (change >= 0) "↑ زيادة ${change.toInt()}%" else "↓ نقصان ${-change.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (change >= 0) Color(0xFFE53935) else Color(0xFF43A047)
                        )
                    }
                }
            }

            if (!comparison.hasPrevious) {
                item { EmptyState("مفيش بيانات للشهر اللي فات", "المقارنة هتظهر أول ما يبقى عندك شهرين مسجلين.") }
            }

            comparison.biggestIncrease?.let { change ->
                item {
                    SectionHeader("أكبر زيادة")
                    AmountRow(change.categoryName, change.deltaMinor, trailingText = "من ${formatMinor(change.previousMinor)}")
                }
            }
            comparison.biggestDecrease?.let { change ->
                item {
                    SectionHeader("أكبر نقصان")
                    AmountRow(change.categoryName, change.deltaMinor, trailingText = "من ${formatMinor(change.previousMinor)}")
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("كل الفئات") }

            items(comparison.changes.size) { index ->
                val change = comparison.changes[index]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(change.categoryName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(
                            "${formatMinor(change.previousMinor, withDecimals = false)} → " +
                                formatMinor(change.currentMinor, withDecimals = false),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val label = when {
                            change.isNew -> "جديد"
                            change.changePercent == null -> "—"
                            change.changePercent >= 0 -> "+${change.changePercent.toInt()}%"
                            else -> "${change.changePercent.toInt()}%"
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                change.deltaMinor > 0 -> Color(0xFFE53935)
                                change.deltaMinor < 0 -> Color(0xFF43A047)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
