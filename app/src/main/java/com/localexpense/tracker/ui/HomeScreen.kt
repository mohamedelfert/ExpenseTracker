package com.localexpense.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.viewmodel.ImportState
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    val expandedYears = remember { mutableStateMapOf<String, Boolean>() }
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }

    val isImporting = importState is ImportState.Running

    val yearFormatter = remember { SimpleDateFormat("yyyy", Locale("ar")) }
    val monthFormatter = remember { SimpleDateFormat("MMMM", Locale("ar")) }
    val timeFormatter = remember { SimpleDateFormat("dd MMMM - hh:mm a", Locale("ar")) }

    val importStatusMessage = remember(importState) {
        when (val state = importState) {
            is ImportState.Running -> "جاري استيراد وقراءة الرسائل..."
            is ImportState.Done -> "تم فحص ${state.scanned} رسالة واستيراد ${state.imported} مصروف جديد"
            is ImportState.Error -> state.message
            else -> null
        }
    }

    val currentMonthTotal = remember(expenses) {
        val currentCal = Calendar.getInstance()
        val currentMonth = currentCal.get(Calendar.MONTH)
        val currentYear = currentCal.get(Calendar.YEAR)
        val expCal = Calendar.getInstance()

        expenses.filter { expense ->
            expCal.timeInMillis = expense.timestamp
            expCal.get(Calendar.MONTH) == currentMonth && expCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.amount }
    }

    val groupedData = remember(expenses) {
        expenses.sortedByDescending { it.timestamp }
            .groupBy { yearFormatter.format(Date(it.timestamp)) }
            .mapValues { (_, yearExpenses) ->
                yearExpenses.groupBy { monthFormatter.format(Date(it.timestamp)) }
                    .mapValues { (_, monthExpenses) ->
                        monthExpenses.groupBy { it.bankName }
                    }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مصروفاتي", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = onOpenAddExpense) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة مصروف", tint = Color.White)
                    }
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Default.BarChart, contentDescription = "الإحصائيات", tint = Color.White)
                    }
                    IconButton(onClick = onOpenRules) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات والقواعد", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!smsPermissionGranted) {
                SmsPermissionCard(
                    onRequestSmsPermission = onRequestSmsPermission,
                    onOpenAppSettings = onOpenAppSettings
                )
            }

            Button(
                onClick = {
                    if (smsPermissionGranted) viewModel.importFromInbox() else onRequestSmsPermission()
                },
                enabled = !isImporting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري الاستيراد...", color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مزامنة واستيراد الرسائل", color = Color.White)
                }
            }

            if (!importStatusMessage.isNullOrEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (importState is ImportState.Error) Color(0xFF3E2723) else Color(0xFF1E3A2B)
                    )
                ) {
                    Text(
                        text = importStatusMessage,
                        color = if (importState is ImportState.Error) Color(0xFFFFAB91) else Color(0xFFA5D6A7),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إجمالي مصروفات الشهر الحالي", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format(Locale.US, "%.2f", currentMonthTotal)} ج.م",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80CBC4)
                        )
                    )
                }
            }

            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد مصروفات مسجلة بعد", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedData.forEach { (year, monthsMap) ->
                        val isYearExpanded = expandedYears[year] ?: true

                        item(key = "year-$year") {
                            val yearTotal = remember(monthsMap) {
                                monthsMap.values.flatMap { it.values.flatten() }.sumOf { it.amount }
                            }
                            HeaderCard(
                                title = "سنة $year",
                                amount = yearTotal,
                                isExpanded = isYearExpanded,
                                onClick = { expandedYears[year] = !isYearExpanded },
                                containerColor = Color(0xFF0F2027),
                                titleColor = Color.White,
                                amountColor = Color(0xFF4DB6AC)
                            )
                        }

                        if (isYearExpanded) {
                            monthsMap.forEach { (monthName, banksMap) ->
                                val monthKey = "$year-$monthName"
                                val isMonthExpanded = expandedMonths[monthKey] ?: true

                                item(key = "month-$monthKey") {
                                    val monthTotal = remember(banksMap) {
                                        banksMap.values.flatten().sumOf { it.amount }
                                    }
                                    HeaderCard(
                                        title = monthName,
                                        amount = monthTotal,
                                        isExpanded = isMonthExpanded,
                                        onClick = { expandedMonths[monthKey] = !isMonthExpanded },
                                        containerColor = Color(0xFF1F2937),
                                        titleColor = Color.White,
                                        amountColor = Color(0xFF80CBC4),
                                        paddingStart = 12.dp
                                    )
                                }

                                if (isMonthExpanded) {
                                    banksMap.forEach { (bankName, bankExpenses) ->
                                        item(key = "bank-$monthKey-$bankName") {
                                            BankExpensesCard(
                                                bankName = bankName,
                                                expenses = bankExpenses,
                                                timeFormatter = timeFormatter
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsPermissionCard(
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "إذن قراءة الرسائل مطلوب",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "يحتاج التطبيق لإذن قراءة الرسائل القصيرة ليتعرف على المعاملات البنكية ويقرأ المصروفات تلقائياً.",
                color = Color(0xFFFFE0B2),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenAppSettings) {
                    Text("فتح الإعدادات", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestSmsPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                ) {
                    Text("منح الإذن الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    title: String,
    amount: Double,
    isExpanded: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    titleColor: Color,
    amountColor: Color,
    paddingStart: androidx.compose.ui.unit.Dp = 0.dp
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = amountColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                )
            }
            Text(
                text = "${String.format(Locale.US, "%.2f", amount)} ج.م",
                fontWeight = FontWeight.Bold,
                color = amountColor,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun BankExpensesCard(
    bankName: String,
    expenses: List<Expense>,
    timeFormatter: SimpleDateFormat
) {
    val bankTotal = remember(expenses) { expenses.sumOf { it.amount } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bankName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "${String.format(Locale.US, "%.2f", bankTotal)} ج.م",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color(0xFF333333)
            )

            expenses.forEach { expense ->
                val formattedTime = timeFormatter.format(Date(expense.timestamp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = expense.merchant,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF37474F),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = expense.categoryName,
                                    color = Color(0xFFECEFF1),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedTime,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "-${String.format(Locale.US, "%.2f", expense.amount)} ج.م",
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}