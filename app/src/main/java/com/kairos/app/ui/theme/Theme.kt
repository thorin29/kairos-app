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

/** The color themes a user can pick (stored per device). `swatch` is the light
 *  accent, shown in the picker. */
enum class ThemeScheme(val label: String, val swatch: Color) {
    TEAL("Teal", Color(0xFF0F5C63)),
    OLIVE("Olive drab", Color(0xFF5A6B2F)),
    GREEN("Green", Color(0xFF2E7D32)),
    BLUE("Blue", Color(0xFF1E5FA8)),
    PURPLE("Purple", Color(0xFF6B3FA0)),
    PINK("Pink", Color(0xFFB83280)),
    ORANGE("Orange", Color(0xFFC2570C)),
    RED("Red", Color(0xFFB3261E)),
    GRAPHITE("Graphite", Color(0xFF4F4B46)); // v0.509

    /** The dark-mode accent, for the picker's dots in dark mode. */
    val swatchDark: Color get() = palette(this).accentD

    companion object {
        fun fromKey(k: String?): ThemeScheme =
            entries.firstOrNull { it.name.equals(k, ignoreCase = true) } ?: TEAL
    }
}

/**
 * The neutrals behind everything (page, cards, chips, lines, muted text, ink),
 * chosen per device independently of the color theme (v0.509). CLASSIC is the
 * original look and the default. Same names and colors as the web's
 * Backgrounds (server 0.710).
 */
enum class BackgroundScheme(val label: String) {
    CLASSIC("Classic"),
    STONE("Stone"),
    MIST("Mist"),
    LINEN("Linen"),
    SAGE("Sage"),
    LAVENDER("Lavender"),
    BLUSH("Blush"),
    TWOTONE("Two-tone");

    companion object {
        fun fromKey(k: String?): BackgroundScheme =
            entries.firstOrNull { it.name.equals(k, ignoreCase = true) } ?: CLASSIC
    }
}

/** One mode's neutrals. `chip` is surfaceVariant, `line` outlineVariant. */
data class Neutrals(
    val page: Color,
    val card: Color,
    val chip: Color,
    val muted: Color,
    val line: Color,
    val outline: Color,
    val ink: Color,
)

private fun hex(v: Long) = Color(v)

/** A new background's neutrals; the outline sits between its line and muted text. */
private fun n(page: Long, card: Long, line: Long, chip: Long, muted: Long, ink: Long): Neutrals {
    val l = hex(line)
    val m = hex(muted)
    val outline = Color(
        red = l.red + (m.red - l.red) * 0.35f,
        green = l.green + (m.green - l.green) * 0.35f,
        blue = l.blue + (m.blue - l.blue) * 0.35f,
    )
    return Neutrals(page = hex(page), card = hex(card), chip = hex(chip), muted = m, line = l, outline = outline, ink = hex(ink))
}

fun neutrals(b: BackgroundScheme, dark: Boolean): Neutrals = when (b) {
    BackgroundScheme.CLASSIC ->
        if (dark) {
            Neutrals(page = DarkPage, card = DarkCard, chip = DarkChip, muted = DarkMuted, line = DarkLine, outline = DarkOutline, ink = DarkInk)
        } else {
            Neutrals(page = PageGreyN, card = CardWhite, chip = ChipGrey, muted = Muted, line = LineN, outline = OutlineN, ink = Ink)
        }
    BackgroundScheme.STONE ->
        if (dark) n(0xFF141312, 0xFF1F1D1B, 0xFF33302C, 0xFF282623, 0xFFA8A29A, 0xFFECE9E4)
        else n(0xFFE7E5E1, 0xFFFFFFFF, 0xFFD6D2CC, 0xFFDEDAD4, 0xFF5D5852, 0xFF1C1A17)
    BackgroundScheme.MIST ->
        if (dark) n(0xFF0D1626, 0xFF17233A, 0xFF2A3A55, 0xFF1F2D47, 0xFF9AACCB, 0xFFE6EDF7)
        else n(0xFFE2EAF3, 0xFFFFFFFF, 0xFFCFDBE8, 0xFFD6E1EC, 0xFF516377, 0xFF10202F)
    BackgroundScheme.LINEN ->
        if (dark) n(0xFF15120E, 0xFF221D17, 0xFF362F26, 0xFF2B251E, 0xFFAB9F8E, 0xFFEFE8DC)
        else n(0xFFF0EADF, 0xFFFFFDF8, 0xFFE0D7C6, 0xFFE8E0D0, 0xFF675E50, 0xFF221D14)
    BackgroundScheme.SAGE ->
        if (dark) n(0xFF10140F, 0xFF1B211A, 0xFF2D362B, 0xFF252C23, 0xFF9BA792, 0xFFE6EBE1)
        else n(0xFFE6EADF, 0xFFFBFCF8, 0xFFD5DBCB, 0xFFDDE3D3, 0xFF56624F, 0xFF162013)
    BackgroundScheme.LAVENDER ->
        if (dark) n(0xFF13101B, 0xFF1F1A2B, 0xFF332C45, 0xFF282237, 0xFFA79FBD, 0xFFEBE7F3)
        else n(0xFFE9E6F0, 0xFFFDFCFF, 0xFFD9D3E4, 0xFFE0DBE9, 0xFF5B5570, 0xFF1B1828)
    BackgroundScheme.BLUSH ->
        if (dark) n(0xFF181014, 0xFF261A20, 0xFF3D2B33, 0xFF30212A, 0xFFB39CA7, 0xFFF1E6EB)
        else n(0xFFF2E6EA, 0xFFFFFAFB, 0xFFE4D3D9, 0xFFECDDE2, 0xFF6B5A61, 0xFF24161C)
    BackgroundScheme.TWOTONE ->
        if (dark) n(0xFF15120E, 0xFF1B2119, 0xFF2F3529, 0xFF252C22, 0xFFA3A690, 0xFFEBE9DF)
        else n(0xFFEDE6D8, 0xFFF6F8EF, 0xFFDCD6C4, 0xFFE5E9DA, 0xFF5C5B4B, 0xFF1E1D14)
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
    ThemeScheme.GRAPHITE -> Palette(
        accentL = Color(0xFF4F4B46),
        accentD = Color(0xFFC9C4BC),
        sidebarL = Color(0xFFA8A29A),
        sidebarD = Color(0xFF33302C),
        softL = Color(0xFFE7E4DF),
        softD = Color(0xFF2A2825),
        onAccentD = Color(0xFF1C1A17),
    )
}

