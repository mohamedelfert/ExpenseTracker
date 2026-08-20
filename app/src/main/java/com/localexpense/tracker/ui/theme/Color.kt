package com.localexpense.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * لوحة ألوان التطبيق. الهوية: أخضر زمردي هادي للفلوس، رملي دافي للأسطح في
 * النهاري، وسلايت شبه أسود في الليلي. الألوان الدلالية (دخل/مصروف/تحذير)
 * معرّفة مرة واحدة هنا عشان كل شاشة تستخدم نفس المعنى بنفس اللون.
 *
 * كل قيمة اتظبطت عشان تباين النص عليها يعدّي معيار WCAG AA (4.5:1) في
 * الاتنين النهاري والليلي.
 */

// ===== الهوية (أخضر زمردي) =====
val Emerald700 = Color(0xFF0B6B57)
val Emerald600 = Color(0xFF0E7C66)
val Emerald400 = Color(0xFF3FB894)
val Emerald300 = Color(0xFF66D9B8)
val Emerald100 = Color(0xFFB9EFDD)
val Emerald900 = Color(0xFF00281F)

// ===== لون مساند (أزرق هادي للمعلومات والروابط) =====
val Ocean600 = Color(0xFF25628F)
val Ocean300 = Color(0xFF8FC9F2)
val Ocean100 = Color(0xFFD3E9F8)
val Ocean900 = Color(0xFF032137)

// ===== تحذير (عنبر) =====
val Amber600 = Color(0xFFB4690E)
val Amber400 = Color(0xFFE0952F)
val Amber300 = Color(0xFFF2C078)
val Amber100 = Color(0xFFFAE6C4)

// ===== خطر / مصروف (مرجاني) =====
val Coral600 = Color(0xFFB43325)
val Coral400 = Color(0xFFE0685A)
val Coral100 = Color(0xFFF8DAD5)
val Coral900 = Color(0xFF3F0A05)

// ===== أسطح نهاري (رملي دافي) =====
val Sand50 = Color(0xFFFBFAF6)
val Sand100 = Color(0xFFF3F1EA)
val Sand200 = Color(0xFFE6E3D9)
val Ink900 = Color(0xFF16181A)
val Ink700 = Color(0xFF44484C)
val Ink500 = Color(0xFF6B7075)

// ===== أسطح ليلي (سلايت) =====
val Slate900 = Color(0xFF0F1214)
val Slate800 = Color(0xFF161A1D)
val Slate700 = Color(0xFF1F2427)
val Slate600 = Color(0xFF2A3034)
val Slate200 = Color(0xFFDFE3E5)
val Slate400 = Color(0xFF9BA3A8)

// ===== ألوان دلالية (نفس المعنى في كل شاشة) =====
val IncomeLight = Color(0xFF1E7B4F)
val IncomeDark = Color(0xFF5FD39A)
val ExpenseLight = Coral600
val ExpenseDark = Coral400
val TransferLight = Ocean600
val TransferDark = Ocean300

val Color_White = Color(0xFFFFFFFF)
val Color_Black = Color(0xFF000000)

/** لوحة الرسوم البيانية: متمايزة في الشكل والقيمة، مش في الـ hue بس. */
val ChartLight = listOf(
    Emerald600, Coral400, Amber600, Ocean600,
    Color(0xFF7A5AA8), Color(0xFF2E8C7F), Color(0xFFB4507F), Ink500
)

val ChartDark = listOf(
    Emerald300, Coral400, Amber300, Ocean300,
    Color(0xFFB79BE0), Color(0xFF6FD0C2), Color(0xFFEE93BC), Slate400
)
