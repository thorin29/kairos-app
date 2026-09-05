package com.kairos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Explicit neutrals so cards read white and chrome reads slate — otherwise the
// unset Material3 roles (surfaceVariant / surfaceContainer*) fall back to the
// default violet scheme, which is why cards looked purple.
private val CardWhite = Color(0xFFFFFFFF)
private val PageGrey = Color(0xFFE9EDF3)
private val ChipGrey = Color(0xFFEDF1F6)
private val Muted = Color(0xFF64748B)
private val Line = Color(0xFFE2E8F0)
private val Outline = Color(0xFFB4C1CF)

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = CardWhite,
    primaryContainer = Teal80,
    onPrimaryContainer = Teal20,
    secondary = Teal60,
    onSecondary = CardWhite,
    secondaryContainer = Color(0xFFCDEFE9),
    onSecondaryContainer = Teal20,
    tertiary = Sand40,
    background = PageGrey,
    onBackground = Slate90,
    surface = CardWhite,
    onSurface = Slate90,
    surfaceVariant = ChipGrey,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,
    outline = Outline,
    outlineVariant = Line,
    error = Color(0xFFB91C1C),
    onError = CardWhite,
)

private val DarkColors = darkColorScheme(
    primary = Teal60,
    onPrimary = Teal20,
    primaryContainer = Teal20,
    onPrimaryContainer = Teal80,
    secondary = Sand80,
    background = Slate90,
    surface = Slate90,
)

@Composable
fun KairosTheme(
    // Kairos has one design — the light scheme, matching the web. We deliberately
    // don't follow the system dark setting until a dark theme is designed.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KairosTypography,
        content = content,
    )
}
