package com.packfare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * "Forest & sky" palette, inspired by the hills, lakes and red earth of the places in the app:
 *   primary   = forest green (deodar)
 *   secondary = lake blue (Pangong)
 *   tertiary  = terracotta clay
 * on soft stone-white surfaces with a hint of green.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD9),
    onPrimaryContainer = Color(0xFF0A2E1D),
    secondary = Color(0xFF2B6CA3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E6F7),
    onSecondaryContainer = Color(0xFF0B2540),
    tertiary = Color(0xFF9A5B34),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5DECB),
    onTertiaryContainer = Color(0xFF34190A),
    background = Color.Transparent, // the page colour and compass are painted once, behind every screen (PageBackdrop)
    onBackground = Color(0xFF1B211D),
    surface = Color(0xFFF6F7F2),
    onSurface = Color(0xFF1B211D),
    surfaceVariant = Color(0xFFDFE5DC),
    onSurfaceVariant = Color(0xFF424A44),
    outline = Color(0xFF727A73),
    outlineVariant = Color(0xFFC2C9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3EC),
    surfaceContainer = Color(0xFFEAEEE6),
    surfaceContainerHigh = Color(0xFFE4E9E0),
    surfaceContainerHighest = Color(0xFFDEE3DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93D3B1),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF145237),
    onPrimaryContainer = Color(0xFFCDEBD9),
    secondary = Color(0xFFA3C9F0),
    onSecondary = Color(0xFF0A3254),
    secondaryContainer = Color(0xFF1E4F7A),
    onSecondaryContainer = Color(0xFFD3E6F7),
    tertiary = Color(0xFFEDBB97),
    onTertiary = Color(0xFF4F250A),
    tertiaryContainer = Color(0xFF6E3B1C),
    onTertiaryContainer = Color(0xFFF5DECB),
    background = Color.Transparent,
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF121613),
    onSurface = Color(0xFFE0E4DE),
    surfaceVariant = Color(0xFF414943),
    onSurfaceVariant = Color(0xFFC1C8C0),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414943),
    surfaceContainerLowest = Color(0xFF0D110E),
    surfaceContainerLow = Color(0xFF1A1E1B),
    surfaceContainer = Color(0xFF1E231F),
    surfaceContainerHigh = Color(0xFF282D29),
    surfaceContainerHighest = Color(0xFF333834),
)

/** Solid page colour painted behind everything, since the theme background is transparent. */
fun pageColor(dark: Boolean) = if (dark) Color(0xFF121613) else Color(0xFFF6F7F2)

/** Colour for money the group owes a person (green) vs money they owe (red). */
object BalanceColors {
    val positive = Color(0xFF15803D)
    val negative = Color(0xFFDC2626)
}

/** Forest green → teal → lake blue, used for hero headers and cards. Text on it is white. */
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF1F5C45), Color(0xFF22717A), Color(0xFF2B6CA3)))

@Composable
fun PackfareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
