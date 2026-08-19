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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.RecurringExpense
import com.localexpense.tracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val recurringExpenses by viewModel.recurringExpenses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصروفات الدورية") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "المصروفات الدورية تتضاف تلقائياً في اليوم المحدد من كل شهر.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (recurringExpenses.isEmpty()) {
                Text(
                    "مفيش مصروفات دورية مسجلة.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp)
                )
            }

            LazyColumn {
                items(recurringExpenses) { expense ->
                    RecurringExpenseRow(expense, onDelete = { viewModel.deleteRecurringExpense(expense) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecurringExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, merchant, bankName, categoryName, day ->
                viewModel.addRecurringExpense(amount, merchant, bankName, categoryName, day)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RecurringExpenseRow(expense: RecurringExpense, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(expense.merchant, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("${expense.amount} ج.م | فئة: ${expense.categoryName}", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                Text("يوم ${expense.dayOfMonth} في الشهر", style = MaterialTheme.typography.bodySmall, color = Color(0xFF80CBC4))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFE57373))
            }
        }
    }
}

@Composable
private fun AddRecurringExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String, Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("كاش") }
    var categoryName by remember { mutableStateOf("عام") }
    var dayOfMonth by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مصروف دوري") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("الجهة (مثال: نتفليكس)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("الفئة") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it },
                    label = { Text("يوم الخصم (1 - 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: return@TextButton
                val d = dayOfMonth.toIntOrNull() ?: return@TextButton
                if (d in 1..31 && merchant.isNotBlank()) {
                    onConfirm(a, merchant, bankName, categoryName, d)
                }
            }) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
