package com.kairos.app.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Kairos section glyphs, ported verbatim from the web icon set
 * (src/components/icons.tsx) — same 24px grid and stroke path data, so the app
 * uses the real icons, not substitutes. Rects/circles/lines from the SVGs are
 * expressed as equivalent stroked paths. Drawn stroke-only; Icon() tints them.
 */
private fun stroked(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (d in paths) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object KairosIcons {
    val Home = stroked(
        "Home",
        "M3 10.5 12 3l9 7.5",
        "M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5",
        "M9.5 21v-6h5v6",
    )
    val Calendar = stroked(
        "Calendar",
        "M5 5H19A2 2 0 0 1 21 7V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V7A2 2 0 0 1 5 5Z",
        "M3 10h18M8 3v4M16 3v4",
    )
    val Chores = stroked(
        "Chores",
        "M9 5h10M9 12h10M9 19h10",
        "m3.5 5 1.25 1.25L7 4",
        "m3.5 12 1.25 1.25L7 11",
        "M4 19h1.5",
    )
    val Bible = stroked(
        "Bible",
        "M4 5.5A2.5 2.5 0 0 1 6.5 3H19v14H6.5A2.5 2.5 0 0 0 4 19.5V5.5Z",
        "M4 19.5A2.5 2.5 0 0 1 6.5 17H19v4H6.5A2.5 2.5 0 0 1 4 19.5Z",
        "M13 5.5v8.5",
        "M10.4 8h5.2",
    )
    val Book = stroked(
        "Book",
        "M4 5.5A2.5 2.5 0 0 1 6.5 3H19v14H6.5A2.5 2.5 0 0 0 4 19.5V5.5Z",
        "M4 19.5A2.5 2.5 0 0 1 6.5 17H19v4H6.5A2.5 2.5 0 0 1 4 19.5Z",
        "M9 7h6",
    )
    val School = stroked(
        "School",
        "M12 4 2.5 9 12 14l9.5-5L12 4Z",
        "M6 11.5V17c0 1.7 2.7 3 6 3s6-1.3 6-3v-5.5",
    )
    val Gamepad = stroked(
        "Gamepad",
        "M7 8h10a4 4 0 0 1 3.9 3.1l1 4.4A2.6 2.6 0 0 1 19.4 19c-.8 0-1.5-.4-2-1l-1.3-1.7H7.9L6.6 18c-.5.6-1.2 1-2 1a2.6 2.6 0 0 1-2.5-3.5l1-4.4A4 4 0 0 1 7 8Z",
        "M7.5 11.5v2.2M6.4 12.6h2.2",
        "M15.5 12h.01M17.5 14h.01",
    )
    val Dumbbell = stroked(
        "Dumbbell",
        "M6.5 8v8M3.5 10v4M17.5 8v8M20.5 10v4M6.5 12h11",
    )
    val Cart = stroked(
        "Cart",
        "M7.7 20a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z",
        "M15.7 20a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z",
        "M2.5 3.5H5l2.1 10.5a1.6 1.6 0 0 0 1.6 1.3h7.6a1.6 1.6 0 0 0 1.6-1.3L20.5 7H6",
    )
    val Dollar = stroked(
        "Dollar",
        "M12 2V22",
        "M17 5.5H9.75a3.25 3.25 0 0 0 0 6.5h4.5a3.25 3.25 0 0 1 0 6.5H6.5",
    )
    val Trophy = stroked(
        "Trophy",
        "M7 4h10v5a5 5 0 0 1-10 0V4Z",
        "M7 6H4.5A1.5 1.5 0 0 0 3 7.5C3 9.4 4.6 11 6.5 11H7",
        "M17 6h2.5A1.5 1.5 0 0 1 21 7.5c0 1.9-1.6 3.5-3.5 3.5H17",
        "M12 14v3M9 20h6M10 17h4l.5 3h-5l.5-3Z",
    )
    val ChevronLeft = stroked("ChevronLeft", "M15 6l-6 6 6 6")
    val ChevronRight = stroked("ChevronRight", "M9 6l6 6-6 6")
    val Switch = stroked("Switch", "M4 8h13M14 5l3 3-3 3", "M20 16H7M10 13l-3 3 3 3")
    val Moon = stroked("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z")
    val Check = stroked("Check", "m4 12.5 5.5 5.5L20 7")
    val Sliders = stroked(
        "Sliders",
        "M4 6h16", "M4 12h16", "M4 18h16",
        "M7 6a2 2 0 1 0 4 0 2 2 0 1 0 -4 0",
        "M13 12a2 2 0 1 0 4 0 2 2 0 1 0 -4 0",
        "M6 18a2 2 0 1 0 4 0 2 2 0 1 0 -4 0",
    )
    val ViewAgenda = stroked(
        "ViewAgenda",
        "M8 7h11", "M8 12h11", "M8 17h11",
        "M4.5 7h1", "M4.5 12h1", "M4.5 17h1",
    )
    val ViewDay = stroked("ViewDay", "M6 4h12a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z")
    val ViewThreeDay = stroked("ViewThreeDay", "M4 5h16v14H4Z", "M9.33 5v14", "M14.66 5v14")
    val ViewWeek = stroked("ViewWeek", "M4 5h16v14H4Z", "M8 5v14", "M12 5v14", "M16 5v14")
    val ViewMonth = stroked("ViewMonth", "M4 5h16v14H4Z", "M4 10h16", "M4 15h16", "M9.33 5v14", "M14.66 5v14")
    val Trash = stroked("Trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13", "M10 11v6M14 11v6")
    val ChevronDown = stroked("ChevronDown", "M6 9l6 6 6-6")
}
