package com.localexpense.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

/**
 * بطاقة أساسية بظل خفيف وناعم بدل الاعتماد على tonal elevation بس — ده اللي
 * بيدي إحساس "عائم فوق الخلفية" الحديث بدل السطح المسطّح. مستخدمة كأساس
 * لأي بطاقة تانية في التطبيق عشان الشكل يفضل موحّد في كل شاشة.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        Column(content = content)
    }
}

/** دايرة أيقونة ملوّنة (badge) — بتُستخدم قبل الأرقام والعناوين عشان تدّي
 * توجيه بصري سريع (نوع الحركة، الفئة، ...) بدل الاعتماد على النص بس. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    containerAlpha: Float = 0.14f
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = containerAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * البطاقة الرئيسية بتاعة الشاشة الرئيسية والداشبورد — العنصر المميّز للتصميم
 * الجديد: تدرّج بلون الهوية بدل سطح مسطّح، ورقم كبير هو أول حاجة تتشاف.
 */
@Composable
fun HeroBalanceCard(
    title: String,
    amountMinor: Long,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val gradient = MaterialTheme.finance.heroGradient
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = MaterialTheme.shapes.extraLarge, ambientColor = gradient.first().copy(alpha = 0.35f), spotColor = gradient.first().copy(alpha = 0.35f))
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(gradient))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (icon != null) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatMinor(amountMinor),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/** بطاقة رقم واحد: عنوان صغير + مبلغ كبير + سطر مساعد اختياري، مع بادچ
 * أيقونة اختياري لتوجيه بصري أسرع. */
@Composable
fun StatCard(
    label: String,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    showSign: Boolean = false,
    icon: ImageVector? = null
) {
    SoftCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            if (icon != null) {
                IconBadge(icon = icon, tint = accent, size = 30.dp)
                Spacer(Modifier.height(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            val prefix = if (showSign && amountMinor > 0) "+" else ""
            Text(
                "$prefix${formatMinor(amountMinor)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
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
