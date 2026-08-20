package com.localexpense.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.money.minorToPlainDecimal
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.viewmodel.MainViewModel
import com.localexpense.tracker.util.CsvExporter
import kotlinx.coroutines.launch

private val ChartPalette = listOf(
    Color(0xFF0F6E56), Color(0xFFD85A30), Color(0xFFBA7517),
    Color(0xFF3D6FB4), Color(0xFF8A5FBF), Color(0xFF4C9A8E),
    Color(0xFFC2554E), Color(0xFF6E6E6E)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val totalsByCategory by viewModel.monthTotalsByCategory.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allExpenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategoryForBudget by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val csvString = CsvExporter.exportToCsv(allExpenses)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvString.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // إجمالي بوحدات صغرى؛ 1 كحد أدنى عشان نتجنب القسمة على صفر في النِسَب.
    val grandTotal = totalsByCategory.sumOf { it.total }.coerceAtLeast(1L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الداشبورد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("expenses.csv") }) {
                        Icon(Icons.Filled.Download, contentDescription = "تصدير إلى CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة فئة")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (totalsByCategory.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DonutChart(
                        totals = totalsByCategory,
                        grandTotal = grandTotal,
                        modifier = Modifier.size(160.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الإجمالي", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(formatMinor(grandTotal, withDecimals = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                "المصروفات حسب الفئة هذا الشهر",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))

            if (totalsByCategory.isEmpty()) {
                Text(
                    "لسه مفيش مصروفات مسجلة الشهر ده",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                itemsIndexed(totalsByCategory) { index: Int, item: CategoryTotal ->
                    val budget = budgets.find { it.categoryName == item.categoryName }
                    CategoryTotalRow(
                        item = item,
                        color = ChartPalette[index % ChartPalette.size],
                        percentage = (item.total.toDouble() / grandTotal * 100),
                        budgetLimitMinor = budget?.limitMinor,
                        onSetBudget = { selectedCategoryForBudget = item.categoryName }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "الفئات المتاحة",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(categories) { category ->
                    CategoryRow(
                        category = category,
                        onDelete = { viewModel.deleteCategory(category) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name)
                showAddDialog = false
            }
        )
    }

    selectedCategoryForBudget?.let { categoryName ->
        val currentLimit = budgets.find { it.categoryName == categoryName }?.limitMinor ?: 0L
        SetBudgetDialog(
            categoryName = categoryName,
            currentLimitMinor = currentLimit,
            onDismiss = { selectedCategoryForBudget = null },
            onConfirm = { amount ->
                if (amount > 0L) {
                    viewModel.setBudget(categoryName, amount)
                } else {
                    viewModel.deleteBudget(categoryName)
                }
                selectedCategoryForBudget = null
            }
        )
    }
}

@Composable
private fun DonutChart(totals: List<CategoryTotal>, grandTotal: Long, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        var startAngle = -90f
        val strokeWidth = 40f

        totals.forEachIndexed { index, item ->
            val sweepAngle = ((item.total.toDouble() / grandTotal) * 360f).toFloat()
            drawArc(
                color = ChartPalette[index % ChartPalette.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun CategoryTotalRow(item: CategoryTotal, color: Color, percentage: Double, budgetLimitMinor: Long?, onSetBudget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSetBudget)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    item.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMinor(item.total),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "%.0f%%".format(percentage),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        if (budgetLimitMinor != null && budgetLimitMinor > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            val progress = (item.total.toDouble() / budgetLimitMinor).toFloat().coerceIn(0f, 1f)
            val progressColor = when {
                progress >= 1f -> Color(0xFFE53935)
                progress >= 0.8f -> Color(0xFFFB8C00)
                else -> Color(0xFF43A047)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = Color(0xFF263238)
                )
                Text(
                    text = "من ${formatMinor(budgetLimitMinor, withDecimals = false)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(category.name, style = MaterialTheme.typography.bodyMedium)
        if (!category.isBuiltIn) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف الفئة")
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فئة جديدة") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الفئة") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun SetBudgetDialog(
    categoryName: String,
    currentLimitMinor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountText by remember { mutableStateOf(if (currentLimitMinor > 0) minorToPlainDecimal(currentLimitMinor) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الميزانية: $categoryName") },
        text = {
            Column {
                Text(
                    "حدد ميزانية شهرية للفئة دي. سيتم إظهار شريط تقدم وتنبيهك عند الاقتراب من الحد. اكتب 0 للإلغاء.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = parseAmountMinor(amountText) ?: 0L
                onConfirm(amount)
            }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}