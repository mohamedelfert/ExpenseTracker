package com.localexpense.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.BuildConfig
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.PeriodBankTotal
import com.localexpense.tracker.data.PeriodTotal
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.ui.theme.finance
import com.localexpense.tracker.viewmodel.CleanupState
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
    onOpenAssistant: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {}
) {
    val monthTotals by viewModel.monthTotals.collectAsStateWithLifecycle()
    val monthBankTotals by viewModel.monthBankTotals.collectAsStateWithLifecycle()
    val currentMonthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val cleanupState by viewModel.cleanupState.collectAsStateWithLifecycle()
    val anomalyWarning by viewModel.anomalyWarning.collectAsStateWithLifecycle()
    val archivedYears by viewModel.archivedYears.collectAsStateWithLifecycle()

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

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            if (BuildConfig.ENABLE_SMS_IMPORT && smsPermissionGranted && !isImporting) {
                val (start, end) = viewModel.settings.smsSyncTimeRange()
                viewModel.importFromInbox(start, end)
            } else if (!smsPermissionGranted && BuildConfig.ENABLE_SMS_IMPORT) {
                showDisclosureDialog = true
                pullToRefreshState.endRefresh()
            } else {
                kotlinx.coroutines.delay(1000)
                pullToRefreshState.endRefresh()
            }
        }
    }

    LaunchedEffect(isImporting) {
        if (!isImporting && pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ===== بطاقة ملخص الشهر (Hero) =====
                item {
                    HeroBalanceCard(
                        title = "مصروفات الشهر الحالي",
                        amountMinor = currentMonthTotal,
                        subtitle = "اضغط لعرض التحليلات: التوقّع، المقارنة بالشهر اللي فات، والرؤى",
                        icon = Icons.Default.BarChart,
                        onClick = onOpenDashboard,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ===== اختصارات =====
                item {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction(Icons.Default.BarChart, "التحليلات", onOpenDashboard)
                        QuickAction(Icons.Default.CalendarMonth, "التقويم", onOpenCalendar)
                        QuickAction(Icons.Default.DateRange, "الدوريات", onOpenRecurring)
                        QuickAction(Icons.Default.CreditCard, "الأقساط", onOpenInstallments)
                        QuickAction(Icons.Default.Tune, "قواعد الرسائل", onOpenTestSms)
                    }
                }

                // ===== أذونات =====
                val needsPermissionsCard = !notificationAccessGranted ||
                        (BuildConfig.ENABLE_SMS_IMPORT && !smsPermissionGranted)
                if (needsPermissionsCard) {
                    item {
                        PermissionsCard(
                            smsGranted = smsPermissionGranted,
                            notifGranted = notificationAccessGranted,
                            onRequestSmsPermission = { showDisclosureDialog = true },
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenRestrictedHelp = { showRestrictedHelp = true }
                        )
                    }
                }

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

                importStatusMessage?.let { message ->
                    item {
                        NoticeCard(
                            text = message,
                            container = if (importState is ImportState.Error) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            content = if (importState is ImportState.Error) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
                cleanupStatusMessage?.let { message ->
                    item {
                        NoticeCard(
                            text = message,
                            container = MaterialTheme.colorScheme.surfaceContainer,
                            content = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ===== سجل الحركات =====
                item {
                    SectionHeader(
                        title = "سجل الحركات",
                        action = "تنظيف المكرر",
                        onAction = { showCleanupConfirmDialog = true }
                    )
                }

                if (years.isEmpty()) {
                    item {
                        EmptyState(
                            title = "لسه مفيش حركات",
                            hint = "الحركات بتظهر هنا تلقائيًا أول ما نلقطها من رسائل أو إشعارات البنك، أو لما تضيف واحدة بنفسك."
                        )
                    }
                } else {
                    years.forEach { (year, monthsOfYear) ->
                        val isYearExpanded = expandedYears[year] ?: true
                        val isSelected = year in selectedYears
                        val yearTotal = monthsOfYear.sumOf { it.total }

                        item(key = "year-$year") {
                            LedgerRow(
                                title = "سنة $year",
                                amountMinor = yearTotal,
                                level = 0,
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
                                    if (!isSelectionMode && year !in selectedYears) selectedYears.add(year)
                                }
                            )
                        }

                        if (isYearExpanded && !isSelectionMode) {
                            monthsOfYear.sortedByDescending { it.period }.forEach { month ->
                                val isMonthExpanded = expandedMonths[month.period] ?: false
                                item(key = "month-${month.period}") {
                                    LedgerRow(
                                        title = monthName(month.period),
                                        amountMinor = month.total,
                                        level = 1,
                                        isExpanded = isMonthExpanded,
                                        onClick = { expandedMonths[month.period] = !isMonthExpanded }
                                    )
                                }

                                if (isMonthExpanded) {
                                    val banks = monthBankTotals.filter { it.period == month.period }
                                    banks.forEach { bank ->
                                        val bankKey = "${bank.period}-${bank.bankName}"
                                        val isBankExpanded = expandedBanks[bankKey] ?: false
                                        item(key = "bank-$bankKey") {
                                            LedgerRow(
                                                title = bank.bankName,
                                                amountMinor = bank.total,
                                                level = 2,
                                                isExpanded = isBankExpanded,
                                                onClick = { expandedBanks[bankKey] = !isBankExpanded }
                                            )
                                        }
                                        if (isBankExpanded) {
                                            item(key = "tx-$bankKey") {
                                                BankTransactions(
                                                    viewModel = viewModel,
                                                    month = bank.period,
                                                    bankName = bank.bankName,
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

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
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

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerRow(
    title: String,
    amountMinor: Long,
    level: Int,
    isExpanded: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val container = when (level) {
        0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        1 -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textStyle = when (level) {
        0 -> MaterialTheme.typography.titleMedium
        1 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyMedium
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (level * 12).dp, end = 16.dp, bottom = 6.dp)
            .let {
                if (level == 0) it.shadow(
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                ) else it
            }
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

@OptIn(ExperimentalMaterial3Api::class)
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
        transactions.forEach { expense ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            onOpenTransaction(expense.id)
                            false
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            viewModel.deleteExpense(expense)
                            true
                        }
                        else -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val color = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }
                    val icon = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Info
                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Close
                        else -> null
                    }
                    val alignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small)
                            .background(color)
                            .padding(horizontal = 16.dp),
                        contentAlignment = alignment
                    ) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            ) {
                TransactionRow(
                    expense = expense,
                    timeLabel = timeFormatter.format(Date(expense.timestamp)),
                    onClick = { onOpenTransaction(expense.id) }
                )
            }
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
                if (!expense.isVerified && expense.source != com.localexpense.tracker.data.TransactionSource.MANUAL) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.finance.warning)
                    )
                }
            }
            Text(
                timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onUnarchive(year) }) {
                                Text("إلغاء الأرشفة")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("ar")) }
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد حركات من فترة معينة") },
        text = {
            Column {
                Text(
                    "اختار الفترة اللي عايز تستورد حركاتها من صندوق الوارد. " +
                        "لو سبت أي تاريخ فاضي، هيتفتح الاستيراد من غير حد لأوله أو آخره.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(startMillis?.let { dateFormat.format(Date(it)) } ?: "من (اختياري)")
                    }
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(endMillis?.let { dateFormat.format(Date(it)) } ?: "إلى (اختياري)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(startMillis, endMillis) }) { Text("استيراد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startMillis = state.selectedDateMillis
                    showStartPicker = false
                }) { Text("تم") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endMillis = state.selectedDateMillis
                    showEndPicker = false
                }) { Text("تم") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}