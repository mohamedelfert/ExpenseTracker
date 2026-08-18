package com.localexpense.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

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

    val importStatusMessage = when (importState) {
        is com.localexpense.tracker.viewmodel.ImportState.Running -> "جاري الاستيراد..."
        is com.localexpense.tracker.viewmodel.ImportState.Done -> {
            val done = importState as com.localexpense.tracker.viewmodel.ImportState.Done
            "تم استيراد ${done.imported} من ${done.scanned} رسالة"
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مصروفاتي",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Button(
                onClick = { viewModel.importFromInbox() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("مزامنة / استيراد", color = Color.White)
            }
        }

        if (!importStatusMessage.isNullOrEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2B))
            ) {
                Text(
                    text = importStatusMessage,
                    color = Color(0xFFA5D6A7),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val currentMonthTotal = remember(expenses) {
            val currentCal = Calendar.getInstance()
            expenses.filter { expense ->
                val expCal = Calendar.getInstance().apply { timeInMillis = expense.timestamp }
                expCal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH) &&
                        expCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)
            }.sumOf { it.amount }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("إجمالي مصروفات الشهر الحالي", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format(Locale.US, "%.2f", currentMonthTotal)} ج.م",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
            val groupedByMonth = remember(expenses) {
                expenses.groupBy { expense ->
                    val cal = Calendar.getInstance().apply { timeInMillis = expense.timestamp }
                    SimpleDateFormat("MMMM yyyy", Locale("ar")).format(cal.time)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedByMonth.forEach { (monthYear, monthExpenses) ->
                    item {
                        Text(
                            text = monthYear,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4)
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    val groupedByBank = monthExpenses.groupBy { it.bankName }

                    groupedByBank.forEach { (bankName, bankExpenses) ->
                        item {
                            val bankTotal = bankExpenses.sumOf { it.amount }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.2f", bankTotal)} ج.م",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF80CBC4),
                                            fontSize = 15.sp
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color(0xFF333333)
                                    )

                                    bankExpenses.forEach { expense ->
                                        val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
                                        val formattedTime = timeFormat.format(Date(expense.timestamp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = expense.merchant,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = formattedTime,
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
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
                    }
                }
            }
        }
    }
}
