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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
