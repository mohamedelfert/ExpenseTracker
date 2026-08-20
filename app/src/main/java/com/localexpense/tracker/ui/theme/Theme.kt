package com.localexpense.tracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color_White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    secondary = Ocean600,
    onSecondary = Color_White,
    secondaryContainer = Ocean100,
    onSecondaryContainer = Ocean900,
    tertiary = Amber600,
    onTertiary = Color_White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Color(0xFF2E1A00),
    error = Coral600,
    onError = Color_White,
    errorContainer = Coral100,
    onErrorContainer = Coral900,
    background = Sand50,
    onBackground = Ink900,
    surface = Color_White,
    onSurface = Ink900,
    surfaceVariant = Sand100,
    onSurfaceVariant = Ink700,
    surfaceContainer = Sand100,
    surfaceContainerHigh = Sand200,
    outline = Color(0xFFB6B3A9),
    outlineVariant = Sand200,
    inverseSurface = Ink900,
    inverseOnSurface = Sand50
)

private val DarkColors = darkColorScheme(
    primary = Emerald300,
    onPrimary = Emerald900,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald100,
    secondary = Ocean300,
    onSecondary = Ocean900,
    secondaryContainer = Color(0xFF16405C),
    onSecondaryContainer = Ocean100,
    tertiary = Amber300,
    onTertiary = Color(0xFF2E1A00),
    tertiaryContainer = Color(0xFF6B3E00),
    onTertiaryContainer = Amber100,
    error = Coral400,
    onError = Coral900,
    errorContainer = Color(0xFF7A2119),
    onErrorContainer = Coral100,
    background = Slate900,
    onBackground = Slate200,
    surface = Slate800,
    onSurface = Slate200,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    surfaceContainer = Slate700,
    surfaceContainerHigh = Slate600,
    outline = Color(0xFF4A5155),
    outlineVariant = Slate600,
    inverseSurface = Slate200,
    inverseOnSurface = Slate900
)

/**
 * ألوان المعنى المالي (دخل/مصروف/تحويل/تحذير) + لوحة الرسوم. مش جزء من
 * ColorScheme بتاع Material، فبتتمرّر كـ CompositionLocal عشان أي شاشة تاخدها
 * من مكان واحد بدل ما تكتب Color(0xFF...) بإيدها.
 */
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val chart: List<Color>,
    val heroGradient: List<Color>
)

private val LightFinanceColors = FinanceColors(
    income = IncomeLight,
    expense = ExpenseLight,
    transfer = TransferLight,
    warning = Amber600,
    chart = ChartLight,
    heroGradient = HeroGradientLight
)

private val DarkFinanceColors = FinanceColors(
    income = IncomeDark,
    expense = ExpenseDark,
    transfer = TransferDark,
    warning = Amber300,
    chart = ChartDark,
    heroGradient = HeroGradientDark
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

/** اختصار للوصول: `MaterialTheme.finance.income`. */
val MaterialTheme.finance: FinanceColors
    @Composable get() = LocalFinanceColors.current

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // مفيش dynamicColor: التطبيق مالي وألوان الدخل/المصروف لازم تفضل ثابتة
    // ومفهومة، مش تتغير مع خلفية الموبايل.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // أيقونات شريط الحالة تتقلب مع الثيم عشان تفضل مقروءة.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
