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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.Account
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.ui.theme.finance
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

    val typeAccent = when (type) {
        TransactionType.INCOME -> MaterialTheme.finance.income
        TransactionType.REFUND -> MaterialTheme.finance.transfer
        else -> MaterialTheme.finance.expense
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة حركة", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== نوع الحركة: شرائح كبيرة بأيقونة، اللون بيتغيّر مع الاختيار =====
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(TransactionType.EXPENSE, Icons.Default.SouthWest, MaterialTheme.finance.expense),
                    Triple(TransactionType.INCOME, Icons.Default.Payments, MaterialTheme.finance.income),
                    Triple(TransactionType.REFUND, Icons.Default.Undo, MaterialTheme.finance.transfer)
                ).forEach { (option, icon, color) ->
                    FilterChip(
                        selected = option == type,
                        onClick = { type = option },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(typeLabel(option), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                        shape = com.localexpense.tracker.ui.theme.PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.16f),
                            selectedLabelColor = color,
                            selectedLeadingIconColor = color
                        )
                    )
                }
            }

            // ===== المبلغ: أكبر عنصر في الشاشة، وسط البطاقة، بلون نوع الحركة =====
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        "المبلغ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; errorMessage = null },
                        placeholder = { Text("0.00", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = errorMessage != null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            textAlign = TextAlign.Center,
                            color = typeAccent
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ===== تفاصيل الحركة =====
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it; errorMessage = null },
                        label = { Text(if (type == TransactionType.INCOME) "المصدر (مثال: راتب)" else "الوصف / الجهة") },
                        leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                        singleLine = true,
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
                            leadingIcon = {
                                val currentCategory = selectedCategory?.name
                                Icon(
                                    if (currentCategory != null) getCategoryIcon(currentCategory) else Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = if (currentCategory != null) getCategoryColor(currentCategory) else LocalContentColor.current
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    leadingIcon = { IconBadge(icon = getCategoryIcon(category.name), tint = getCategoryColor(category.name), size = 28.dp) },
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
                                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
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
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

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
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = typeAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("حفظ", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
