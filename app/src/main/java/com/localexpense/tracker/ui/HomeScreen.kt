package com.localexpense.tracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.BuildConfig
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.viewmodel.CleanupState
import com.localexpense.tracker.viewmodel.ImportState
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    smsPermissionGranted: Boolean,
    notificationAccessGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenTestSms: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenRecurring: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val cleanupState by viewModel.cleanupState.collectAsStateWithLifecycle()

    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showCleanupConfirmDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showImportRangeDialog by remember { mutableStateOf(false) }

    // حالات فتح وإغلاق السنوات والشهور
    val expandedYears = remember { mutableStateMapOf<String, Boolean>() }
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }

    // استخدام mutableStateListOf لدعم Compose Snapshot State بدون مشاكل
    val selectedYears = remember { mutableStateListOf<String>() }
    val archivedYears by viewModel.archivedYears.collectAsStateWithLifecycle()
    val isSelectionMode = selectedYears.isNotEmpty()

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

    val cleanupStatusMessage = remember(cleanupState) {
        when (val state = cleanupState) {
            is CleanupState.Running -> "جاري البحث عن المصروفات المكررة..."
            is CleanupState.Done -> if (state.removed > 0) {
                "تم حذف ${state.removed} مصروف مكرر"
            } else {
                "مفيش مصروفات مكررة"
            }
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
        }.sumOf { it.amountMinor }
    }

    // تجميع البيانات وتصفية السنوات المؤرشفة
    val groupedData = remember(expenses, archivedYears.toList()) {
        expenses.sortedByDescending { it.timestamp }
            .groupBy { yearFormatter.format(Date(it.timestamp)) }
            .filterKeys { year -> year !in archivedYears }
            .mapValues { (_, yearExpenses) ->
                yearExpenses.groupBy { monthFormatter.format(Date(it.timestamp)) }
                    .mapValues { (_, monthExpenses) ->
                        monthExpenses.groupBy { it.bankName }
                    }
            }
    }

    // إجمالي كل سنة مؤرشفة، عشان تظهر في شاشة الأرشيف حتى وهي مخفية من القائمة الرئيسية
    val archivedYearTotals = remember(expenses, archivedYears.toList()) {
        expenses.groupBy { yearFormatter.format(Date(it.timestamp)) }
            .filterKeys { year -> year in archivedYears }
            .mapValues { (_, yearExpenses) -> yearExpenses.sumOf { it.amountMinor } }
            .toList()
            .sortedByDescending { it.first }
    }

    if (showCleanupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirmDialog = false },
            title = { Text("تنظيف المصروفات المكررة") },
            text = {
                Text(
                    "هيتم البحث عن مصروفات بنفس المبلغ والبنك حصلت خلال سلسلة " +
                        "فروق زمنية قصيرة متتالية (حتى لو النص أو اسم الجهة مختلف شوية)، " +
                        "وحذف النسخ الزيادة والاحتفاظ بأقدم واحدة في كل مجموعة فقط. " +
                        "الإجراء ده مينفعش يتراجع فيه."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCleanupConfirmDialog = false
                    viewModel.cleanupDuplicateExpenses()
                }) {
                    Text("تنظيف الآن", color = Color(0xFFF57C00))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showArchiveDialog) {
        ArchiveDialog(
            archivedYearTotals = archivedYearTotals,
            onUnarchive = { year -> viewModel.unarchiveYear(year) },
            onDismiss = { showArchiveDialog = false }
        )
    }

    if (showImportRangeDialog) {
        ImportRangeDialog(
            onDismiss = { showImportRangeDialog = false },
            onConfirm = { start, end ->
                showImportRangeDialog = false
                viewModel.importFromInbox(start, end)
            }
        )
    }

    if (showDisclosureDialog) {
        SmsProminentDisclosureDialog(
            onAccept = {
                showDisclosureDialog = false
                onRequestSmsPermission()
            },
            onDismiss = { showDisclosureDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedYears.size} محدد", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { selectedYears.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.archiveYears(selectedYears.toSet())
                            selectedYears.clear()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "أرشفة", tint = Color(0xFF80CBC4))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF263238))
                )
            } else {
                TopAppBar(
                    title = { Text("مصروفاتي", fontWeight = FontWeight.Bold, color = Color.White) },
                    actions = {
                        IconButton(onClick = { showArchiveDialog = true }) {
                            Icon(Icons.Default.Unarchive, contentDescription = "الأرشيف", tint = Color.White)
                        }
                        IconButton(onClick = onOpenRecurring) {
                            Icon(Icons.Default.DateRange, contentDescription = "الدوريات", tint = Color.White)
                        }
                        IconButton(onClick = onOpenAddExpense) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة مصروف", tint = Color.White)
                        }
                        IconButton(onClick = onOpenDashboard) {
                            Icon(Icons.Default.BarChart, contentDescription = "الإحصائيات", tint = Color.White)
                        }
                        IconButton(onClick = onOpenTestSms) {
                            Icon(Icons.Default.Settings, contentDescription = "اختبار رسالة SMS", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
                )
            }
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // في نسخة "play" مفيش إذن SMS من الأساس (BuildConfig.ENABLE_SMS_IMPORT = false)،
            // فبطاقة الأذونات والزرار بتاعها بيقتصروا على إذن الإشعارات بس.
            val needsPermissionsCard = !notificationAccessGranted ||
                (BuildConfig.ENABLE_SMS_IMPORT && !smsPermissionGranted)

            if (needsPermissionsCard) {
                PermissionsCard(
                    smsGranted = smsPermissionGranted,
                    notifGranted = notificationAccessGranted,
                    onRequestSmsPermission = { showDisclosureDialog = true },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenAppSettings = onOpenAppSettings
                )
            }

            // زرار "استيراد صندوق الوارد" بيحتاج READ_SMS، فمش متاح إلا في نسخة
            // "direct". نسخة "play" بتلتقط المصروفات تلقائيًا من الإشعارات فور
            // وصولها من غير أي زرار مزامنة يدوي.
            if (BuildConfig.ENABLE_SMS_IMPORT) {
                Button(
                    onClick = {
                        if (smsPermissionGranted) showImportRangeDialog = true else showDisclosureDialog = true
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

            TextButton(
                onClick = { showCleanupConfirmDialog = true },
                enabled = cleanupState !is CleanupState.Running,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (cleanupState is CleanupState.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFF57C00),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("تنظيف المصروفات المكررة", color = Color(0xFFF57C00), fontSize = 13.sp)
            }

            if (!cleanupStatusMessage.isNullOrEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Text(
                        text = cleanupStatusMessage,
                        color = Color(0xFFFFCC80),
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
                        text = formatMinor(currentMonthTotal),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80CBC4)
                        )
                    )
                }
            }

            if (groupedData.isEmpty()) {
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
                        val isSelected = year in selectedYears

                        item(key = "year-$year") {
                            val yearTotal = remember(monthsMap) {
                                monthsMap.values.flatMap { it.values.flatten() }.sumOf { it.amountMinor }
                            }

                            HeaderCard(
                                title = "سنة $year",
                                amount = yearTotal,
                                isExpanded = isYearExpanded,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedYears.remove(year) else selectedYears.add(year)
                                    } else {
                                        expandedYears[year] = !isYearExpanded
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        if (year !in selectedYears) selectedYears.add(year)
                                    }
                                },
                                containerColor = if (isSelected) Color(0xFF1B4D3E) else Color(0xFF0F2027),
                                titleColor = Color.White,
                                amountColor = Color(0xFF4DB6AC)
                            )
                        }

                        if (isYearExpanded && !isSelectionMode) {
                            monthsMap.forEach { (monthName, banksMap) ->
                                val monthKey = "$year-$monthName"
                                val isMonthExpanded = expandedMonths[monthKey] ?: true

                                item(key = "month-$monthKey") {
                                    val monthTotal = remember(banksMap) {
                                        banksMap.values.flatten().sumOf { it.amountMinor }
                                    }
                                    HeaderCard(
                                        title = monthName,
                                        amount = monthTotal,
                                        isExpanded = isMonthExpanded,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        onClick = { expandedMonths[monthKey] = !isMonthExpanded },
                                        onLongClick = {},
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
private fun PermissionsCard(
    smsGranted: Boolean,
    notifGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
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
                    text = "أذونات القراءة مطلوبة",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (BuildConfig.ENABLE_SMS_IMPORT) {
                    "يحتاج التطبيق لإذن قراءة الإشعارات (مستحسن) أو قراءة الرسائل (بديل) ليتعرف على المعاملات البنكية ويقرأ المصروفات تلقائياً."
                } else {
                    "يحتاج التطبيق لإذن قراءة الإشعارات ليتعرف على المعاملات البنكية ويسجّل المصروفات تلقائياً. البيانات كلها بتفضل على جهازك فقط."
                },
                color = Color(0xFFFFE0B2),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenAppSettings) {
                    Text("الإعدادات", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (!notifGranted) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) {
                        Text("إذن الإشعارات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (BuildConfig.ENABLE_SMS_IMPORT && !smsGranted) {
                    Button(
                        onClick = onRequestSmsPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                    ) {
                        Text("إذن الرسائل", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveDialog(
    archivedYearTotals: List<Pair<String, Long>>,
    onUnarchive: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        title = { Text("الأرشيف") },
        text = {
            if (archivedYearTotals.isEmpty()) {
                Text("مفيش سنوات مؤرشفة دلوقتي", color = Color.Gray, fontSize = 13.sp)
            } else {
                Column {
                    Text(
                        "السنوات دي متخفية من الشاشة الرئيسية بس، مصروفاتها لسه محفوظة بالكامل. اضغط \"إظهار\" لترجعها.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    archivedYearTotals.forEach { (year, total) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("سنة $year", color = Color.White, fontWeight = FontWeight.Medium)
                                Text(
                                    formatMinor(total),
                                    color = Color(0xFF80CBC4),
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { onUnarchive(year) }) {
                                Text("إظهار", color = Color(0xFF4DB6AC))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تمام", color = Color.White) }
        }
    )
}

private enum class ImportRangeOption { LAST_3_MONTHS, LAST_6_MONTHS, CURRENT_YEAR, SPECIFIC_YEAR, ALL_TIME }

@Composable
private fun ImportRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long?, endMillis: Long?) -> Unit
) {
    var selectedOption by remember { mutableStateOf(ImportRangeOption.LAST_3_MONTHS) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val availableYears = remember { (currentYear downTo currentYear - 6).toList() }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var yearMenuExpanded by remember { mutableStateOf(false) }

    fun rangeFor(option: ImportRangeOption): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        return when (option) {
            ImportRangeOption.LAST_3_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis to null
            }
            ImportRangeOption.LAST_6_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis to null
            }
            ImportRangeOption.CURRENT_YEAR -> {
                cal.set(currentYear, Calendar.JANUARY, 1, 0, 0, 0)
                cal.timeInMillis to null
            }
            ImportRangeOption.SPECIFIC_YEAR -> {
                val start = Calendar.getInstance().apply {
                    set(selectedYear, Calendar.JANUARY, 1, 0, 0, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    set(selectedYear, Calendar.DECEMBER, 31, 23, 59, 59)
                }.timeInMillis
                start to end
            }
            ImportRangeOption.ALL_TIME -> null to null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        title = { Text("استيراد الرسائل من") },
        text = {
            Column {
                Text(
                    "حدد المدى الزمني عشان الاستيراد يبقى أسرع ومش يجيب مصروفات من سنين مش محتاجها.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val options = listOf(
                    ImportRangeOption.LAST_3_MONTHS to "آخر 3 شهور",
                    ImportRangeOption.LAST_6_MONTHS to "آخر 6 شهور",
                    ImportRangeOption.CURRENT_YEAR to "السنة الحالية ($currentYear)",
                    ImportRangeOption.SPECIFIC_YEAR to "سنة محددة",
                    ImportRangeOption.ALL_TIME to "كل الرسائل (من غير حد)"
                )

                options.forEach { (option, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00897B))
                        )
                        Text(label, color = Color.White, fontSize = 14.sp)
                    }
                }

                if (selectedOption == ImportRangeOption.SPECIFIC_YEAR) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.padding(start = 40.dp)) {
                        OutlinedButton(onClick = { yearMenuExpanded = true }) {
                            Text("$selectedYear", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = yearMenuExpanded,
                            onDismissRequest = { yearMenuExpanded = false }
                        ) {
                            availableYears.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text("$year") },
                                    onClick = {
                                        selectedYear = year
                                        yearMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (start, end) = rangeFor(selectedOption)
                onConfirm(start, end)
            }) {
                Text("استيراد", color = Color(0xFF4DB6AC), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.Gray) }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderCard(
    title: String,
    amount: Long,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    containerColor: Color,
    titleColor: Color,
    amountColor: Color,
    paddingStart: androidx.compose.ui.unit.Dp = 0.dp
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00897B),
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = amountColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                )
            }
            Text(
                text = formatMinor(amount),
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
    val bankTotal = remember(expenses) { expenses.sumOf { it.amountMinor } }

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
                    text = formatMinor(bankTotal),
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
                        text = "-${formatMinor(expense.amountMinor)}",
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}