package com.kairos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Slate10,
    primaryContainer = Teal80,
    onPrimaryContainer = Teal20,
    secondary = Sand40,
    background = Slate10,
    surface = Slate10,
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
