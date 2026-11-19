package com.example.focuswear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val SageHint = Color(0xFFDAE5D9)
val Mint = Color(0xFFBFCFBB)
val Sage = Color(0xFF8EA58C)
val Moss = Color(0xFF738A6E)
val Evergreen = Color(0xFF344C3D)
val DeepGreen = Color(0xFF08110C)
val WatchSurface = Color(0xFF15221A)
val StudyGreen = Color(0xFFBFCFBB)
val RestGold = Color(0xFFF1D3A1)
val Warning = Color(0xFFFFD8A0)

private val FocusWearColors = ColorScheme(
    primary = Mint,
    primaryDim = Sage,
    onPrimary = DeepGreen,
    primaryContainer = Evergreen,
    onPrimaryContainer = SageHint,
    secondary = Sage,
    secondaryDim = Moss,
    onSecondary = DeepGreen,
    secondaryContainer = Color(0xFF293D31),
    onSecondaryContainer = SageHint,
    tertiary = RestGold,
    tertiaryDim = Color(0xFFD4B779),
    onTertiary = Color(0xFF33250E),
    tertiaryContainer = Color(0xFF493719),
    onTertiaryContainer = Color(0xFFFFE4B8),
    background = DeepGreen,
    onBackground = Color(0xFFF1F4EE),
    surfaceContainerLow = Color(0xFF101A14),
    surfaceContainer = WatchSurface,
    surfaceContainerHigh = Color(0xFF203027),
    onSurface = Color(0xFFF1F4EE),
    onSurfaceVariant = Mint,
    outline = Sage,
    outlineVariant = Evergreen
)

@Composable
fun FocusWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FocusWearColors, content = content)
}
