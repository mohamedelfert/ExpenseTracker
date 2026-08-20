package com.localexpense.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * زوايا أكبر شوية من افتراضيات Material 3 — الشكل ده اللي بيعطي الإحساس
 * الحديث للبطاقات المالية، ومقاسات محدّدة في مكان واحد أحسن من `RoundedCornerShape`
 * متكررة برقم مختلف في كل شاشة.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** شكل كبسولة كامل — للشيبس والأزرار والبادچات الصغيرة. */
val PillShape = RoundedCornerShape(50)
