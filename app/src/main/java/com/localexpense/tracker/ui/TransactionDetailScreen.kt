package com.localexpense.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.ui.theme.finance
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * تفاصيل الحركة (المرحلة 3، بند 10) + إجراءاتها: تعديل الفئة والجهة،
 * إضافة ملاحظة، تأكيد الحركة، عمل قاعدة منها، وحذفها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: MainViewModel,
    transactionId: Long,
    onBack: () -> Unit
) {
    val transaction by viewModel.observeTransaction(transactionId).collectAsState(initial = null)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showMerchantDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }
    var showAmountDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }

    // الحذف بيعرض Snackbar فيه "تراجع" بدل ما يرجع على طول: لو المستخدم
    // ضغط تراجع بنعيد الحركة، ولو المهلة خلصت من غير تفاعل بنرجع للشاشة
    // اللي قبلها.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale("ar")) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل الحركة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    transaction?.let { item ->
                        IconButton(onClick = { viewModel.setVerified(item, !item.isVerified) }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "تأكيد",
                                tint = if (item.isVerified) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val item = transaction
        if (item == null) {
            EmptyState(title = "الحركة دي مش موجودة", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = getCategoryIcon(item.categoryName), tint = getCategoryColor(item.categoryName), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                formatMinor(item.amountMinor, item.currency),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showAmountDialog = true }
                            )
                            Text("${typeLabel(item.type)} • ${sourceLabel(item.source)}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (!item.isVerified) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.finance.warning.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.finance.warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("غير مؤكدة — راجع الجهة والمبلغ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.finance.warning)
                        }
                    }
                }
            }

            DetailRow("الجهة", item.merchant) { showMerchantDialog = true }
            DetailRow("الفئة", item.categoryName) { showCategoryDialog = true }
            DetailRow("الحساب", accounts.firstOrNull { it.id == item.accountId }?.name ?: "غير محدد")
            DetailRow("البنك", item.bankName)
            DetailRow("التاريخ", dateFormat.format(Date(item.timestamp))) { showDateDialog = true }
            DetailRow("الوقت", timeFormat.format(Date(item.timestamp))) { showDateDialog = true }
            DetailRow("رقم المرجع", item.referenceId.ifBlank { "غير متاح" })
            DetailRow("ملاحظة", item.note.ifBlank { "أضف ملاحظة" }) { showNoteDialog = true }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.createRuleFromTransaction(item) },
                    modifier = Modifier.weight(1f)
                ) { Text("اعمل قاعدة للجهة دي") }
            }

            if (item.rawBody.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = showRawText, onCheckedChange = { showRawText = it })
                    Text("إظهار نص الرسالة الأصلي", style = MaterialTheme.typography.bodySmall)
                }
                if (showRawText) {
                    Text(
                        item.rawBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val item = transaction
    if (item != null && showCategoryDialog) {
        var applyToMerchant by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("تغيير الفئة") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyToMerchant, onCheckedChange = { applyToMerchant = it })
                        Text(
                            "طبّق على كل حركات \"${item.merchant}\" واحفظ القاعدة",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.height(280.dp)) {
                        items(categories) { category ->
                            TextButton(
                                onClick = {
                                    viewModel.changeCategory(item, category.name, applyToMerchant)
                                    showCategoryDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(category.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (item != null && showMerchantDialog) {
        var name by remember { mutableStateOf(item.merchant) }
        AlertDialog(
            onDismissRequest = { showMerchantDialog = false },
            title = { Text("تغيير الجهة") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الجهة") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.changeMerchant(item, name.trim())
                    showMerchantDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showMerchantDialog = false }) { Text("إلغاء") } }
        )
    }

    if (item != null && showNoteDialog) {
        var note by remember { mutableStateOf(item.note) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("ملاحظة") },
            text = {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("اكتب ملاحظة") })
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateNote(item, note.trim())
                    showNoteDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("إلغاء") } }
        )
    }

    if (item != null && showAmountDialog) {
        AmountDialog(
            title = "تعديل المبلغ",
            hint = "تعديل مبلغ حركة اتسجلت غلط (مثلاً الـ parser قرا رقم ناقص).",
            initialMinor = item.amountMinor,
            onDismiss = { showAmountDialog = false },
            onConfirm = { amount ->
                if (amount > 0L) viewModel.updateExpense(item.copy(amountMinor = amount))
                showAmountDialog = false
            }
        )
    }

    if (item != null && showDateDialog) {
        DateTimeDialog(
            initialTimestamp = item.timestamp,
            onDismiss = { showDateDialog = false },
            onConfirm = { timestamp ->
                viewModel.updateExpense(item.copy(timestamp = timestamp))
                showDateDialog = false
            }
        )
    }

    if (item != null && showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الحركة") },
            text = { Text("هتتشال من التطبيق. هيظهرلك زرار \"تراجع\" لحظة عشان ترجّعها لو غيّرت رأيك.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteExpense(item)
                    showDeleteConfirm = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "تم حذف الحركة",
                            actionLabel = "تراجع",
                            duration = SnackbarDuration.Short
                        )
                        // item هنا قيمة متسكة في الـ lambda، فبتفضل صالحة حتى
                        // بعد ما الحركة تتشال من قاعدة البيانات والـ state
                        // بقى null.
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreExpense(item)
                        } else {
                            onBack()
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") } }
        )
    }
}

/**
 * تعديل تاريخ ووقت الحركة. إدخال نصي (yyyy-MM-dd و HH:mm) بدل DatePicker
 * الكامل: الشاشة دي بتُستخدم لتصحيح حركة ملتقطة غلط، والإدخال النصي أسرع
 * وأقل كود من منتقي تاريخ + منتقي وقت.
 */
@Composable
private fun DateTimeDialog(
    initialTimestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    var dateText by remember { mutableStateOf(dateFormat.format(Date(initialTimestamp))) }
    var timeText by remember { mutableStateOf(timeFormat.format(Date(initialTimestamp))) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل التاريخ والوقت") },
        text = {
            Column {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; error = null },
                    label = { Text("التاريخ (yyyy-MM-dd)") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it; error = null },
                    label = { Text("الوقت (HH:mm)") }
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .parse("${dateText.trim()} ${timeText.trim()}")
                }.getOrNull()
                if (parsed == null) error = "الصيغة غلط" else onConfirm(parsed.time)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.then(Modifier.padding(0.dp)) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) {
            TextButton(onClick = onClick) { Text(value) }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}