package com.localexpense.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color_White,
    primaryContainer = TealPrimaryContainer,
    error = CoralDanger,
    errorContainer = CoralDangerContainer,
    background = SurfaceLight,
    surface = SurfaceCard,
    surfaceVariant = SurfaceMuted,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color_Black,
    primaryContainer = TealPrimary,
    error = CoralDanger,
    background = SurfaceDark,
    surface = SurfaceCardDark,
    onBackground = Color_White,
    onSurface = Color_White
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
