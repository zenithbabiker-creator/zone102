package com.example.landscapedesign.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider

private val GreenPrimary = Color(0xFF2E7D32)
private val GreenPrimaryDark = Color(0xFF66BB6A)
private val SoilBrown = Color(0xFF8D6E63)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = SoilBrown,
    tertiary = Color(0xFF00796B)
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    secondary = SoilBrown,
    tertiary = Color(0xFF4DB6AC)
)

/** App is Arabic-only, so layout direction is forced to RTL regardless of system locale. */
@Composable
fun LandscapeDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}
