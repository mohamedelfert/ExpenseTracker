package com.localexpense.tracker.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.domain.Insight
import com.localexpense.tracker.domain.InsightLevel
import com.localexpense.tracker.domain.UpcomingKind
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.ui.theme.finance
import com.localexpense.tracker.ui.shimmerEffect
import com.localexpense.tracker.ui.getCategoryIcon
import com.localexpense.tracker.viewmodel.FinanceViewModel
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الداشبورد (المرحلة 8): أهم الأرقام فوق، وباقي التفاصيل تحتها بالترتيب.
 * كل الأرقام جاية من FinancialContext الواحد — مفيش أي حساب في الـ Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    finance: FinanceViewModel,
    onBack: () -> Unit,
    onOpenTransactions: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenCompare: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {}
) {
    val context by finance.financialContext.collectAsStateWithLifecycle()
    val insights by finance.insights.collectAsStateWithLifecycle()
    val daily by finance.dailyTotals.collectAsStateWithLifecycle()
    val recent by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val monthOffset by finance.monthOffset.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("dd MMM", Locale("ar"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نظرة عامة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenReports) { Text("تقارير", style = MaterialTheme.typography.labelMedium) }
                    IconButton(onClick = onOpenAssistant) { Text("اسأل", style = MaterialTheme.typography.labelMedium) }
                }
            )
        }
    ) { padding ->
        val ctx = context
        if (ctx == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Box(Modifier.fillMaxWidth().height(60.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(100.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                    Box(Modifier.weight(1f).height(100.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                    Box(Modifier.weight(1f).height(100.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                }
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {

            // ===== الشهر + التنقل بين الشهور =====
            item {
                SoftCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finance.showMonth(monthOffset - 1) }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "الشهر السابق")
                        }
                        Text(ctx.monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { finance.showMonth(monthOffset + 1) },
                            enabled = monthOffset < 0
                        ) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "الشهر التالي")
                        }
                    }
                }
            }

            // ===== الأرقام الأساسية =====
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "الدخل",
                        amountMinor = ctx.summary.incomeMinor,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.finance.income,
                        icon = Icons.Default.ArrowDownward
                    )
                    StatCard(
                        label = "المصروف",
                        amountMinor = ctx.summary.netSpentMinor,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.finance.expense,
                        icon = Icons.Default.ArrowUpward,
                        hint = if (ctx.summary.refundMinor > 0) "بعد خصم استرداد ${formatMinor(ctx.summary.refundMinor)}" else null
                    )
                    StatCard(
                        label = if (ctx.summary.incomeMinor > 0) "المتبقي" else "الصافي",
                        amountMinor = if (ctx.summary.incomeMinor > 0) ctx.summary.remainingMinor else ctx.summary.netCashFlowMinor,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccountBalanceWallet,
                        showSign = true
                    )
                }
            }

            // ===== المقارنة + المتوسط + التوقّع =====
            item {
                val change = ctx.comparison.totalChangePercent
                val comparisonText = when {
                    !ctx.comparison.hasPrevious -> "مفيش بيانات للشهر اللي فات"
                    change == null -> "مفيش أساس للمقارنة"
                    change >= 0 -> "↑ ${change.toInt()}% عن الشهر اللي فات"
                    else -> "↓ ${-change.toInt()}% عن الشهر اللي فات"
                }
                Column(Modifier.padding(16.dp)) {
                    Text(comparisonText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "متوسط يومي ${formatMinor(ctx.forecast.dailyAverageMinor)} • " +
                            "متوقّع بنهاية الشهر ${formatMinor(ctx.forecast.projectedMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ctx.forecast.projectedOverBudgetMinor?.takeIf { it > 0 }?.let {
                        Text(
                            "⚠️ ممكن تتخطى ميزانية الشهر بحوالي ${formatMinor(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.finance.expense
                        )
                    }
                }
            }

            // ===== الرسم البياني اليومي =====
            if (daily.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader("الصرف اليومي")
                        DailyBarChart(
                            values = daily.map { it.total },
                            modifier = Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // ===== الرؤى (المرحلة 11) =====
            if (insights.isNotEmpty()) {
                item { SectionHeader("رؤى مالية", action = "الكل", onAction = onOpenInsights) }
                items(insights.take(3).size) { index ->
                    InsightCard(insights[index]) { finance.dismissInsight(insights[index]) }
                }
            }

            // ===== الميزانيات =====
            item { SectionHeader("الميزانيات", action = "إدارة", onAction = onOpenBudgets) }
            if (ctx.overallBudgetMinor > 0) {
                item { BudgetBar(ctx.overallBudget, "الميزانية الكلية") }
            }
            items(ctx.categoryBudgetProgress.take(4).size) { index ->
                val (name, progress) = ctx.categoryBudgetProgress[index]
                BudgetBar(progress, name)
            }
            if (ctx.overallBudgetMinor == 0L && ctx.categoryBudgetProgress.isEmpty()) {
                item { EmptyState("مفيش ميزانيات محددة", "حدد ميزانية شهرية عشان نقدر نحذّرك قبل ما تتخطاها.") }
            }

            // ===== الفئات =====
            item { SectionHeader("حسب الفئة") }
            if (ctx.categoryTotals.isEmpty()) {
                item { EmptyState("لسه مفيش مصروفات الشهر ده", "الحركات بتظهر تلقائيًا لما نلقطها من رسائل البنك.") }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = ctx.categoryTotals.toList().sortedByDescending { it.second },
                            modifier = Modifier.size(150.dp)
                        )
                        Text(
                            formatMinor(ctx.summary.netSpentMinor, withDecimals = false),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val categories = ctx.categoryTotals.toList().sortedByDescending { it.second }
                items(categories.size) { index ->
                    val (name, total) = categories[index]
                    val palette = chartPalette()
                    AmountRow(
                        label = name,
                        amountMinor = total,
                        leading = { IconBadge(icon = getCategoryIcon(name), tint = palette[index % palette.size], size = 32.dp) },
                        trailingText = "${(total * 100 / ctx.summary.netSpentMinor.coerceAtLeast(1)).toInt()}%"
                    )
                }
            }

            // ===== أعلى الجهات =====
            if (ctx.topMerchants.isNotEmpty()) {
                item { SectionHeader("أعلى الجهات") }
                items(ctx.topMerchants.size) { index ->
                    val (merchant, total) = ctx.topMerchants[index]
                    AmountRow(label = merchant, amountMinor = total)
                }
            }

            // ===== الدفعات القادمة =====
            if (ctx.upcoming.isNotEmpty()) {
                item { SectionHeader("دفعات قادمة") }
                items(ctx.upcoming.take(5).size) { index ->
                    val payment = ctx.upcoming[index]
                    val kindLabel = when (payment.kind) {
                        UpcomingKind.SUBSCRIPTION -> "اشتراك"
                        UpcomingKind.RECURRING -> "دورية"
                        UpcomingKind.INSTALLMENT -> "قسط"
                    }
                    AmountRow(
                        label = payment.name,
                        amountMinor = payment.amountMinor,
                        trailingText = "$kindLabel • ${dateFormat.format(Date(payment.dueDate))}"
                    )
                }
            }

            // ===== أحدث الحركات =====
            item { SectionHeader("أحدث الحركات", action = "الكل", onAction = onOpenTransactions) }
            items(recent.size) { index ->
                val expense = recent[index]
                AmountRow(
                    label = expense.merchant,
                    amountMinor = expense.amountMinor,
                    trailingText = "${typeLabel(expense.type)} • ${dateFormat.format(Date(expense.timestamp))}",
                    onClick = { onOpenTransaction(expense.id) }
                )
            }

            item { SectionHeader("المقارنة الشهرية", action = "فتح", onAction = onOpenCompare) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, onDismiss: () -> Unit) {
    val accent = when (insight.level) {
        InsightLevel.INFO -> MaterialTheme.colorScheme.primary
        InsightLevel.WARNING -> MaterialTheme.finance.warning
        InsightLevel.ALERT -> MaterialTheme.finance.expense
    }
    val icon = when (insight.level) {
        InsightLevel.INFO -> Icons.Default.Lightbulb
        InsightLevel.WARNING -> Icons.Default.WarningAmber
        InsightLevel.ALERT -> Icons.Default.PriorityHigh
    }
    SoftCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = icon, tint = accent, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(insight.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "إخفاء", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
