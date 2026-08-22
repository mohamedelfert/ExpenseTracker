package com.localexpense.tracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs

fun getCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("أكل") || lower.contains("طعام") || lower.contains("مطعم") || lower.contains("سوبر") || lower.contains("بقالة") || lower.contains("كافيه") -> Icons.Default.Fastfood
        lower.contains("مواصلات") || lower.contains("بنزين") || lower.contains("سيارة") || lower.contains("أوبر") -> Icons.Default.DirectionsCar
        lower.contains("فواتير") || lower.contains("كهرباء") || lower.contains("مياه") || lower.contains("انترنت") || lower.contains("غاز") -> Icons.Default.Receipt
        lower.contains("صحة") || lower.contains("علاج") || lower.contains("صيدلية") || lower.contains("طبيب") -> Icons.Default.LocalHospital
        lower.contains("تعليم") || lower.contains("كورس") || lower.contains("مدرسة") || lower.contains("جامعة") -> Icons.Default.School
        lower.contains("تسوق") || lower.contains("ملابس") || lower.contains("شراء") -> Icons.Default.ShoppingBag
        lower.contains("سكن") || lower.contains("إيجار") || lower.contains("بيت") || lower.contains("شقة") -> Icons.Default.Home
        lower.contains("ترفيه") || lower.contains("سينما") || lower.contains("خروج") || lower.contains("لعب") -> Icons.Default.Movie
        lower.contains("رياضة") || lower.contains("جيم") || lower.contains("نادي") -> Icons.Default.FitnessCenter
        lower.contains("اتصالات") || lower.contains("رصيد") || lower.contains("باقة") -> Icons.Default.PhoneIphone
        lower.contains("هدايا") || lower.contains("هدية") -> Icons.Default.CardGiftcard
        lower.contains("سفر") || lower.contains("طيران") -> Icons.Default.FlightTakeoff
        lower.contains("استثمار") || lower.contains("توفير") || lower.contains("جمعية") -> Icons.Default.TrendingUp
        lower.contains("دخل") || lower.contains("راتب") || lower.contains("مرتب") || lower.contains("مكافأة") -> Icons.Default.AttachMoney
        lower.contains("قهوة") -> Icons.Default.LocalCafe
        lower.contains("أطفال") || lower.contains("عائلة") -> Icons.Default.FamilyRestroom
        else -> Icons.Default.Category
    }
}

/**
 * لون ثابت لكل فئة بالاسم — نفس الفئة تاخد نفس اللون في كل شاشة (المعاملات،
 * الميزانيات، ...) بدل ما يبقى لون واحد لكل الفئات أو لون بيتغيّر حسب
 * الترتيب في القايمة. بنستخدم hash الاسم عشان نختار من لوحة الرسوم البيانية
 * الموجودة أصلاً في الثيم (بتفرق نهاري/ليلي زي أي لون تاني في التطبيق).
 *
 * ملحوظة: شاشة الداشبورد بتستخدم `palette[index % size]` بالترتيب لأنها
 * بتربط لون كل فئة بشريحة الدونات المقابلة لها — مينفعش نستبدلها بده هنا
 * لأنها هتفصل بين لون الشريحة ولون الأيقونة في نفس الشاشة.
 */
@Composable
fun getCategoryColor(name: String): Color {
    val palette = chartPalette()
    if (palette.isEmpty()) return Color.Gray
    val index = abs(name.hashCode()) % palette.size
    return palette[index]
}
