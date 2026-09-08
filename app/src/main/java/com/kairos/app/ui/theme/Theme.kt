package com.kairos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The colour themes a user can pick (stored per device). `swatch` is the light
 *  accent, shown in the picker. */
enum class ThemeScheme(val label: String, val swatch: Color) {
    TEAL("Teal", Color(0xFF0F5C63)),
    OLIVE("Olive drab", Color(0xFF5A6B2F)),
    GREEN("Green", Color(0xFF2E7D32)),
    BLUE("Blue", Color(0xFF1E5FA8)),
    PURPLE("Purple", Color(0xFF6B3FA0)),
    PINK("Pink", Color(0xFFB83280)),
    ORANGE("Orange", Color(0xFFC2570C)),
    RED("Red", Color(0xFFB3261E));

    companion object {
        fun fromKey(k: String?): ThemeScheme =
            entries.firstOrNull { it.name.equals(k, ignoreCase = true) } ?: TEAL
    }
}

private data class Palette(
    val accentL: Color,
    val accentD: Color,
    val sidebarL: Color,
    val sidebarD: Color,
    val softL: Color,
    val softD: Color,
    val onAccentD: Color,
)

private fun palette(s: ThemeScheme): Palette = when (s) {
    ThemeScheme.TEAL -> Palette(Color(0xFF0F5C63), Color(0xFF2DD4BF), Color(0xFF86A0A3), Color(0xFF14343A), Color(0xFFCDEFE9), Color(0xFF123A3E), Color(0xFF04231F))
    ThemeScheme.OLIVE -> Palette(Color(0xFF5A6B2F), Color(0xFFAAC066), Color(0xFF9AA37E), Color(0xFF333A1E), Color(0xFFE6EBD1), Color(0xFF2C3319), Color(0xFF20260C))
    ThemeScheme.GREEN -> Palette(Color(0xFF2E7D32), Color(0xFF5BD37A), Color(0xFF8FB097), Color(0xFF1E3D26), Color(0xFFD6EFDA), Color(0xFF1B3421), Color(0xFF07260F))
    ThemeScheme.BLUE -> Palette(Color(0xFF1E5FA8), Color(0xFF6BA6F5), Color(0xFF8CA0BC), Color(0xFF1E2C48), Color(0xFFD6E4F7), Color(0xFF19263D), Color(0xFF041B34))
    ThemeScheme.PURPLE -> Palette(Color(0xFF6B3FA0), Color(0xFFC08BF5), Color(0xFFA394BC), Color(0xFF2C2146), Color(0xFFE7DAF7), Color(0xFF261B3D), Color(0xFF1C0A33))
    ThemeScheme.PINK -> Palette(Color(0xFFB83280), Color(0xFFF77FBE), Color(0xFFBC93AC), Color(0xFF3D1F32), Color(0xFFF7D9EC), Color(0xFF34182B), Color(0xFF360A24))
    ThemeScheme.ORANGE -> Palette(Color(0xFFC2570C), Color(0xFFFB9E4B), Color(0xFFBFA088), Color(0xFF40280F), Color(0xFFFBE3CC), Color(0xFF37220D), Color(0xFF331705))
    ThemeScheme.RED -> Palette(Color(0xFFB3261E), Color(0xFFF48078), Color(0xFFBF9491), Color(0xFF411E1B), Color(0xFFF7D6D3), Color(0xFF371917), Color(0xFF330A07))
}

/**
 * Brand colours that live outside the Material palette — the accent (read by many
 * screens as a plain val) and the sidebar. Snapshot-backed so switching theme
 * re-composes every reader. Written only by [KairosTheme].
 */
object KairosThemeState {
    var accent by mutableStateOf(Color(0xFF0F5C63))
    var sidebar by mutableStateOf(Color(0xFF86A0A3))
    var onSidebar by mutableStateOf(Color.White)
    var dark by mutableStateOf(false)
}

// Neutrals — light
private val CardWhite = Color(0xFFFFFFFF)
private val PageGreyN = Color(0xFFE9EDF3)
private val ChipGrey = Color(0xFFEDF1F6)
private val Muted = Color(0xFF64748B)
private val LineN = Color(0xFFE2E8F0)
private val OutlineN = Color(0xFFB4C1CF)
private val Ink = Color(0xFF0B1220)

// Neutrals — dark
private val DarkPage = Color(0xFF0B1220)
private val DarkCard = Color(0xFF161E2E)
private val DarkChip = Color(0xFF202A3B)
private val DarkMuted = Color(0xFF94A3B8)
private val DarkLine = Color(0xFF2A3547)
private val DarkOutline = Color(0xFF3A465A)
private val DarkInk = Color(0xFFE7ECF3)

private fun lightScheme(p: Palette) = lightColorScheme(
    primary = p.accentL,
    onPrimary = Color.White,
    primaryContainer = p.softL,
    onPrimaryContainer = p.accentL,
    secondary = p.accentL,
    onSecondary = Color.White,
    secondaryContainer = p.softL,
    onSecondaryContainer = p.accentL,
    tertiary = Color(0xFFF59E0B),
    background = PageGreyN,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = ChipGrey,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,
    outline = OutlineN,
    outlineVariant = LineN,
    error = Color(0xFFB91C1C),
    onError = CardWhite,
)

private fun darkScheme(p: Palette) = darkColorScheme(
    primary = p.accentD,
    onPrimary = p.onAccentD,
    primaryContainer = p.softD,
    onPrimaryContainer = p.accentD,
    secondary = p.accentD,
    onSecondary = p.onAccentD,
    secondaryContainer = p.softD,
    onSecondaryContainer = p.accentD,
    tertiary = Color(0xFFFBBF24),
    background = DarkPage,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkChip,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkCard,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkChip,
    surfaceContainerHighest = DarkChip,
    outline = DarkOutline,
    outlineVariant = DarkLine,
    error = Color(0xFFF87171),
    onError = Color(0xFF3A0A08),
)

@Composable
fun KairosTheme(
    scheme: ThemeScheme = ThemeScheme.TEAL,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = palette(scheme)
    // Sync the out-of-palette brand colours after composition so readers update.
    SideEffect {
        KairosThemeState.accent = if (darkTheme) p.accentD else p.accentL
        KairosThemeState.sidebar = if (darkTheme) p.sidebarD else p.sidebarL
        KairosThemeState.onSidebar = Color.White
        KairosThemeState.dark = darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(p) else lightScheme(p),
        typography = KairosTypography,
        content = content,
    )
}
