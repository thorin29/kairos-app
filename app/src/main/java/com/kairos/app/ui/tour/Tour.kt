package com.kairos.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The app tour (v0.513): a box with a pointed edge over a dimmed screen,
 * moving through the menu, each page and Settings. Shown once on first use,
 * replayed from Settings › Help & about, and after an update only the stops
 * new since the person last saw it (WHATS_NEW, when tips are on).
 */

/** One line of a stop; [parents] lines only show to parents. */
data class TourLine(val text: String, val parents: Boolean = false)

/**
 * A stop. [where]: "home", "rail", "settings" or "section:<key>" (the page it
 * opens; a page hidden for the person is left out). [target]: the
 * `tourTarget` key it points at; none points at nothing. [page]: no target, so
 * the page shows through and the box sits near the bottom. [since]: the
 * TOUR_VERSION that added it, for WHATS_NEW.
 */
data class TourStop(
    val id: String,
    val where: String,
    val title: String,
    val lines: List<TourLine>,
    val target: String? = null,
    val page: Boolean = false,
    val next: String? = null,
    val since: Int = 1,
) {
    val section: String? get() = where.removePrefix("section:").takeIf { where.startsWith("section:") }
}

/** Bump with each release that adds WHATS_NEW stops. */
const val TOUR_VERSION = 1

private fun l(vararg lines: String) = lines.map { TourLine(text = it) }
private fun p(text: String) = TourLine(text = text, parents = true)

val TOUR_STOPS: List<TourStop> = listOf(
    TourStop(id = "welcome", where = "home", title = "Welcome to Kairos", lines = l("Let’s begin the tour!"), next = "Start tour"),
    TourStop(
        id = "menu", where = "rail", target = "rail.sections", title = "The menu",
        lines = l("Tap the logo on any page to open this menu, or swipe the menu open from the side.", "Let's visit every page now."),
    ),
    TourStop(
        id = "tasks", where = "section:tasks", page = true, title = "Tasks",
        lines = l("One-off jobs. ↻ means it repeats.", "To create one, tap + on Home.") + p("Parents can also create tasks for children."),
    ),
    TourStop(
        id = "calendar", where = "section:calendar", target = "calendar.add", title = "Calendar",
        lines = l("Everyone’s events in one place. Tap + to add an event.", "The sliders icon lets you select event colors and set calendar defaults."),
    ),
    TourStop(
        id = "chores", where = "section:chores", page = true, title = "Chores",
        lines = l("Today’s chores.", "Anyone can claim an Up for grabs chore, and Get ahead lets you do chores early for a bonus.") +
            p("Parents see summaries for all children for the week."),
    ),
    TourStop(
        id = "bible", where = "section:bible", page = true, title = "Bible reading",
        lines = l(
            "See the current Bible reading plan, percentages complete by Old and New Testament and individual books.",
            "Create your own personal reading plan or manually log personal reading.",
        ),
    ),
    TourStop(
        id = "reading", where = "section:reading", page = true, title = "Reading",
        lines = l("Add books and log reading.", "Log pages or chapters as you go, and add a reading goal to track progress if you would like."),
    ),
    TourStop(
        id = "school", where = "section:school", page = true, title = "School",
        lines = l("View your school assignments and progress, late items will show in red.") +
            p("Parents can click on children to see a break-down of school work progress."),
    ),
    TourStop(
        id = "workouts", where = "section:workouts", target = "workouts.progress", title = "Workouts: progress",
        lines = l("Tap a muscle on the body map to see more details."),
    ),
    TourStop(
        id = "heat", where = "section:workouts", target = "workouts.heat", title = "Heat map",
        lines = l("Switch to Heat map to see progress by muscle group.", "Darker means more. The heart is cardio."),
    ),
    TourStop(
        id = "weekly", where = "section:workouts", target = "workouts.weekly", title = "Weekly training",
        lines = l("Each bar is one week, split by type: strength, conditioning, cardio and sport.", "Tap a bar to see the week broken down below the chart."),
    ),
    TourStop(
        id = "week", where = "section:workouts", target = "workouts.week", title = "This week",
        lines = l("Your weekly workout at a glance."),
    ),
    TourStop(
        id = "programs", where = "section:workouts", target = "workouts.programs", title = "Programs",
        lines = l(
            "Click to select and start a program, Kairos has several different programs depending on your goals.",
            "Tap here to view or change programs.",
        ),
    ),
    TourStop(
        id = "plan", where = "section:workouts", target = "workouts.plan", title = "Edit plan and Log workout",
        lines = l(
            "Edit plan lets you change your plan for each day of the week.",
            "Log workout records today’s sets and warm-ups.",
            "Routines holds your warm-ups and stretches.",
        ),
    ),
    TourStop(
        id = "tools", where = "section:workouts", target = "workouts.tools", title = "More tools",
        lines = l(
            "Browse workouts, or create your own HIIT or CrossFit workout.",
            "Calculator displays bars and plates for lifts.",
            "Family Workout of the Day (WOD) is a workout for everyone.",
            "Pause stops your plan for a period of time.",
            "Recent shows what you’ve logged.",
        ),
    ),
    TourStop(
        id = "shopping", where = "section:groceries", target = "shopping.actions", title = "Shopping",
        lines = l("Add an item with the + button.", "Click I'm going shopping to move all items to a checklist to use while shopping."),
    ),
    TourStop(
        id = "money", where = "section:money", page = true, title = "Money",
        lines = l("Track children's finances separate from a bank account by adding transactions.") +
            p("Parents can view all users that are carrying a balance."),
    ),
    TourStop(
        id = "games", where = "section:games", page = true, title = "Game time",
        lines = l("Shows a breakdown of video game time by day and by console.") + p("Parents can view all user's time that is tracked."),
    ),
    TourStop(
        id = "hockey", where = "section:hockey", page = true, title = "Ice time",
        lines = l("Check open hockey, and public skating sessions.") + p("Set in the admin menu on the web."),
    ),
    TourStop(
        id = "characters", where = "section:characters", page = true, title = "Characters",
        lines = l(
            "A page to track a companion that grows when you finish your tasks, workouts, assignments, and Bible reading.",
            "Level it up, hatch new ones, and work with the family toward a shared reward.",
        ),
    ),
    TourStop(
        id = "settings", where = "rail", target = "rail.profile", title = "Settings",
        lines = l("Tap your name at the bottom of the menu to open Settings."),
    ),
    TourStop(
        id = "appearance", where = "settings", target = "settings.Look & feel", title = "Appearance",
        lines = l("Change the app’s color and background, choose light or dark modes, profile photo/image, and other system color options."),
    ),
    TourStop(
        id = "notifications", where = "settings", target = "settings.Notifications", title = "Notifications & reminders",
        lines = l("Turn notifications on or off.", "Set default reminders for calendar events and reading goals."),
    ),
    TourStop(
        id = "done", where = "home", title = "That’s the tour",
        lines = l("You can watch it again anytime in Settings, under Help & about."),
    ),
)

