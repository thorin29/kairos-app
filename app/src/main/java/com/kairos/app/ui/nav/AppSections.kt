package com.kairos.app.ui.nav

import androidx.compose.ui.graphics.Color

/**
 * The app's top-level sections, mirroring the web sidebar. Each keeps its web
 * brand colour, shown as a dot in the drawer (no icon-font dependency). "home"
 * is the real page; the rest are placeholders until their section is built.
 */
data class AppSection(
    val key: String,
    val label: String,
    val color: Color,
    val built: Boolean = false,
)

val APP_SECTIONS: List<AppSection> = listOf(
    AppSection("home", "Home", Color(0xFF0F5C63), built = true),
    AppSection("calendar", "Calendar", Color(0xFF2563EB)),
    AppSection("chores", "Chores", Color(0xFFD97706)),
    AppSection("bible", "Bible reading", Color(0xFF7C3AED)),
    AppSection("reading", "Reading", Color(0xFF0891B2)),
    AppSection("school", "School", Color(0xFF4F46E5)),
    AppSection("games", "Game time", Color(0xFF059669)),
    AppSection("workouts", "Workouts", Color(0xFFDC2626)),
    AppSection("groceries", "Groceries", Color(0xFF0D9488)),
    AppSection("money", "Money", Color(0xFF15803D)),
    AppSection("characters", "Characters", Color(0xFFDB2777)),
)

fun sectionFor(key: String): AppSection =
    APP_SECTIONS.firstOrNull { it.key == key }
        ?: AppSection(key, key.replaceFirstChar { it.uppercase() }, Color(0xFF64748B))
