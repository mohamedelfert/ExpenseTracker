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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.Account
import com.localexpense.tracker.data.AccountType
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * الحسابات والمحافظ (المرحلة 5) + تحويل بين حسابين.
 *
 * الحسابات اختيارية تمامًا: التطبيق بيشتغل من غيرها، واللي مش محتاجها
 * مش مضطر يفتح الشاشة دي أصلاً.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الحسابات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { showTransfer = true }, enabled = accounts.size >= 2) {
                        Text("تحويل")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "حساب جديد")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "الحسابات اختيارية. لو ضفتها، كل حركة تقدر تتربط بحساب ويظهر رصيده محسوبًا من حركاته.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            if (accounts.isEmpty()) {
                EmptyState(
                    title = "مفيش حسابات",
                    hint = "أضف بنك أو محفظة أو كاش لو حابب تتابع أرصدتها.",
                    icon = Icons.Filled.AccountBalanceWallet,
                    actionLabel = "أضف حساب",
                    onAction = { editing = null; showEditor = true }
                )
            } else {
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        val balance by produceState(initialValue = 0L, account.id) {
                            value = viewModel.accountBalance(account.id)
                        }
                        SoftCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${accountTypeLabel(account.type)} • رصيد ${formatMinor(balance, account.currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = account.isActive,
                                    onCheckedChange = { viewModel.saveAccount(account.copy(isActive = it)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { editing = account; showEditor = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteAccount(account) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AccountEditorDialog(
            account = editing,
            onDismiss = { showEditor = false },
            onSave = { account ->
                viewModel.saveAccount(account)
                showEditor = false
            }
        )
    }

    if (showTransfer) {
        TransferDialog(
            accounts = accounts,
            onDismiss = { showTransfer = false },
            onConfirm = { amount, from, to, note ->
                viewModel.addTransfer(amount, from, to, note)
                showTransfer = false
            }
        )
    }
}

fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.BANK -> "بنك"
    AccountType.CASH -> "كاش"
    AccountType.WALLET -> "محفظة"
    AccountType.CREDIT_CARD -> "بطاقة ائتمان"
    AccountType.OTHER -> "أخرى"
}

@Composable
private fun AccountEditorDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: AccountType.BANK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "حساب جديد" else "تعديل الحساب") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم (مثال: CIB، فودافون كاش)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountType.entries.forEach { option ->
                        FilterChip(
                            selected = option == type,
                            onClick = { type = option },
                            label = { Text(accountTypeLabel(option)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave((account ?: Account(name = name, type = type)).copy(name = name.trim(), type = type))
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun TransferDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?, Long?, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var toId by remember { mutableStateOf(accounts.getOrNull(1)?.id) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحويل بين حسابين") },
        text = {
            Column {
                Text(
                    "التحويل مش مصروف ومش دخل — مش بيدخل في أي حساب للصرف أو الصافي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("من", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == fromId,
                            onClick = { fromId = account.id },
                            label = { Text(account.name) }
                        )
                    }
                }
                Text("إلى", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == toId,
                            onClick = { toId = account.id },
                            label = { Text(account.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = parseAmountMinor(amountText)
                if (amount != null && amount > 0 && fromId != toId) {
                    onConfirm(amount, fromId, toId, note.trim())
                }
            }) { Text("تحويل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
