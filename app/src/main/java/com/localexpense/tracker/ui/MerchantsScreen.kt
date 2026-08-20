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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.MerchantAnalytics
import com.localexpense.tracker.data.TransactionFilter
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.viewmodel.MainViewModel

/** قائمة الجهات المعروفة (المرحلة 4) — بتتولد تلقائيًا من الحركات. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenMerchant: (String) -> Unit
) {
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الجهات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        if (merchants.isEmpty()) {
            EmptyState(
                "مفيش جهات لسه",
                "الجهات بتتسجّل تلقائيًا مع كل حركة جديدة.",
                Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(merchants, key = { it.id }) { merchant ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(merchant.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            merchant.categoryName.ifBlank { "مفيش فئة مربوطة" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onOpenMerchant(merchant.name) }) { Text("تفاصيل") }
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * تفاصيل جهة (المرحلة 4، بند 16): إجمالي الشهر ده والشهر اللي فات، عدد
 * العمليات، المتوسط، الأعلى، تاريخ 6 شهور، وحركات الجهة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDetailScreen(
    viewModel: MainViewModel,
    merchantName: String,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val analytics by produceState<MerchantAnalytics?>(initialValue = null, merchantName) {
        value = viewModel.merchantAnalytics(merchantName)
    }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showCategoryPicker by remember { mutableStateOf(false) }

    // حركات الجهة: نفس باني استعلام البحث بفلتر merchant.
    val transactions by produceState(initialValue = emptyList<com.localexpense.tracker.data.Expense>(), merchantName) {
        value = viewModel.searchByMerchant(merchantName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(merchantName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { showCategoryPicker = true }) { Text("اربط بفئة") }
                }
            )
        }
    ) { padding ->
        val stats = analytics
        if (stats == null) {
            EmptyState("جاري الحساب...", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("الشهر ده", stats.thisMonthMinor, Modifier.weight(1f))
                    StatCard("الشهر اللي فات", stats.lastMonthMinor, Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("متوسط العملية", stats.averageMinor, Modifier.weight(1f))
                    StatCard("أعلى عملية", stats.highestMinor, Modifier.weight(1f))
                }
            }
            item {
                Text(
                    "${stats.transactionCount} عملية الشهر ده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item { SectionHeader("آخر 6 شهور") }
            items(stats.history.size) { index ->
                val (label, total) = stats.history[index]
                AmountRow(label, total)
            }

            item { SectionHeader("حركات الجهة") }
            if (transactions.isEmpty()) {
                item { EmptyState("مفيش حركات") }
            } else {
                items(transactions.size) { index ->
                    val expense = transactions[index]
                    AmountRow(
                        label = expense.categoryName,
                        amountMinor = expense.amountMinor,
                        trailingText = typeLabel(expense.type),
                        onClick = { onOpenTransaction(expense.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCategoryPicker) {
        var applyToPast by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("اربط \"$merchantName\" بفئة") },
            text = {
                Column {
                    Row {
                        androidx.compose.material3.Checkbox(
                            checked = applyToPast,
                            onCheckedChange = { applyToPast = it }
                        )
                        Text("طبّق على الحركات القديمة كمان", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.height(260.dp)) {
                        items(categories) { category ->
                            TextButton(
                                onClick = {
                                    viewModel.setMerchantCategory(merchantName, category.name, applyToPast)
                                    showCategoryPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(category.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("إلغاء") } }
        )
    }
}
