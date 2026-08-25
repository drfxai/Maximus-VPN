package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val SophisticatedDarkColorScheme = darkColorScheme(
    primary = DarkAppColors.primary,
    onPrimary = DarkAppColors.onPrimary,
    primaryContainer = DarkAppColors.primaryContainer,
    onPrimaryContainer = DarkAppColors.onPrimaryContainer,
    secondary = DarkAppColors.metricDownload,
    onSecondary = Color(0xFF042B4E),
    secondaryContainer = Color(0xFF1D4770),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = DarkAppColors.metricUpload,
    onTertiary = Color(0xFF601410),
    tertiaryContainer = Color(0xFF8C1D18),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = DarkAppColors.background,
    onBackground = DarkAppColors.textPrimary,
    surface = DarkAppColors.surface,
    onSurface = DarkAppColors.textPrimary,
    surfaceVariant = DarkAppColors.surfaceCard,
    onSurfaceVariant = DarkAppColors.textSecondary,
    outline = DarkAppColors.borderSubtle,
    outlineVariant = DarkAppColors.borderMedium,
    error = DarkAppColors.statusError,
    onError = Color(0xFF601410)
)

private val SophisticatedLightColorScheme = lightColorScheme(
    primary = LightAppColors.primary,
    onPrimary = LightAppColors.onPrimary,
    primaryContainer = LightAppColors.primaryContainer,
    onPrimaryContainer = LightAppColors.onPrimaryContainer,
    secondary = LightAppColors.metricDownload,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    tertiary = LightAppColors.metricUpload,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE4E6),
    onTertiaryContainer = Color(0xFF9F1239),
    background = LightAppColors.background,
    onBackground = LightAppColors.textPrimary,
    surface = LightAppColors.surface,
    onSurface = LightAppColors.textPrimary,
    surfaceVariant = LightAppColors.surfaceElevated,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.borderSubtle,
    outlineVariant = LightAppColors.borderMedium,
    error = LightAppColors.statusError,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SophisticatedDarkColorScheme else SophisticatedLightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
