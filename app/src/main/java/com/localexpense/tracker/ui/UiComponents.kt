package com.localexpense.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localexpense.tracker.domain.BudgetProgress
import com.localexpense.tracker.domain.BudgetState
import com.localexpense.tracker.ui.theme.finance
import com.localexpense.tracker.money.formatMinor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

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
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Card(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .border(width = 0.6.dp, color = borderColor, shape = shape)
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
            AnimatedCounter(
                countMinor = amountMinor,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
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
fun DailyBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    selectedIndex: Int?,
    onBarSelected: (Int?) -> Unit
) {
    if (values.isEmpty()) return
    val color = barColor ?: chartPalette().first()
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    
    Canvas(modifier = modifier.pointerInput(values, selectedIndex) {
        detectTapGestures { offset ->
            val gap = 2f
            val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
            val index = (offset.x / (barWidth + gap)).toInt()
            if (index in values.indices) {
                onBarSelected(if (selectedIndex == index) null else index)
            }
        }
    }) {
        val gap = 2f
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
        
        values.forEachIndexed { index, value ->
            val height = (value.toFloat() / max) * size.height
            val x = index * (barWidth + gap)
            val y = size.height - height
            
            // رسم العمود
            val barAlpha = if (selectedIndex == null || selectedIndex == index) 1f else 0.4f
            drawRect(
                color = color.copy(alpha = barAlpha),
                topLeft = androidx.compose.ui.geometry.Offset(x = x, y = y),
                size = androidx.compose.ui.geometry.Size(barWidth, height)
            )
            
            points.add(androidx.compose.ui.geometry.Offset(x + barWidth / 2, y))
        }
        
        // رسم خط الاتجاه (Trend line)
        if (points.size > 1) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            )
        }
    }
}

@Composable
fun DonutChart(
    slices: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    selectedIndex: Int?,
    onSliceSelected: (Int?) -> Unit
) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.second }.coerceAtLeast(1L)
    val palette = chartPalette()
    
    Canvas(modifier = modifier.pointerInput(slices, selectedIndex) {
        detectTapGestures { offset ->
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val angle = (Math.toDegrees(Math.atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())) + 360) % 360
            // Adjust angle to match -90 start
            val adjustedAngle = (angle + 90) % 360
            
            var currentAngle = 0f
            for (i in slices.indices) {
                val sweep = (slices[i].second.toDouble() / total * 360.0).toFloat()
                if (adjustedAngle >= currentAngle && adjustedAngle < currentAngle + sweep) {
                    onSliceSelected(if (selectedIndex == i) null else i)
                    break
                }
                currentAngle += sweep
            }
        }
    }) {
        var startAngle = -90f
        slices.forEachIndexed { index, (_, value) ->
            val sweep = (value.toDouble() / total * 360.0).toFloat()
            val sliceAlpha = if (selectedIndex == null || selectedIndex == index) 1f else 0.4f
            val strokeWidth = if (selectedIndex == index) 50f else 40f
            
            drawArc(
                color = palette[index % palette.size].copy(alpha = sliceAlpha),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
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
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
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

@Composable
fun AnimatedCounter(
    countMinor: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = Color.White
) {
    AnimatedContent(
        targetState = countMinor,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            } else {
                slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
            }.using(SizeTransform(clip = false))
        },
        label = "CounterAnimation"
    ) { targetCount ->
        Text(
            text = formatMinor(targetCount),
            style = style,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "shimmerOffsetX"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                surfaceColor,
                shimmerColor,
                surfaceColor
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned { size = it.size }
}

/**
 * اختصار يجمع الضغط والاهتزاز في Modifier واحد — ضع `hapticClick { }` بدل
 * `.clickable { }` في أي زر أو عنصر تريد له اهتزازاً خفيفاً عند الضغط.
 */
fun Modifier.hapticClick(onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material.ripple.rememberRipple()
    ) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}