/**
 * Stops for features added after the first tour, each with the TOUR_VERSION
 * that brings it. Shown after an update to people who have seen the tour, when
 * tips are on, behind a "What's new" intro. Added only for big updates, and
 * only once the owner has approved the stops: most releases add none.
 */
val WHATS_NEW: List<TourStop> = emptyList()

private val WHATS_NEW_INTRO = TourStop(
    id = "whats-new",
    where = "home",
    title = "What's new",
    lines = l("A couple of things changed in this update. Tap Show me and we'll take you to each one."),
    next = "Show me",
)

/** The stops to show this person now, or empty: the full tour on first use,
 *  otherwise what's new since [seen] (when tips are on). */
fun tourFor(seen: Int, tips: Boolean, hidden: Set<String>): List<TourStop> {
    if (seen == 0) return fullTour(hidden)
    return newStops(seen = seen, tips = tips, hidden = hidden)
}

/** The whole tour, less pages hidden for the person. */
fun fullTour(hidden: Set<String>): List<TourStop> =
    TOUR_STOPS.filter { s -> s.section == null || s.section !in hidden }

/** What's new since [seen], behind the intro, or empty. */
fun newStops(seen: Int, tips: Boolean, hidden: Set<String>): List<TourStop> {
    val keep = { s: TourStop -> s.section == null || s.section !in hidden }
    if (!tips || seen >= TOUR_VERSION) return emptyList()
    val fresh = WHATS_NEW.filter { it.since > seen && keep(it) }
    return if (fresh.isEmpty()) emptyList() else listOf(WHATS_NEW_INTRO) + fresh
}

/** The running tour, and where its targets are on screen. */
object Tour {
    val targets = mutableStateMapOf<String, Rect>()
    var stops by mutableStateOf<List<TourStop>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    private var onEnd: (() -> Unit)? = null

    val current: TourStop? get() = stops.getOrNull(index)

    /** Which tour is running ("full" or "new"), for resuming after a restart. */
    var kind by mutableStateOf("full")
        private set

    fun start(list: List<TourStop>, onFinish: () -> Unit, kind: String = "full", at: Int = 0) {
        if (list.isEmpty()) return
        onEnd = onFinish
        this.kind = kind
        index = at.coerceIn(0, list.lastIndex)
        stops = list
    }

    fun next() {
        if (index >= stops.lastIndex) finish() else index++
    }

    fun back() {
        if (index > 0) index--
    }

    fun finish() {
        stops = emptyList()
        index = 0
        onEnd?.invoke()
        onEnd = null
    }
}