/**
 * Brand colors that live outside the Material palette — the accent (read by many
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

private fun lightScheme(p: Palette, n: Neutrals) = lightColorScheme(
    primary = p.accentL,
    onPrimary = Color.White,
    primaryContainer = p.softL,
    onPrimaryContainer = p.accentL,
    secondary = p.accentL,
    onSecondary = Color.White,
    secondaryContainer = p.softL,
    onSecondaryContainer = p.accentL,
    tertiary = Color(0xFFF59E0B),
    background = n.page,
    onBackground = n.ink,
    surface = n.card,
    onSurface = n.ink,
    surfaceVariant = n.chip,
    onSurfaceVariant = n.muted,
    surfaceContainerLowest = n.card,
    surfaceContainerLow = n.card,
    surfaceContainer = n.card,
    surfaceContainerHigh = n.card,
    surfaceContainerHighest = n.card,
    outline = n.outline,
    outlineVariant = n.line,
    error = Color(0xFFB91C1C),
    onError = CardWhite,
)

private fun darkScheme(p: Palette, n: Neutrals) = darkColorScheme(
    primary = p.accentD,
    onPrimary = p.onAccentD,
    primaryContainer = p.softD,
    onPrimaryContainer = p.accentD,
    secondary = p.accentD,
    onSecondary = p.onAccentD,
    secondaryContainer = p.softD,
    onSecondaryContainer = p.accentD,
    tertiary = Color(0xFFFBBF24),
    background = n.page,
    onBackground = n.ink,
    surface = n.card,
    onSurface = n.ink,
    surfaceVariant = n.chip,
    onSurfaceVariant = n.muted,
    surfaceContainerLowest = n.card,
    surfaceContainerLow = n.card,
    surfaceContainer = n.card,
    surfaceContainerHigh = n.chip,
    surfaceContainerHighest = n.chip,
    outline = n.outline,
    outlineVariant = n.line,
    error = Color(0xFFF87171),
    onError = Color(0xFF3A0A08),
)

@Composable
fun KairosTheme(
    scheme: ThemeScheme = ThemeScheme.TEAL,
    darkTheme: Boolean = false,
    background: BackgroundScheme = BackgroundScheme.CLASSIC,
    content: @Composable () -> Unit,
) {
    val p = palette(scheme)
    val n = neutrals(b = background, dark = darkTheme)
    // Sync the out-of-palette brand colors after composition so readers update.
    SideEffect {
        KairosThemeState.accent = if (darkTheme) p.accentD else p.accentL
        KairosThemeState.sidebar = if (darkTheme) p.sidebarD else p.sidebarL
        KairosThemeState.onSidebar = Color.White
        KairosThemeState.dark = darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(p = p, n = n) else lightScheme(p = p, n = n),
        typography = KairosTypography,
        content = content,
    )
}

/** The colors the home-screen widget paints with — the same palette as the app,
 *  so the widget matches whichever color theme and mode the phone uses. */
data class WidgetPalette(
    val card: Color,
    val ink: Color,
    val muted: Color,
    val chip: Color,
    val chipIcon: Color,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val line: Color,
)

fun widgetPalette(scheme: ThemeScheme, dark: Boolean): WidgetPalette {
    val p = palette(scheme)
    return if (dark) {
        WidgetPalette(
            card = DarkCard, ink = DarkInk, muted = DarkMuted, chip = DarkChip,
            chipIcon = Color(0xFFCBD5E1), accent = p.accentD, onAccent = p.onAccentD,
            soft = p.softD, line = DarkLine,
        )
    } else {
        WidgetPalette(
            card = CardWhite, ink = Ink, muted = Muted, chip = ChipGrey,
            chipIcon = Color(0xFF334155), accent = p.accentL, onAccent = Color.White,
            soft = p.softL, line = LineN,
        )
    }
}
