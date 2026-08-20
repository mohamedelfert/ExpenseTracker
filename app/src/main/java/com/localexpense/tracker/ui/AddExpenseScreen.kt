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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.Account
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.util.parseAmountMinor

/**
 * إضافة حركة يدوية: مصروف أو دخل أو استرداد (المرحلة 6 ضافت الدخل)،
 * مع حساب اختياري وملاحظة. التحويل بين حسابين مكانه شاشة الحسابات لأنه
 * محتاج حسابين.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    categories: List<Category>,
    accounts: List<Account> = emptyList(),
    onSaveTransaction: (
        amountMinor: Long,
        merchant: String,
        categoryName: String,
        type: TransactionType,
        accountId: Long?,
        note: String
    ) -> Unit,
    onBack: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var expanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة حركة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND).forEach { option ->
                    FilterChip(
                        selected = option == type,
                        onClick = { type = option },
                        label = { Text(typeLabel(option)) }
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; errorMessage = null },
                label = { Text("المبلغ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errorMessage != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it; errorMessage = null },
                label = { Text(if (type == TransactionType.INCOME) "المصدر (مثال: راتب)" else "الوصف / الجهة") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "اختر فئة",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("الفئة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "بدون حساب",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الحساب (اختياري)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("بدون حساب") },
                            onClick = { selectedAccount = null; accountExpanded = false }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedAccount = account; accountExpanded = false }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("ملاحظة (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val amount = parseAmountMinor(amountText)
                    when {
                        amount == null -> errorMessage = "اكتب رقم صحيح في خانة المبلغ"
                        amount <= 0L -> errorMessage = "المبلغ لازم يكون أكبر من صفر"
                        merchantText.isBlank() -> errorMessage = "اكتب وصف أو اسم الجهة"
                        else -> {
                            onSaveTransaction(
                                amount,
                                merchantText.trim(),
                                selectedCategory?.name ?: "عام",
                                type,
                                selectedAccount?.id,
                                noteText.trim()
                            )
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ")
            }
        }
    }
}
