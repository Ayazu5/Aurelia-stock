package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Immutable
data class AureliaSemanticColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val surfaceSubtle: Color,
    val hairlineBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val champagne: Color,
    val champagneBright: Color,
    val champagneContainer: Color,
    val emerald: Color,
    val emeraldNeon: Color,
    val emeraldContainer: Color,
    val rose: Color,
    val roseContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val whatsapp: Color,
    val glassSurface: Color
)

private val DarkAureliaColors = AureliaSemanticColors(
    isDark = true,
    background = Obsidian950,
    surfaceElevated = Obsidian900,
    surfaceCard = Obsidian850,
    surfaceSubtle = Obsidian800,
    hairlineBorder = Color.White.copy(alpha = 0.08f),
    textPrimary = Slate100,
    textSecondary = Slate400,
    textMuted = Slate500,
    champagne = Champagne500,
    champagneBright = Champagne400,
    champagneContainer = Champagne500.copy(alpha = 0.14f),
    emerald = EmeraldNeon500,
    emeraldNeon = EmeraldNeon400,
    emeraldContainer = EmeraldNeon500.copy(alpha = 0.14f),
    rose = Rose400,
    roseContainer = Rose500.copy(alpha = 0.14f),
    amber = Amber400,
    amberContainer = Amber500.copy(alpha = 0.14f),
    whatsapp = WhatsAppGreen,
    glassSurface = Obsidian900.copy(alpha = 0.88f)
)

private val LightAureliaColors = AureliaSemanticColors(
    isDark = false,
    background = Alabaster50,
    surfaceElevated = PureWhite,
    surfaceCard = PureWhite,
    surfaceSubtle = Alabaster100,
    hairlineBorder = Slate900.copy(alpha = 0.08f),
    textPrimary = Slate900,
    textSecondary = Slate600,
    textMuted = Slate500,
    champagne = Champagne600,
    champagneBright = Champagne500,
    champagneContainer = Champagne500.copy(alpha = 0.14f),
    emerald = Emerald600,
    emeraldNeon = EmeraldNeon400,
    emeraldContainer = EmeraldNeon500.copy(alpha = 0.12f),
    rose = Rose600,
    roseContainer = Rose500.copy(alpha = 0.10f),
    amber = Amber600,
    amberContainer = Amber500.copy(alpha = 0.12f),
    whatsapp = WhatsAppGreen,
    glassSurface = PureWhite.copy(alpha = 0.90f)
)

val LocalAureliaColors = staticCompositionLocalOf { DarkAureliaColors }

private val DarkColorScheme = darkColorScheme(
    primary = Champagne500,
    onPrimary = Obsidian950,
    primaryContainer = Obsidian800,
    onPrimaryContainer = Champagne400,
    secondary = EmeraldNeon500,
    onSecondary = Obsidian950,
    secondaryContainer = Obsidian850,
    onSecondaryContainer = EmeraldNeon400,
    tertiary = Amber400,
    onTertiary = Obsidian950,
    background = Obsidian950,
    onBackground = Slate100,
    surface = Obsidian900,
    onSurface = Slate100,
    surfaceVariant = Obsidian850,
    onSurfaceVariant = Slate400,
    outline = Obsidian700,
    outlineVariant = Color.White.copy(alpha = 0.08f),
    error = Rose500,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = Champagne600,
    onPrimary = PureWhite,
    primaryContainer = Alabaster100,
    onPrimaryContainer = Champagne600,
    secondary = Emerald600,
    onSecondary = PureWhite,
    secondaryContainer = Alabaster100,
    onSecondaryContainer = Emerald700,
    tertiary = Amber600,
    onTertiary = PureWhite,
    background = Alabaster50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Alabaster100,
    onSurfaceVariant = Slate600,
    outline = Alabaster200,
    outlineVariant = Slate900.copy(alpha = 0.08f),
    error = Rose600,
    onError = PureWhite
)

@Composable
fun AureliaStockTheme(
    darkTheme: Boolean = true,
    uiScalePercent: Int = 100,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkAureliaColors else LightAureliaColors
    val currentDensity = LocalDensity.current
    val scaleFactor = (uiScalePercent.coerceIn(90, 120) / 100f)

    // Adjust fontScale gently while preserving crisp touch-target layout
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * scaleFactor
    )

    CompositionLocalProvider(
        LocalAureliaColors provides semanticColors,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AureliaTypography,
            content = content
        )
    }
}

object AureliaTheme {
    val colors: AureliaSemanticColors
        @Composable get() = LocalAureliaColors.current
}
