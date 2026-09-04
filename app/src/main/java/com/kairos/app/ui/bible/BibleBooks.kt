package com.kairos.app.ui.bible

import androidx.compose.ui.graphics.Color

/**
 * The canon with chapter counts and the groupings the statistics report on —
 * ported verbatim from the web's dependency-free `src/lib/bible/books.ts`, so the
 * plan creator's book grid and the "Mark what you've read" tracker read exactly
 * as the web does. No imports beyond Compose Color for the genre hues.
 */

enum class Testament { OT, NT }

data class BibleBook(
    val name: String,
    val chapters: Int,
    val testament: Testament,
    val group: String,
)

val BIBLE_GROUPS: List<String> = listOf(
    "Pentateuch",
    "History",
    "Wisdom",
    "Major Prophets",
    "Minor Prophets",
    "Gospels",
    "Acts",
    "Paul",
    "General Epistles",
    "Revelation",
)

// A hue per genre, matching the web BookProgress GROUP_COLOR.
val GROUP_COLOR: Map<String, Color> = mapOf(
    "Pentateuch" to Color(0xFFB45309),
    "History" to Color(0xFFB91C1C),
    "Wisdom" to Color(0xFF7C3AED),
    "Major Prophets" to Color(0xFF1D4ED8),
    "Minor Prophets" to Color(0xFF0891B2),
    "Gospels" to Color(0xFF047857),
    "Acts" to Color(0xFF0F766E),
    "Paul" to Color(0xFFC026D3),
    "General Epistles" to Color(0xFFCA8A04),
    "Revelation" to Color(0xFFE11D48),
)

val BIBLE_BOOKS: List<BibleBook> = listOf(
    BibleBook("Genesis", 50, Testament.OT, "Pentateuch"),
    BibleBook("Exodus", 40, Testament.OT, "Pentateuch"),
    BibleBook("Leviticus", 27, Testament.OT, "Pentateuch"),
    BibleBook("Numbers", 36, Testament.OT, "Pentateuch"),
    BibleBook("Deuteronomy", 34, Testament.OT, "Pentateuch"),
    BibleBook("Joshua", 24, Testament.OT, "History"),
    BibleBook("Judges", 21, Testament.OT, "History"),
    BibleBook("Ruth", 4, Testament.OT, "History"),
    BibleBook("1 Samuel", 31, Testament.OT, "History"),
    BibleBook("2 Samuel", 24, Testament.OT, "History"),
    BibleBook("1 Kings", 22, Testament.OT, "History"),
    BibleBook("2 Kings", 25, Testament.OT, "History"),
    BibleBook("1 Chronicles", 29, Testament.OT, "History"),
    BibleBook("2 Chronicles", 36, Testament.OT, "History"),
    BibleBook("Ezra", 10, Testament.OT, "History"),
    BibleBook("Nehemiah", 13, Testament.OT, "History"),
    BibleBook("Esther", 10, Testament.OT, "History"),
    BibleBook("Job", 42, Testament.OT, "Wisdom"),
    BibleBook("Psalms", 150, Testament.OT, "Wisdom"),
    BibleBook("Proverbs", 31, Testament.OT, "Wisdom"),
    BibleBook("Ecclesiastes", 12, Testament.OT, "Wisdom"),
    BibleBook("Song of Solomon", 8, Testament.OT, "Wisdom"),
    BibleBook("Isaiah", 66, Testament.OT, "Major Prophets"),
    BibleBook("Jeremiah", 52, Testament.OT, "Major Prophets"),
    BibleBook("Lamentations", 5, Testament.OT, "Major Prophets"),
    BibleBook("Ezekiel", 48, Testament.OT, "Major Prophets"),
    BibleBook("Daniel", 12, Testament.OT, "Major Prophets"),
    BibleBook("Hosea", 14, Testament.OT, "Minor Prophets"),
    BibleBook("Joel", 3, Testament.OT, "Minor Prophets"),
    BibleBook("Amos", 9, Testament.OT, "Minor Prophets"),
    BibleBook("Obadiah", 1, Testament.OT, "Minor Prophets"),
    BibleBook("Jonah", 4, Testament.OT, "Minor Prophets"),
    BibleBook("Micah", 7, Testament.OT, "Minor Prophets"),
    BibleBook("Nahum", 3, Testament.OT, "Minor Prophets"),
    BibleBook("Habakkuk", 3, Testament.OT, "Minor Prophets"),
    BibleBook("Zephaniah", 3, Testament.OT, "Minor Prophets"),
    BibleBook("Haggai", 2, Testament.OT, "Minor Prophets"),
    BibleBook("Zechariah", 14, Testament.OT, "Minor Prophets"),
    BibleBook("Malachi", 4, Testament.OT, "Minor Prophets"),
    BibleBook("Matthew", 28, Testament.NT, "Gospels"),
    BibleBook("Mark", 16, Testament.NT, "Gospels"),
    BibleBook("Luke", 24, Testament.NT, "Gospels"),
    BibleBook("John", 21, Testament.NT, "Gospels"),
    BibleBook("Acts", 28, Testament.NT, "Acts"),
    BibleBook("Romans", 16, Testament.NT, "Paul"),
    BibleBook("1 Corinthians", 16, Testament.NT, "Paul"),
    BibleBook("2 Corinthians", 13, Testament.NT, "Paul"),
    BibleBook("Galatians", 6, Testament.NT, "Paul"),
    BibleBook("Ephesians", 6, Testament.NT, "Paul"),
    BibleBook("Philippians", 4, Testament.NT, "Paul"),
    BibleBook("Colossians", 4, Testament.NT, "Paul"),
    BibleBook("1 Thessalonians", 5, Testament.NT, "Paul"),
    BibleBook("2 Thessalonians", 3, Testament.NT, "Paul"),
    BibleBook("1 Timothy", 6, Testament.NT, "Paul"),
    BibleBook("2 Timothy", 4, Testament.NT, "Paul"),
    BibleBook("Titus", 3, Testament.NT, "Paul"),
    BibleBook("Philemon", 1, Testament.NT, "Paul"),
    BibleBook("Hebrews", 13, Testament.NT, "General Epistles"),
    BibleBook("James", 5, Testament.NT, "General Epistles"),
    BibleBook("1 Peter", 5, Testament.NT, "General Epistles"),
    BibleBook("2 Peter", 3, Testament.NT, "General Epistles"),
    BibleBook("1 John", 5, Testament.NT, "General Epistles"),
    BibleBook("2 John", 1, Testament.NT, "General Epistles"),
    BibleBook("3 John", 1, Testament.NT, "General Epistles"),
    BibleBook("Jude", 1, Testament.NT, "General Epistles"),
    BibleBook("Revelation", 22, Testament.NT, "Revelation"),
)

fun chapterCountFor(bookNames: Set<String>): Int =
    BIBLE_BOOKS.filter { it.name in bookNames }.sumOf { it.chapters }
