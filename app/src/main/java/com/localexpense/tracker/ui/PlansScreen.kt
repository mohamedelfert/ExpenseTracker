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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.Frequency
import com.localexpense.tracker.data.RecurringExpense
import com.localexpense.tracker.domain.monthlyEquivalentMinor
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.PlansViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الدفعات الدورية والاشتراكات (المراحل 12 و 13) في شاشة واحدة بتابين —
 * الاشتراك هو دفعة دورية بفلاج، فالمنطق واحد والواجهة واحدة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(plans: PlansViewModel, onBack: () -> Unit) {
    val recurring by plans.recurring.collectAsStateWithLifecycle()
    val subscriptions by plans.subscriptions.collectAsStateWithLifecycle()
    val upcoming by plans.upcoming.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale("ar")) }

    val subscriptionsMonthly = subscriptions.filter { it.isActive }
        .sumOf { monthlyEquivalentMinor(it.amountMinor, it.frequency, it.intervalDays) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الدوريات والاشتراكات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("دوريات") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("اشتراكات") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("قادم") })
            }

            when (tab) {
                0 -> PlanList(
                    items = recurring,
                    emptyTitle = "مفيش دفعات دورية",
                    onEdit = { editing = it; showEditor = true },
                    onToggle = { item, active -> plans.setActive(item, active) },
                    onDelete = { plans.delete(it) },
                    dateFormat = dateFormat
                )
                1 -> Column {
                    Text(
                        "إجمالي الاشتراكات الشهرية: ${formatMinor(subscriptionsMonthly)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp)
                    )
                    HorizontalDivider()
                    PlanList(
                        items = subscriptions,
                        emptyTitle = "مفيش اشتراكات",
                        onEdit = { editing = it; showEditor = true },
                        onToggle = { item, active -> plans.setActive(item, active) },
                        onDelete = { plans.delete(it) },
                        dateFormat = dateFormat
                    )
                }
                else -> {
                    if (upcoming.isEmpty()) {
                        EmptyState("مفيش دفعات قادمة", "الدفعات المستحقة في الـ 45 يوم الجاية بتظهر هنا.")
                    } else {
                        LazyColumn {
                            items(upcoming.size) { index ->
                                val payment = upcoming[index]
                                AmountRow(
                                    label = payment.name,
                                    amountMinor = payment.amountMinor,
                                    trailingText = dateFormat.format(Date(payment.dueDate))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        PlanEditorDialog(
            plan = editing,
            defaultSubscription = tab == 1,
            onDismiss = { showEditor = false },
            onSave = { name, amount, category, bank, day, frequency, intervalDays, isSubscription, reminder ->
                plans.savePlan(
                    existing = editing,
                    name = name,
                    amountMinor = amount,
                    categoryName = category,
                    bankName = bank,
                    dayOfMonth = day,
                    frequency = frequency,
                    intervalDays = intervalDays,
                    accountId = editing?.accountId,
                    isSubscription = isSubscription,
                    reminderDaysBefore = reminder
                )
                showEditor = false
            }
        )
    }
}

@Composable
private fun PlanList(
    items: List<RecurringExpense>,
    emptyTitle: String,
    onEdit: (RecurringExpense) -> Unit,
    onToggle: (RecurringExpense, Boolean) -> Unit,
    onDelete: (RecurringExpense) -> Unit,
    dateFormat: SimpleDateFormat
) {
    if (items.isEmpty()) {
        EmptyState(emptyTitle, "أضف دفعة عشان تتسجّل تلقائيًا في موعدها وتظهر في الدفعات القادمة.")
        return
    }
    LazyColumn {
        items(items, key = { it.id }) { item ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formatMinor(item.amountMinor)} • ${frequencyLabel(item.frequency)} • ${item.categoryName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.nextDueDate > 0) {
                        Text(
                            "الدفعة الجاية ${dateFormat.format(Date(item.nextDueDate))}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(checked = item.isActive, onCheckedChange = { onToggle(item, it) })
                TextButton(onClick = { onEdit(item) }) { Text("تعديل") }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف")
                }
            }
            HorizontalDivider()
        }
    }
}

fun frequencyLabel(frequency: Frequency): String = when (frequency) {
    Frequency.DAILY -> "يومي"
    Frequency.WEEKLY -> "أسبوعي"
    Frequency.MONTHLY -> "شهري"
    Frequency.YEARLY -> "سنوي"
    Frequency.CUSTOM -> "مخصص"
}

@Composable
private fun PlanEditorDialog(
    plan: RecurringExpense?,
    defaultSubscription: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, Int, Frequency, Int, Boolean, Int) -> Unit
) {
    var name by remember { mutableStateOf(plan?.displayName ?: "") }
    var amount by remember { mutableStateOf(plan?.let { (it.amountMinor / 100).toString() } ?: "") }
    var category by remember { mutableStateOf(plan?.categoryName ?: "عام") }
    var bank by remember { mutableStateOf(plan?.bankName ?: "كاش") }
    var day by remember { mutableStateOf((plan?.dayOfMonth ?: 1).toString()) }
    var frequency by remember { mutableStateOf(plan?.frequency ?: Frequency.MONTHLY) }
    var interval by remember { mutableStateOf((plan?.intervalDays ?: 30).toString()) }
    var isSubscription by remember { mutableStateOf(plan?.isSubscription ?: defaultSubscription) }
    var reminder by remember { mutableStateOf((plan?.reminderDaysBefore ?: 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "دفعة جديدة" else "تعديل الدفعة") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم (مثال: نتفليكس)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("الفئة") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Frequency.entries.forEach { option ->
                        FilterChip(
                            selected = option == frequency,
                            onClick = { frequency = option },
                            label = { Text(frequencyLabel(option)) }
                        )
                    }
                }
                if (frequency == Frequency.MONTHLY) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it },
                        label = { Text("يوم الخصم (1 - 31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (frequency == Frequency.CUSTOM) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it },
                        label = { Text("كل كام يوم؟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("تنبيه قبل الاستحقاق (أيام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isSubscription, onCheckedChange = { isSubscription = it })
                    Text("اشتراك (يظهر في تاب الاشتراكات)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountMinor = parseAmountMinor(amount)
                if (name.isNotBlank() && amountMinor != null && amountMinor > 0) {
                    onSave(
                        name.trim(),
                        amountMinor,
                        category.trim().ifBlank { "عام" },
                        bank.trim().ifBlank { "كاش" },
                        (day.toIntOrNull() ?: 1).coerceIn(1, 31),
                        frequency,
                        interval.toIntOrNull() ?: 30,
                        isSubscription,
                        reminder.toIntOrNull() ?: 1
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
