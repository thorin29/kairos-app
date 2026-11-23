package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * A day rested on purpose (v0.547): a pale square with a small moon, on
 * Workout days and This week. No legend; the moon says it. Size and shape
 * come from [modifier]; this fills it.
 */
@Composable
internal fun RestSquare(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF334155) else Color(0xFFE6E9F2)
    val moon = if (dark) Color(0xFF94A3B8) else Color(0xFF7D88A8)
    Canvas(modifier) {
        drawRect(bg)
        val r = size.minDimension * 0.27f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = moon, radius = r, center = c)
        // The bite out of it, in the square's own colour.
        drawCircle(color = bg, radius = r * 0.85f, center = Offset(c.x + r * 0.55f, c.y - r * 0.45f))
    }
}

