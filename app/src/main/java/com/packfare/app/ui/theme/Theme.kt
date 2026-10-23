package com.packfare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Desi palette:
 *   primary   = deep saffron (kesari)
 *   secondary = peacock teal (mor)
 *   tertiary  = rani pink
 * on warm, cream-tinted surfaces instead of Material's default greys.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2500F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC7),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Color(0xFF006A6A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9FF2EF),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFFB0226A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E6),
    onTertiaryContainer = Color(0xFF3E0021),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85746A),
    outlineVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEBE2),
    surfaceContainerHigh = Color(0xFFF7E5DC),
    surfaceContainerHighest = Color(0xFFF1DFD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFF773300),
    onPrimaryContainer = Color(0xFFFFDCC7),
    secondary = Color(0xFF80D5D3),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFF9FF2EF),
    tertiary = Color(0xFFFFB0CD),
    onTertiary = Color(0xFF640038),
    tertiaryContainer = Color(0xFF8C0B51),
    onTertiaryContainer = Color(0xFFFFD8E6),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF140D09),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D332D),
)

/** Colour for money the group owes a person (green) vs money they owe (red). */
object BalanceColors {
    val positive = Color(0xFF15803D)
    val negative = Color(0xFFDC2626)
}

/** Saffron → rani pink, used for the trip dashboard hero card. Text on it is white. */
val HeroGradient = Brush.linearGradient(listOf(Color(0xFFD9540F), Color(0xFFB0226A)))

@Composable
fun PackfareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
