package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.localexpense.tracker.money.minorToPlainDecimal
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.FinanceViewModel
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * الميزانيات (المرحلة 7): ميزانية كلية للشهر + ميزانية لكل فئة، وكل واحدة
 * بحالتها (آمن/تحذير/تخطي) محسوبة من domain/BudgetStatus.
 *
 * الشاشة دي كمان بتدير الفئات نفسها (إضافة/حذف) — كانت جوه الداشبورد قبل كده،
 * ومكانها الطبيعي جنب الميزانيات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: MainViewModel,
    finance: FinanceViewModel,
    onBack: () -> Unit
) {
    val context by finance.financialContext.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val overall by viewModel.overallBudget.collectAsStateWithLifecycle()

    var editingCategory by remember { mutableStateOf<String?>(null) }
    var editingOverall by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الميزانيات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCategory = true }) {
                Icon(Icons.Filled.Add, contentDescription = "فئة جديدة")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {

            item { SectionHeader("الميزانية الكلية", action = "تعديل", onAction = { editingOverall = true }) }
            item {
                val ctx = context
                if (ctx != null && overall > 0) {
                    BudgetBar(ctx.overallBudget, "صرف ${ctx.monthLabel}")
                } else {
                    EmptyState("مفيش ميزانية كلية", "حدد سقف شهري عشان نحسب المتبقي ونحذّرك قبل ما تتخطاه.")
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("ميزانيات الفئات") }

            items(categories) { category ->
                val ctx = context
                val progress = ctx?.categoryBudget(category.name)
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editingCategory = category.name }) {
                            Text(if (progress != null && progress.limitMinor > 0) "تعديل" else "تحديد")
                        }
                        if (!category.isBuiltIn) {
                            IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف الفئة")
                            }
                        }
                    }
                    if (progress != null && progress.limitMinor > 0) {
                        BudgetBar(progress, "")
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (editingOverall) {
        AmountDialog(
            title = "الميزانية الكلية للشهر",
            hint = "اكتب 0 لإلغاء الميزانية الكلية.",
            initialMinor = overall,
            onDismiss = { editingOverall = false },
            onConfirm = { amount ->
                viewModel.setOverallBudget(amount)
                finance.refresh()
                editingOverall = false
            }
        )
    }

    editingCategory?.let { categoryName ->
        val current = context?.categoryBudgets?.get(categoryName) ?: 0L
        AmountDialog(
            title = "ميزانية \"$categoryName\"",
            hint = "اكتب 0 لإلغاء ميزانية الفئة دي.",
            initialMinor = current,
            onDismiss = { editingCategory = null },
            onConfirm = { amount ->
                if (amount > 0) viewModel.setBudget(categoryName, amount) else viewModel.deleteBudget(categoryName)
                finance.refresh()
                editingCategory = null
            }
        )
    }

    if (showAddCategory) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategory = false },
            title = { Text("فئة جديدة") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الفئة") })
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addCategory(name)
                    showAddCategory = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddCategory = false }) { Text("إلغاء") } }
        )
    }
}

/** حوار إدخال مبلغ موحّد — مستخدم في الميزانيات والأقساط والدوريات. */
@Composable
fun AmountDialog(
    title: String,
    hint: String,
    initialMinor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(if (initialMinor > 0) minorToPlainDecimal(initialMinor) else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parseAmountMinor(text) ?: 0L) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
