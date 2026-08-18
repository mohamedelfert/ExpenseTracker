package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.viewmodel.ImportState
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    smsPermissionGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    val monthTotal by viewModel.monthTotal.collectAsState()
    val bySource by viewModel.monthTotalsBySource.collectAsState()
    val importState by viewModel.importState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مصروفاتي") },
                actions = {
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Filled.PieChart, contentDescription = "الداشبورد")
                    }
                    IconButton(onClick = onOpenRules) {
                        Icon(Icons.Filled.Settings, contentDescription = "قواعد الرسائل")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة مصروف يدوي")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (!smsPermissionGranted) {
                PermissionBanner(
                    onRequest = onRequestSmsPermission,
                    onOpenSettings = onOpenAppSettings
                )
            } else {
                ImportInboxCard(
                    importState = importState,
                    onImport = { viewModel.importFromInbox() },
                    onDismiss = { viewModel.resetImportState() }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("إجمالي مصروفات الشهر", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "%.2f ج.م".format(monthTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconCircle(icon = Icons.Filled.Wallet, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bySource.take(2).forEach { s ->
                    Card(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.source, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("%.2f".format(s.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(expenses) { expense ->
                    ExpenseRow(expense)
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MarkEmailUnread, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(
                    "التطبيق محتاج إذن قراءة الرسائل عشان يسجل المصروفات تلقائيًا",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("منح إذن قراءة الرسائل")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "لو ضغطت \"منح الإذن\" قبل كده وماظهرش سؤال، افتح إعدادات التطبيق يدويًا وفعّل إذن الرسائل من هناك:",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("فتح إعدادات التطبيق")
            }
        }
    }
}

@Composable
private fun ImportInboxCard(
    importState: ImportState,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when (importState) {
                is ImportState.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Text(
                            "عندك رسائل قديمة من قبل ما تنزّل التطبيق؟",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp).weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("استيراد المصروفات من الرسائل القديمة")
                    }
                }
                is ImportState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            "جاري فحص الرسائل...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                is ImportState.Done -> {
                    LaunchedEffect(importState) { /* keep result visible until user dismisses */ }
                    Text(
                        "تم فحص ${importState.scanned} رسالة، وتسجيل ${importState.imported} مصروف جديد.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("تمام")
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCircle(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

private fun categoryIcon(expense: Expense): ImageVector = when {
    expense.merchant.contains("سوبر", true) || expense.merchant.contains("ماركت", true) -> Icons.Filled.ShoppingCart
    expense.merchant.contains("فاتورة", true) -> Icons.Filled.Receipt
    expense.source.contains("Insta", true) -> Icons.Filled.PhoneAndroid
    expense.source == "يدوي" -> Icons.Filled.Wallet
    else -> Icons.Filled.AccountBalance
}

@Composable
private fun ExpenseRow(expense: Expense) {
    val timeFormat = remember(expense.timestampMillis) {
        SimpleDateFormat("hh:mm a", Locale("ar"))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = if (expense.isConfirmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            IconCircle(icon = categoryIcon(expense), tint = tint)
            Spacer(Modifier.height(0.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(expense.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val subtitle = expense.source + " · " + timeFormat.format(Date(expense.timestampMillis)) +
                    if (!expense.isConfirmed) " · غير مؤكد" else ""
                Text(subtitle, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            "-%.2f".format(expense.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error
        )
    }
}
