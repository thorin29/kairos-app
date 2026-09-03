package com.kairos.app.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's top-level sections, mirroring the web sidebar — same order, brand
 * colours, and glyphs. "home" is the real page; the rest are placeholders until
 * their section is built.
 */
data class AppSection(
    val key: String,
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val built: Boolean = false,
)

val APP_SECTIONS: List<AppSection> = listOf(
    AppSection("home", "Home", Color(0xFF0F5C63), KairosIcons.Home, built = true),
    AppSection("calendar", "Calendar", Color(0xFF2563EB), KairosIcons.Calendar),
    AppSection("chores", "Chores", Color(0xFFD97706), KairosIcons.Chores),
    AppSection("bible", "Bible reading", Color(0xFF7C3AED), KairosIcons.Bible),
    AppSection("reading", "Reading", Color(0xFF0891B2), KairosIcons.Book),
    AppSection("school", "School", Color(0xFF4F46E5), KairosIcons.School),
    AppSection("games", "Game time", Color(0xFF059669), KairosIcons.Gamepad),
    AppSection("workouts", "Workouts", Color(0xFFDC2626), KairosIcons.Dumbbell),
    AppSection("groceries", "Groceries", Color(0xFF0D9488), KairosIcons.Cart),
    AppSection("money", "Money", Color(0xFF15803D), KairosIcons.Dollar),
    AppSection("characters", "Characters", Color(0xFFDB2777), KairosIcons.Trophy),
)

fun sectionFor(key: String): AppSection =
    APP_SECTIONS.firstOrNull { it.key == key } ?: APP_SECTIONS.first()
