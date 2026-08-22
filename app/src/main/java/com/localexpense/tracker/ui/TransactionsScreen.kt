package com.localexpense.tracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.TransactionSort
import com.localexpense.tracker.data.TransactionSource
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.monthRangeOffset
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة الحركات: بحث + فلاتر + ترتيب (المرحلة 3). البحث والفلترة بتحصل في
 * SQL (راجع TransactionQuery) — مفيش فلترة في الذاكرة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val banks by viewModel.bankNames.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale("ar")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الحركات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "فلاتر")
                    }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "ترتيب")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        TransactionSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    viewModel.updateFilter { it.copy(sort = sort) }
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = filter.text,
                onValueChange = { text -> viewModel.updateFilter { it.copy(text = text) } },
                label = { Text("بحث: جهة، فئة، بنك، ملاحظة، مرجع، مبلغ") },
                singleLine = true,
                trailingIcon = {
                    if (filter.text.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateFilter { it.copy(text = "") } }) {
                            Icon(Icons.Filled.Clear, contentDescription = "مسح")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // فلاتر سريعة: نوع الحركة + الشهر الحالي
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = {
                            viewModel.updateFilter { current ->
                                current.copy(
                                    types = if (type in current.types) current.types - type else current.types + type
                                )
                            }
                        },
                        label = { Text(typeLabel(type)) }
                    )
                }
                AssistChip(
                    onClick = {
                        val range = monthRangeOffset(0)
                        viewModel.updateFilter { it.copy(startTime = range.start, endTime = range.end) }
                    },
                    label = { Text("الشهر الحالي") }
                )
                if (!filter.isEmpty) {
                    AssistChip(onClick = { viewModel.clearFilter() }, label = { Text("مسح الفلاتر") })
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "${results.size} حركة — الإجمالي ${formatMinor(results.sumOf { it.amountMinor })}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (results.isEmpty()) {
                EmptyState(
                    title = "مفيش نتايج",
                    hint = "جرّب كلمة تانية أو امسح الفلاتر."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { expense ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onOpenTransaction(expense.id)
                                        false
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteExpense(expense)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        )
                        
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val color = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                }
                                val icon = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                    else -> null
                                }
                                val alignment = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 4.dp, horizontal = 16.dp)
                                        .background(color, MaterialTheme.shapes.medium)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (icon != null) {
                                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        ) {
                            SoftCard(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                                AmountRow(
                                    label = expense.merchant,
                                    amountMinor = expense.amountMinor,
                                    leading = { 
                                        IconBadge(
                                            icon = getCategoryIcon(expense.categoryName), 
                                            tint = getCategoryColor(expense.categoryName), 
                                            size = 36.dp
                                        ) 
                                    },
                                    trailingText = "${expense.categoryName} • ${timeFormat.format(Date(expense.timestamp))}",
                                    onClick = { onOpenTransaction(expense.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FiltersDialog(
            categories = categories.map { it.name },
            accounts = accounts.map { it.id to it.name },
            banks = banks,
            currentCategory = filter.categoryName,
            currentAccountId = filter.accountId,
            currentBank = filter.bankName,
            currentSource = filter.source,
            currentMin = filter.minAmountMinor,
            currentMax = filter.maxAmountMinor,
            onDismiss = { showFilters = false },
            onApply = { category, accountId, bank, source, min, max ->
                viewModel.updateFilter {
                    it.copy(
                        categoryName = category,
                        accountId = accountId,
                        bankName = bank,
                        source = source,
                        minAmountMinor = min,
                        maxAmountMinor = max
                    )
                }
                showFilters = false
            }
        )
    }
}

fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "مصروف"
    TransactionType.INCOME -> "دخل"
    TransactionType.TRANSFER -> "تحويل"
    TransactionType.REFUND -> "استرداد"
}

fun sourceLabel(source: TransactionSource): String = when (source) {
    TransactionSource.MANUAL -> "يدوي"
    TransactionSource.SMS -> "رسالة SMS"
    TransactionSource.NOTIFICATION -> "إشعار بنك"
    TransactionSource.IMPORT -> "استيراد"
    TransactionSource.RECURRING -> "دورية"
}

@Composable
private fun FiltersDialog(
    categories: List<String>,
    accounts: List<Pair<Long, String>>,
    banks: List<String>,
    currentCategory: String?,
    currentAccountId: Long?,
    currentBank: String?,
    currentSource: TransactionSource?,
    currentMin: Long?,
    currentMax: Long?,
    onDismiss: () -> Unit,
    onApply: (String?, Long?, String?, TransactionSource?, Long?, Long?) -> Unit
) {
    var category by remember { mutableStateOf(currentCategory) }
    var accountId by remember { mutableStateOf(currentAccountId) }
    var bank by remember { mutableStateOf(currentBank) }
    var source by remember { mutableStateOf(currentSource) }
    var minText by remember { mutableStateOf(currentMin?.let { (it / 100).toString() } ?: "") }
    var maxText by remember { mutableStateOf(currentMax?.let { (it / 100).toString() } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فلاتر") },
        text = {
            Column {
                ChipRow("الفئة", categories, category) { category = it }
                ChipRow("البنك", banks, bank) { bank = it }
                ChipRow(
                    "الحساب",
                    accounts.map { it.second },
                    accounts.firstOrNull { it.first == accountId }?.second
                ) { name -> accountId = accounts.firstOrNull { it.second == name }?.first }
                ChipRow(
                    "المصدر",
                    TransactionSource.entries.map { sourceLabel(it) },
                    source?.let { sourceLabel(it) }
                ) { label -> source = TransactionSource.entries.firstOrNull { sourceLabel(it) == label } }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("من مبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("إلى مبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    category, accountId, bank, source,
                    parseAmountMinor(minText), parseAmountMinor(maxText)
                )
            }) { Text("تطبيق") }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(null, null, null, null, null, null)
            }) { Text("مسح الكل") }
        }
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    if (options.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(if (option == selected) null else option) },
                label = { Text(option) }
            )
        }
    }
}
