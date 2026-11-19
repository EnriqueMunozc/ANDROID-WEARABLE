package com.example.focuswear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = DeepGreen,
    primaryContainer = Evergreen,
    onPrimaryContainer = SageHint,
    secondary = Sage,
    onSecondary = DeepGreen,
    secondaryContainer = Color(0xFF314638),
    onSecondaryContainer = SageHint,
    tertiary = PaleAmber,
    onTertiary = DeepGreen,
    tertiaryContainer = Color(0xFF4C3822),
    onTertiaryContainer = PaleAmber,
    background = DeepGreen,
    onBackground = SageHint,
    surface = DarkSurface,
    onSurface = SageHint,
    surfaceVariant = Color(0xFF26362B),
    onSurfaceVariant = Mint,
    outline = Sage,
    outlineVariant = Evergreen
)

private val LightColorScheme = lightColorScheme(
    primary = Evergreen,
    onPrimary = WarmWhite,
    primaryContainer = SageHint,
    onPrimaryContainer = Ink,
    secondary = Moss,
    onSecondary = WarmWhite,
    secondaryContainer = Mint,
    onSecondaryContainer = Ink,
    tertiary = WarmAmber,
    onTertiary = WarmWhite,
    tertiaryContainer = PaleAmber,
    onTertiaryContainer = Color(0xFF3D2915),
    background = WarmWhite,
    onBackground = Ink,
    surface = SoftSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EFE7),
    onSurfaceVariant = MutedInk,
    outline = Moss,
    outlineVariant = Mint
)

@Composable
fun FocusWearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
