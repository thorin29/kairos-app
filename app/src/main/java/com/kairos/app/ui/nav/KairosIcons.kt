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

/** A solid (filled) icon from one path — used where a filled glyph reads better,
 *  like the Material share nodes. Icon() tints it. */
private fun filled(name: String, path: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    builder.addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    )
    return builder.build()
}

/** A filled icon whose path cuts out inner shapes (even-odd) — e.g. a person
 *  silhouette with a check / X / ? punched out. Single color; Icon() tints it. */
private fun filledEvenOdd(name: String, path: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    builder.addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
        pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
    )
    return builder.build()
}

object KairosIcons {
    // A real cog: eight-tooth gear ring with a center circle (Lucide "settings").
    val Bell = stroked(
        "Bell",
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0",
    )
    val PersonCircle = stroked(
        "PersonCircle",
        "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z",
        "M12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        "M6.2 18.4a6 6 0 0 1 11.6 0",
    )
    val MapPin = stroked(
        "MapPin",
        "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
        "M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
    )
    val Download = stroked(
        "Download",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "M7 10l5 5 5-5",
        "M12 15V3",
    )
    val Settings = stroked(
        "Settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
    )
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
    val Tasks = stroked(
        "Tasks",
        "M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z",
        "m8.5 12 2.5 2.5 4.5-5.5",
    )
    val Plus = stroked("Plus", "M12 5v14", "M5 12h14")
    val Bookmark = stroked("Bookmark", "M6 3h12a1 1 0 0 1 1 1v17l-7-4-7 4V4a1 1 0 0 1 1-1Z")
    val Search = stroked(
        "Search",
        "M11 4a7 7 0 1 0 0 14 7 7 0 1 0 0-14Z",
        "m21 21-4.3-4.3",
    )
    val Pencil = stroked("Pencil", "M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z")

    val Swap = stroked(
        "Swap",
        "M23 4v6h-6",
        "M1 20v-6h6",
        "M3.51 9a9 9 0 0 1 14.85-3.36L23 10",
        "M1 14l4.64 4.36A9 9 0 0 0 20.49 15",
    )

    // Hatching egg: a bigger egg with a single crack straight across the middle.
    val Egg = stroked(
        "Egg",
        "M12 2.5c-3.8 0-6.3 5.7-6.3 10.4a6.3 6.3 0 0 0 12.6 0c0-4.7-2.5-10.4-6.3-10.4z",
        "M5.9 13.2l2.4-1.8 2.4 1.8 2.4-1.8 2.4 1.8",
    )

    // Drawn glyphs for common items Unicode has no emoji for.
    val Napkin = stroked("Napkin", "M3 19h18L12 5Z", "M12 5 8.5 19", "M12 5 15.5 19")

    val WaterBottle = stroked(
        "WaterBottle",
        "M10 2h4",
        "M9 4h6v2l1.5 3v9a2 2 0 0 1-2 2h-5a2 2 0 0 1-2-2V9l1.5-3Z",
        "M8.5 12h7",
    )
    val Repeat = stroked("Repeat", "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
    val Globe = stroked("Globe", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z", "M3 12h18", "M12 3c2.5 2.5 3.5 6 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-6-3.5-9s1-6.5 3.5-9Z")
    val DragHandle = stroked("DragHandle", "M4 9h16", "M4 15h16")
    val Palette = stroked("Palette", "M12 3a9 9 0 1 0 0 18c1 0 1.5-.9 1.5-1.6 0-.4-.2-.7-.5-1-.3-.3-.5-.6-.5-1 0-.8.7-1.4 1.5-1.4H16a5 5 0 0 0 5-5c0-4.4-4-8-9-8Z", "M7.5 11.5v.01", "M11 7.5v.01", "M16 9.5v.01")
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
    val Share = filled(
        "Share",
        "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z",
    )

    // Attendance markers: a person silhouette with a check / X / ? punched out of
    // the body (even-odd). Tinted at the call site (green / red / grey).
    private const val PERSON_HEAD = "M12 1.5a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z"
    private const val PERSON_BODY = "M9 9h6a4 4 0 0 1 4 4v10.5H5V13a4 4 0 0 1 4-4z"
    val PersonCheck = filledEvenOdd(
        "PersonCheck",
        PERSON_HEAD + PERSON_BODY +
            "M11.2 18.1l-1.9-1.9 1-1 .9.9 2.3-2.3 1 1z",
    )
    val PersonX = filledEvenOdd(
        "PersonX",
        PERSON_HEAD + PERSON_BODY +
            "M12 15.4l1.6-1.6 1 1-1.6 1.6 1.6 1.6-1 1-1.6-1.6-1.6 1.6-1-1 1.6-1.6-1.6-1.6 1-1z",
    )
    val PersonQuestion = filledEvenOdd(
        "PersonQuestion",
        PERSON_HEAD + PERSON_BODY +
            "M12 13.7c-1.1 0-2 .7-2.2 1.6l1.2.3c.1-.5.5-.7 1-.7.5 0 .9.3.9.7 0 .4-.3.6-.7.9-.6.4-.9.8-.9 1.5h1.2c0-.4.1-.5.6-.9.5-.4.9-.8.9-1.6 0-1-.9-1.8-2-1.8z" +
            "M11.4 18.5h1.2v1.2h-1.2z",
    )

    // Class-form field glyphs. Drawn on the same 24px grid; distinct silhouettes
    // so each field reads by shape, not just label.
    val Puzzle = stroked(
        "Puzzle",
        "M5 8h2.3a2 2 0 1 1 3.4 0H14a1 1 0 0 1 1 1v2.3a2 2 0 1 1 0 3.4V19a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8z",
    )
    val Category = stroked(
        "Category",
        "M7 4.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z",
        "M17 4.5l3 5.5h-6z",
        "M12 14l3.5 3.5L12 21l-3.5-3.5z",
    )
    val DateRange = stroked(
        "DateRange",
        "M5 7v10",
        "M19 7v10",
        "M5 12h14",
        "M8 9l-3 3 3 3",
        "M16 9l3 3-3 3",
    )
    val Clock = stroked(
        "Clock",
        "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z",
        "M12 8v4l3 2",
    )
}
