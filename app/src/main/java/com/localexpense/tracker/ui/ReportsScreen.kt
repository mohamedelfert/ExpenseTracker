package com.localexpense.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localexpense.tracker.viewmodel.FinanceViewModel
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * التقارير (المرحلة 18، بند 40): سبعة تقارير، تصدير CSV أو PDF لملف
 * المستخدم بيختاره من منتقي النظام. مفيش أي رفع لأي مكان.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val finance: FinanceViewModel = viewModel()
    val message by finance.exportMessage.collectAsStateWithLifecycle()
    var includeRaw by remember { mutableStateOf(viewModel.includeRawTextInExport) }
    var pendingKind by remember { mutableStateOf<FinanceViewModel.ReportKind?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val kind = pendingKind
        if (uri != null && kind != null) finance.exportCsvReport(uri, kind)
    }

    val transactionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) finance.exportTransactionsCsv(uri) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) finance.exportPdfReport(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (message != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { finance.clearExportMessage() }) { Text("تمام") }
                        }
                    }
                }
            }

            item { SectionHeader("تصدير الحركات") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("تضمين نص رسائل البنك", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "مقفول افتراضيًا: النص الخام فيه بيانات حساسة زي أجزاء من أرقام البطاقات والأرصدة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeRaw,
                        onCheckedChange = {
                            includeRaw = it
                            viewModel.setIncludeRawTextInExport(it)
                        }
                    )
                }
            }
            item {
                TextButton(
                    onClick = { transactionsLauncher.launch("transactions.csv") },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("تصدير حركات الشهر (CSV)") }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("تقارير مجمّعة") }

            items(FinanceViewModel.ReportKind.entries.size) { index ->
                val kind = FinanceViewModel.ReportKind.entries[index]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(kind.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        pendingKind = kind
                        csvLauncher.launch("${kind.name.lowercase()}.csv")
                    }) { Text("CSV") }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                TextButton(
                    onClick = { pdfLauncher.launch("financial-report.pdf") },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("تقرير PDF كامل للشهر") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
