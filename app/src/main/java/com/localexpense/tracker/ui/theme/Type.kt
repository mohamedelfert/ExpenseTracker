package com.localexpense.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import com.localexpense.tracker.R

/**
 * سلّم خطوط واحد للتطبيق. تم استخدام خط Cairo ليعطي جمالية فائقة للحروف العربية.
 */
private val CairoFontFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold)
)

private val Default = CairoFontFamily

/**
 * "tnum" (tabular figures) بتخلي كل رقم من 0-9 ياخد نفس العرض بالظبط، فالمبالغ
 * في الداشبورد والليستات والتقارير متتحركش يمين شمال وقت ما القيمة بتتحدّث أو
 * بتترتب فوق بعض في عمود. الخاصية دي بتأثر بس على الأرقام (الغربية اللي
 * formatMinor بيستخدمها)، مش على النص العربي - فمفيش أي تأثير جانبي على شكل
 * الحروف. لو الخط مدعّمهاش أصلاً بتتجاهل من غير أي ضرر.
 */
private const val TabularFigures = "tnum"

private fun tabularStyle(
    fontWeight: FontWeight,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp
) = TextStyle(
    fontFamily = Default,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = TabularFigures
)

val AppTypography = Typography(
    displayMedium = tabularStyle(FontWeight.Bold, 34.sp, 40.sp, (-0.5).sp),
    headlineSmall = tabularStyle(FontWeight.Bold, 24.sp, 30.sp, (-0.2).sp),
    titleLarge = tabularStyle(FontWeight.SemiBold, 20.sp, 26.sp),
    titleMedium = tabularStyle(FontWeight.SemiBold, 17.sp, 23.sp),
    titleSmall = tabularStyle(FontWeight.SemiBold, 15.sp, 20.sp),
    bodyLarge = tabularStyle(FontWeight.Normal, 16.sp, 23.sp),
    bodyMedium = tabularStyle(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = tabularStyle(FontWeight.Normal, 12.5.sp, 18.sp),
    labelLarge = tabularStyle(FontWeight.SemiBold, 14.sp, 18.sp, 0.1.sp),
    labelMedium = tabularStyle(FontWeight.Medium, 12.5.sp, 16.sp, 0.2.sp),
    labelSmall = tabularStyle(FontWeight.Medium, 11.sp, 15.sp, 0.3.sp)
)
