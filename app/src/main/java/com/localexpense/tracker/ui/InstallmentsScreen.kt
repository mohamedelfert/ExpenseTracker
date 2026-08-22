package com.localexpense.tracker.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.localexpense.tracker.data.Installment
import com.localexpense.tracker.domain.firstDueDateForDayOfMonth
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.PlansViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الأقساط (المرحلة 14).
 *
 * القاعدة المهمة: إجمالي المشترى مش بيتسجّل كحركة مالية أبدًا — بس القسط
 * الشهري لما تضغط "سجّل قسط". كده تحليلات الشهر مبتعدّش نفس الفلوس مرتين.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(plans: PlansViewModel, onBack: () -> Unit) {
    val installments by plans.installments.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Installment?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("ar")) }

    val monthlyLoad = installments.filter { it.isActive }.sumOf { it.installmentMinor }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الأقساط") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "قسط جديد")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "الالتزام الشهري من الأقساط النشطة: ${formatMinor(monthlyLoad)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                "إجمالي المشترى مش بيتحسب كمصروف — القسط الشهري بس هو اللي بيتسجّل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (installments.isEmpty()) {
                EmptyState(
                    title = "مفيش أقساط",
                    hint = "أضف مشترى مقسّط عشان نتابع المدفوع والمتبقي.",
                    icon = Icons.Filled.Add,
                    actionLabel = "أضف قسط",
                    onAction = { editing = null; showEditor = true }
                )
            } else {
                LazyColumn {
                    items(installments, key = { it.id }) { item ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(icon = getCategoryIcon(item.categoryName), tint = getCategoryColor(item.categoryName), size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${formatMinor(item.installmentMinor)} شهريًا • " +
                                            "${item.paidCount}/${item.count} مدفوع",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "متبقي ${formatMinor(item.remainingMinor)} من إجمالي ${formatMinor(item.totalMinor)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (item.nextDueDate > 0 && item.isActive) {
                                        Text(
                                            "القسط الجاي ${dateFormat.format(Date(item.nextDueDate))}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                TextButton(onClick = { editing = item; showEditor = true }) { Text("تعديل") }
                                IconButton(onClick = { plans.deleteInstallment(item) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { item.paidCount.toFloat() / item.count.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { plans.payInstallment(item) },
                                enabled = item.remainingCount > 0
                            ) {
                                Text(if (item.remainingCount > 0) "سجّل قسط" else "مكتمل")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showEditor) {
        InstallmentEditorDialog(
            installment = editing,
            onDismiss = { showEditor = false },
            onSave = {
                plans.saveInstallment(it)
                showEditor = false
            }
        )
    }
}

@Composable
private fun InstallmentEditorDialog(
    installment: Installment?,
    onDismiss: () -> Unit,
    onSave: (Installment) -> Unit
) {
    var title by remember { mutableStateOf(installment?.title ?: "") }
    var total by remember { mutableStateOf(installment?.let { (it.totalMinor / 100).toString() } ?: "") }
    var count by remember { mutableStateOf((installment?.count ?: 12).toString()) }
    var merchant by remember { mutableStateOf(installment?.merchant ?: "") }
    var category by remember { mutableStateOf(installment?.categoryName ?: "عام") }
    var day by remember { mutableStateOf("1") }

    val totalMinor = parseAmountMinor(total) ?: 0L
    val countValue = count.toIntOrNull() ?: 0
    val perInstallment = if (countValue > 0) totalMinor / countValue else 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (installment == null) "مشترى مقسّط" else "تعديل القسط") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("الاسم (مثال: لابتوب)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it },
                    label = { Text("الإجمالي") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it },
                    label = { Text("عدد الأقساط") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("الجهة (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("الفئة") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("يوم الاستحقاق في الشهر") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (perInstallment > 0) {
                    Text(
                        "القسط الشهري: ${formatMinor(perInstallment)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && totalMinor > 0 && countValue > 0) {
                    val now = System.currentTimeMillis()
                    val due = firstDueDateForDayOfMonth(now, (day.toIntOrNull() ?: 1).coerceIn(1, 31))
                    onSave(
                        (installment ?: Installment(
                            title = title,
                            totalMinor = totalMinor,
                            installmentMinor = perInstallment,
                            count = countValue,
                            startDate = now,
                            nextDueDate = due
                        )).copy(
                            title = title.trim(),
                            totalMinor = totalMinor,
                            installmentMinor = perInstallment,
                            count = countValue,
                            merchant = merchant.trim(),
                            categoryName = category.trim().ifBlank { "عام" },
                            nextDueDate = if (installment == null) due else installment.nextDueDate
                        )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
