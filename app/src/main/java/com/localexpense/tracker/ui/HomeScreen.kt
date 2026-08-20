package com.localexpense.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.BuildConfig
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.viewmodel.CleanupState
import com.localexpense.tracker.viewmodel.ImportState
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * الشاشة الرئيسية: بطاقة ملخص الشهر فوق، اختصارات، وبعدين سجل الحركات
 * كشجرة سنة ← شهر ← بنك.
 *
 * الشجرة دي كانت بتتبني بتحميل كل الحركات وتجميعها في الـ Compose. دلوقتي
 * الإجماليات كلها جاية من تجميعات SQL (صفوف قليلة)، وحركات أي مجموعة
 * بتتحمّل بس لما المستخدم يفتحها — يعني 100 ألف حركة مش بتدخل الذاكرة.
 */
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
    onOpenRecurring: () -> Unit,
    onOpenTransactions: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenInstallments: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {}
) {
    val monthTotals by viewModel.monthTotals.collectAsStateWithLifecycle()
    val monthBankTotals by viewModel.monthBankTotals.collectAsStateWithLifecycle()
    val currentMonthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val cleanupState by viewModel.cleanupState.collectAsStateWithLifecycle()
    val anomalyWarning by viewModel.anomalyWarning.collectAsStateWithLifecycle()

    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showCleanupConfirmDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showImportRangeDialog by remember { mutableStateOf(false) }
    var showRestrictedHelp by remember { mutableStateOf(false) }

    val expandedYears = remember { mutableStateMapOf<String, Boolean>() }
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }
    val expandedBanks = remember { mutableStateMapOf<String, Boolean>() }
    val selectedYears = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedYears.isNotEmpty()
    val isImporting = importState is ImportState.Running

    val monthLabelFormat = remember { SimpleDateFormat("MMMM", Locale("ar")) }
    val monthKeyParser = remember { SimpleDateFormat("yyyy-MM", Locale.US) }

    fun monthName(period: String): String =
        runCatching { monthLabelFormat.format(monthKeyParser.parse(period)!!) }.getOrDefault(period)

    // الإجماليات كلها من SQL؛ اللي بيحصل هنا مجرد تجميع صفوف قليلة في شجرة.
    val years = remember(monthTotals, archivedYears) {
        monthTotals
            .groupBy { it.period.take(4) }
            .filterKeys { it !in archivedYears }
            .toSortedMap(compareByDescending { it })
    }
    val archivedYearTotals = remember(monthTotals, archivedYears) {
        monthTotals
            .groupBy { it.period.take(4) }
            .filterKeys { it in archivedYears }
            .map { (year, months) -> year to months.sumOf { it.total } }
            .sortedByDescending { it.first }
    }

    val importStatusMessage = when (val state = importState) {
        is ImportState.Running -> "جاري استيراد وقراءة الرسائل..."
        is ImportState.Done -> "تم فحص ${state.scanned} رسالة واستيراد ${state.imported} حركة جديدة"
        is ImportState.Error -> state.message
        else -> null
    }
    val cleanupStatusMessage = when (val state = cleanupState) {
        is CleanupState.Running -> "جاري البحث عن الحركات المكررة..."
        is CleanupState.Done ->
            if (state.removed > 0) "تم حذف ${state.removed} حركة مكررة" else "مفيش حركات مكررة"
        else -> null
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedYears.size} محدد") },
                    navigationIcon = {
                        IconButton(onClick = { selectedYears.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.archiveYears(selectedYears.toSet())
                            selectedYears.clear()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "أرشفة")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("مصروفاتي", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onOpenTransactions) {
                            Icon(Icons.Default.Search, contentDescription = "بحث وفلاتر")
                        }
                        IconButton(onClick = onOpenAssistant) {
                            Icon(Icons.Default.Chat, contentDescription = "اسأل عن مصروفاتك")
                        }
                        IconButton(onClick = { showArchiveDialog = true }) {
                            Icon(Icons.Default.Unarchive, contentDescription = "الأرشيف")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Tune, contentDescription = "الإعدادات")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onOpenAddExpense,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("حركة جديدة") }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== بطاقة ملخص الشهر =====
            item {
                MonthSummaryCard(
                    spentMinor = currentMonthTotal,
                    onOpenDashboard = onOpenDashboard,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

    // إجمالي الشهر جاي من تجميع SQL (بيستثني التحويلات وبيخصم الاسترداد)،
    // مش من جمع القائمة المعروضة في الذاكرة.
    val currentMonthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()

            // ===== تحذير حركة شاذة =====
            anomalyWarning?.let { warning ->
                item {
                    NoticeCard(
                        text = warning,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        actionLabel = "تمام",
                        onAction = { viewModel.dismissAnomalyWarning() }
                    )
                }
            }

            // ===== استيراد الرسائل (نسخة direct فقط) =====
            if (BuildConfig.ENABLE_SMS_IMPORT) {
                item {
                    Button(
                        onClick = {
                            if (smsPermissionGranted) showImportRangeDialog = true else showDisclosureDialog = true
                        },
                        enabled = !isImporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("جاري الاستيراد...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("مزامنة واستيراد الرسائل")
                        }
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
            title = { Text("تنظيف الحركات المكررة") },
            text = {
                Text(
                    "هيتم البحث عن حركات بنفس المبلغ والبنك حصلت خلال فروق زمنية قصيرة " +
                        "متتالية (حتى لو النص أو اسم الجهة مختلف شوية)، وحذف النسخ الزيادة " +
                        "والاحتفاظ بأقدم واحدة في كل مجموعة. الإجراء ده مينفعش يتراجع فيه."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCleanupConfirmDialog = false
                    viewModel.cleanupDuplicateExpenses()
                }) { Text("تنظيف الآن") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirmDialog = false }) { Text("إلغاء") }
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

    if (showRestrictedHelp) {
        RestrictedSettingsDialog(
            onOpenAppInfo = onOpenAppSettings,
            onDismiss = { showRestrictedHelp = false }
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
                        IconButton(onClick = onOpenTransactions) {
                            Icon(Icons.Default.Search, contentDescription = "بحث وفلاتر", tint = Color.White)
                        }
                        IconButton(onClick = onOpenCalendar) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "التقويم", tint = Color.White)
                        }
                        IconButton(onClick = onOpenRecurring) {
                            Icon(Icons.Default.DateRange, contentDescription = "الدوريات والاشتراكات", tint = Color.White)
                        }
                        IconButton(onClick = onOpenInstallments) {
                            Icon(Icons.Default.CreditCard, contentDescription = "الأقساط", tint = Color.White)
                        }
                        IconButton(onClick = onOpenAddExpense) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة مصروف", tint = Color.White)
                        }
                        IconButton(onClick = onOpenDashboard) {
                            Icon(Icons.Default.BarChart, contentDescription = "الإحصائيات", tint = Color.White)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Tune, contentDescription = "الإعدادات والخصوصية", tint = Color.White)
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
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NoticeCard(
    text: String,
    container: Color,
    content: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            }
        }
    }
}

            // تحذير حركة شاذة (المرحلة 11): بيظهر بعد تسجيل عملية أعلى بكتير
            // من متوسط فئتها، وبيتقفل بضغطة عشان ميقعدش في وش المستخدم.
            anomalyWarning?.let { warning ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = warning,
                            color = Color(0xFFFFCC80),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissAnomalyWarning() }) {
                            Text("تمام", color = Color(0xFFFFB74D), fontSize = 12.sp)
                        }
                    }
                }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (level * 12).dp, end = 16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else container
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                } else {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = textStyle, maxLines = 1)
            }
            Text(
                formatMinor(amountMinor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * حركات مجموعة واحدة (شهر + بنك). الاستعلام بيشتغل بس لما المجموعة تتفتح،
 * وبيتوقف تلقائيًا لما تتقفل (الـ Flow بينتهي مع خروج الـ composable).
 */
@Composable
private fun BankTransactions(
    viewModel: MainViewModel,
    month: String,
    bankName: String,
    onOpenTransaction: (Long) -> Unit
) {
    val flow = remember(month, bankName) { viewModel.observeGroupTransactions(month, bankName) }
    val transactions by flow.collectAsStateWithLifecycle(emptyList())

    val timeFormatter = remember { SimpleDateFormat("dd MMMM - hh:mm a", Locale("ar")) }

    Column(Modifier.padding(start = 52.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
        // مفيش رسالة "جاري التحميل": المجموعة دي إجماليها معروف أصلاً من
        // التجميع، فالصفوف بتظهر في إطار واحد والرسالة كانت هتوهم إن فيه
        // مشكلة في المجموعات الفاضية.
        transactions.forEach { expense ->
            TransactionRow(
                expense = expense,
                timeLabel = timeFormatter.format(Date(expense.timestamp)),
                onClick = { onOpenTransaction(expense.id) }
            )
        }
    }
}

@Composable
private fun TransactionRow(expense: Expense, timeLabel: String, onClick: () -> Unit) {
    val isCredit = expense.type == TransactionType.INCOME || expense.type == TransactionType.REFUND
    val amountColor = when (expense.type) {
        TransactionType.INCOME, TransactionType.REFUND -> MaterialTheme.finance.income
        TransactionType.TRANSFER -> MaterialTheme.finance.transfer
        TransactionType.EXPENSE -> MaterialTheme.finance.expense
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.merchant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        expense.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                timeFormatter = timeFormatter,
                                                onOpenTransaction = onOpenTransaction
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
        Text(
            (if (isCredit) "+" else if (expense.type == TransactionType.TRANSFER) "" else "-") +
                formatMinor(expense.amountMinor),
            style = MaterialTheme.typography.labelLarge,
            color = amountColor
        )
    }
}

@Composable
private fun PermissionsCard(
    smsGranted: Boolean,
    notifGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRestrictedHelp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("أذونات القراءة مطلوبة", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (BuildConfig.ENABLE_SMS_IMPORT) {
                    "التطبيق محتاج إذن قراءة الإشعارات (مستحسن) أو قراءة الرسائل (بديل) عشان يتعرف على العمليات البنكية ويسجّلها تلقائيًا."
                } else {
                    "التطبيق محتاج إذن قراءة الإشعارات عشان يتعرف على العمليات البنكية ويسجّلها تلقائيًا. البيانات كلها بتفضل على جهازك."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenRestrictedHelp) { Text("الإذن محجوب؟") }
                TextButton(onClick = onOpenAppSettings) { Text("إعدادات النظام") }
                if (!notifGranted) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRequestNotificationPermission) { Text("إذن الإشعارات") }
                }
                if (BuildConfig.ENABLE_SMS_IMPORT && !smsGranted) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRequestSmsPermission) { Text("إذن الرسائل") }
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
        title = { Text("الأرشيف") },
        text = {
            if (archivedYearTotals.isEmpty()) {
                Text("مفيش سنوات مؤرشفة دلوقتي", style = MaterialTheme.typography.bodySmall)
            } else {
                Column {
                    Text(
                        "السنوات دي متخفية من الشاشة الرئيسية بس، حركاتها لسه محفوظة بالكامل.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    archivedYearTotals.forEach { (year, total) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("سنة $year", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatMinor(total),
                                    color = Color(0xFF80CBC4),
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { onUnarchive(year) }) { Text("إظهار") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } }
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
        title = { Text("استيراد الرسائل من") },
        text = {
            Column {
                Text(
                    "حدد المدى الزمني عشان الاستيراد يبقى أسرع ومش يجيب حركات من سنين مش محتاجها.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))

                val options = listOf(
                    ImportRangeOption.LAST_3_MONTHS to "آخر 3 شهور",
                    ImportRangeOption.LAST_6_MONTHS to "آخر 6 شهور",
                    ImportRangeOption.CURRENT_YEAR to "السنة الحالية ($currentYear)",
                    ImportRangeOption.SPECIFIC_YEAR to "سنة محددة",
                    ImportRangeOption.ALL_TIME to "كل الرسائل (من غير حد)"
                )

                options.forEach { (option, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == option, onClick = { selectedOption = option })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                AnimatedVisibility(visible = selectedOption == ImportRangeOption.SPECIFIC_YEAR) {
                    Box(Modifier.padding(start = 40.dp)) {
                        OutlinedButton(onClick = { yearMenuExpanded = true }) { Text("$selectedYear") }
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
            }) { Text("استيراد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
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
    timeFormatter: SimpleDateFormat,
    onOpenTransaction: (Long) -> Unit = {}
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
                        .clickable { onOpenTransaction(expense.id) }
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

                    val isCredit = expense.type == com.localexpense.tracker.data.TransactionType.INCOME ||
                        expense.type == com.localexpense.tracker.data.TransactionType.REFUND
                    Text(
                        text = (if (isCredit) "+" else "-") + formatMinor(expense.amountMinor),
                        color = if (isCredit) Color(0xFF81C784) else Color(0xFFFF8A80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
