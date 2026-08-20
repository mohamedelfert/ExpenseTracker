package com.localexpense.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localexpense.tracker.domain.BudgetProgress
import com.localexpense.tracker.domain.BudgetState
import com.localexpense.tracker.ui.theme.finance
import com.localexpense.tracker.money.formatMinor

/**
 * مكوّنات واجهة مشتركة بين شاشات التحليلات — عشان الداشبورد والميزانيات
 * والتقارير يبقوا بنفس الشكل من غير تكرار نفس الكود في كل شاشة.
 */

/**
 * لوحة الرسوم من الثيم (بتتقلب مع النهاري/الليلي).
 *
 * دالة مش property بـ @Composable getter: خاصية عامة بـ getter مركّب مش
 * مدعومة بشكل موثوق في كل نسخ الـ Compose compiler plugin.
 */
@Composable
fun chartPalette(): List<Color> = MaterialTheme.finance.chart

@Composable
fun budgetColor(state: BudgetState): Color = when (state) {
    BudgetState.SAFE -> MaterialTheme.finance.income
    BudgetState.WARNING -> MaterialTheme.finance.warning
    BudgetState.EXCEEDED -> MaterialTheme.finance.expense
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

/** بطاقة رقم واحد: عنوان صغير + مبلغ كبير + سطر مساعد اختياري. */
@Composable
fun StatCard(
    label: String,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    showSign: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            val prefix = if (showSign && amountMinor > 0) "+" else ""
            Text(
                "$prefix${formatMinor(amountMinor)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BudgetBar(progress: BudgetProgress, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatMinor(progress.spentMinor)} / ${formatMinor(progress.limitMinor, withDecimals = false)}",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.ratio.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = budgetColor(progress.state),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "${progress.percentUsed}% — متبقي ${formatMinor(progress.remainingMinor)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * رسم أعمدة بسيط للصرف اليومي. Canvas بدل مكتبة رسوم بيانية: أعمدة رأسية
 * ومقياس واحد، مش محتاجة 300 كيلوبايت مكتبة.
 */
@Composable
fun DailyBarChart(values: List<Long>, modifier: Modifier = Modifier, barColor: Color? = null) {
    if (values.isEmpty()) return
    val color = barColor ?: chartPalette().first()
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val gap = 2f
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { index, value ->
            val height = (value.toFloat() / max) * size.height
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * (barWidth + gap),
                    y = size.height - height
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, height)
            )
        }
    }
}

@Composable
fun DonutChart(slices: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1L)
    val palette = chartPalette()
    Canvas(modifier = modifier) {
        var startAngle = -90f
        slices.forEachIndexed { index, (_, value) ->
            val sweep = (value.toDouble() / total * 360.0).toFloat()
            drawArc(
                color = palette[index % palette.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 40f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
            )
            startAngle += sweep
        }
    }
}

@Composable
fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
    )
}

/** حالة فاضية موحّدة (شرط الـ UX في الـ spec: حالات فاضية واضحة). */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** سطر "اسم ... مبلغ" مستخدم في كل قوائم الأرقام. */
@Composable
fun AmountRow(
    label: String,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMinor(amountMinor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (trailingText != null) {
                Text(trailingText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
