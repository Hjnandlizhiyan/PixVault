package com.pixvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF14215F),
    primaryContainer = Color(0xFF303F85),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFD0BCFF),
    secondaryContainer = Color(0xFF463769),
    tertiary = Color(0xFF9DD7FF),
    background = Color(0xFF0E1020),
    surface = Color(0xFF141628),
    surfaceVariant = Color(0xFF2B2D42),
    surfaceContainerLow = Color(0xFF191B30),
    surfaceContainer = Color(0xFF1D2036),
    onBackground = Color(0xFFE7E7F4),
    onSurface = Color(0xFFE7E7F4),
    onSurfaceVariant = Color(0xFFC7C6D8),
    outline = Color(0xFF8F8EA3)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4D5FCE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF101C5A),
    secondary = Color(0xFF73558F),
    tertiary = Color(0xFF25658A),
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFAF8FF),
    surfaceContainerLow = Color(0xFFF3F0FC),
    surfaceContainer = Color(0xFFEDEAF6)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun PixVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.background,
                contentColor = colorScheme.onBackground
            ) {
                content()
            }
        }
    )
}