/** Marks what a tour stop points at; scrolls it into view when its stop comes up. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(key: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val active = Tour.current?.target == key
    LaunchedEffect(active) {
        if (active) {
            delay(250)
            runCatching { requester.bringIntoView() }
        }
    }
    DisposableEffect(key) { onDispose { Tour.targets.remove(key) } }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { c ->
            val r = c.boundsInRoot()
            if (Tour.targets[key] != r) Tour.targets[key] = r
        }
}

private val Dim = Color(0xFF10202F)

/** Over everything while a tour runs. */
@Composable
fun TourOverlay(isParent: Boolean) {
    val stop = Tour.current ?: return
    val n = Tour.stops.size
    val i = Tour.index
    // Hide the box while the page opens and scrolls, then show it once, in
    // place (v0.516): it used to appear at the bottom and jump to its target.
    var settled by remember(stop.id) { mutableStateOf(false) }
    LaunchedEffect(stop.id) {
        delay(200)
        var last: Rect? = null
        var waited = 200L
        while (waited < 1600L) {
            val now = stop.target?.let { Tour.targets[it] }?.takeIf { it.width > 1f && it.height > 1f }
            if (stop.target == null && waited >= 350L) break
            if (now != null && now == last) break
            last = now
            delay(100)
            waited += 100
        }
        settled = true
    }
    val raw = stop.target?.let { Tour.targets[it] }?.takeIf { it.width > 1f && it.height > 1f }
    val spot = if (settled) raw else null
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = if (settled) 220 else 90),
        label = "tourCard",
    )
    BackHandler { if (i > 0) Tour.back() else Tour.finish() }

    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Nothing underneath takes a tap while the tour is up.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pad = with(density) { 6.dp.toPx() }
        val hole = spot?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        val light = stop.page || (settled && stop.target != null && hole == null)
        val dimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (light) 0.3f else 0.62f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 250),
            label = "tourDim",
        )
        Canvas(Modifier.fillMaxSize()) {
            val dim = Dim.copy(alpha = dimAlpha)
            if (hole != null) {
                val corner = CornerRadius(14.dp.toPx())
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(rect = hole, cornerRadius = corner))
                }
                drawPath(path = path, color = dim)
                drawRoundRect(
                    color = Color.White,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else {
                drawRect(color = dim)
            }
        }

        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val cardW = minOf(w - 2 * margin, with(density) { 340.dp.toPx() })
        var cardH by remember(stop.id) { mutableIntStateOf(0) }
        val below: Boolean
        val x: Float
        val y: Float
        if (hole != null) {
            below = hole.bottom + gap + cardH <= h - margin || hole.top - gap - cardH < margin
            x = (hole.center.x - cardW / 2).coerceIn(margin, w - margin - cardW)
            y = if (below) {
                (hole.bottom + gap).coerceAtMost(h - margin - cardH)
            } else {
                hole.top - gap - cardH
            }
        } else {
            below = true
            x = (w - cardW) / 2
            y = if (light) h - cardH - with(density) { 96.dp.toPx() } else (h - cardH) / 2
        }
        val arrowX = hole?.let { (it.center.x - x).coerceIn(with(density) { 20.dp.toPx() }, cardW - with(density) { 36.dp.toPx() }) }

        val edge = MaterialTheme.colorScheme.primary
        Box(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(with(density) { cardW.toDp() })
                .onSizeChanged { cardH = it.height }
                .graphicsLayer { alpha = if (cardH > 0) shown else 0f },
        ) {
            if (arrowX != null) {
                Box(
                    Modifier
                        .align(if (below) Alignment.TopStart else Alignment.BottomStart)
                        .offset { IntOffset((arrowX - 8.dp.toPx()).toInt(), (if (below) -8.dp else 8.dp).roundToPx()) }
                        .size(16.dp)
                        .rotate(45f)
                        .background(surface)
                        .border(width = 1.5.dp, color = edge),
                )
            }
            // An accent edge so the box stands out from a white card under it (v0.516).
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surface,
                shadowElevation = 16.dp,
                border = androidx.compose.foundation.BorderStroke(width = 1.5.dp, color = edge),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TourCard(stop = stop, i = i, n = n, isParent = isParent)
            }
        }
    }
}

@Composable
private fun TourCard(stop: TourStop, i: Int, n: Int, isParent: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (n > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1} of $n", style = MaterialTheme.typography.labelMedium, color = muted)
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((i + 1f) / n)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent),
                    )
                }
            }
        }
        Text(stop.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = accent)
        stop.lines.filter { !it.parents || isParent }.forEach { line ->
            Text(line.text, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (i < n - 1) {
                TextButton(onClick = { Tour.finish() }) { Text("Skip tour", color = muted) }
            }
            Spacer(Modifier.weight(1f))
            if (i > 0) {
                OutlinedButton(onClick = { Tour.back() }) { Text("Back") }
            }
            Button(onClick = { Tour.next() }) { Text(stop.next ?: if (i == n - 1) "Done" else "Next") }
        }
    }
}
