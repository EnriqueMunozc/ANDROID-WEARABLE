
package com.example.focuswear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val SageHint = Color(0xFFDCE9E8)
val Mint = Color(0xFFB5D3D0)
val Sage = Color(0xFF80AAA5)
val Moss = Color(0xFF527F79)
val Evergreen = Color(0xFF28534D)
val DeepGreen = Color(0xFF081514)
val WatchSurface = Color(0xFF142724)
val StudyGreen = Color(0xFFB5D3D0)
val RestGold = Color(0xFFEBCB91)
val Warning = Color(0xFFFFD69B)

private val FocusWearColors = ColorScheme(
    primary = Mint,
    primaryDim = Sage,
    onPrimary = DeepGreen,
    primaryContainer = Evergreen,
    onPrimaryContainer = SageHint,
    secondary = Sage,
    secondaryDim = Moss,
    onSecondary = DeepGreen,
    secondaryContainer = Color(0xFF243F3B),
    onSecondaryContainer = SageHint,
    tertiary = RestGold,
    tertiaryDim = Color(0xFFCBAE76),
    onTertiary = Color(0xFF33250E),
    tertiaryContainer = Color(0xFF493719),
    onTertiaryContainer = Color(0xFFFFE4B8),
    background = DeepGreen,
    onBackground = Color(0xFFF1F5F3),
    surfaceContainerLow = Color(0xFF101E1B),
    surfaceContainer = WatchSurface,
    surfaceContainerHigh = Color(0xFF203A35),
    onSurface = Color(0xFFF1F5F3),
    onSurfaceVariant = Mint,
    outline = Sage,
    outlineVariant = Evergreen
)

@Composable
fun FocusWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FocusWearColors, content = content)
}