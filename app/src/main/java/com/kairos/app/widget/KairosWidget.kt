package com.kairos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kairos.app.KairosApp
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.notifications.CompleteTaskWorker
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.CalEventDto
import com.kairos.app.data.remote.dto.WidgetDto
import com.kairos.app.data.remote.dto.WidgetTaskDto
import com.kairos.app.ui.theme.ThemeScheme
import com.kairos.app.ui.theme.WidgetPalette
import com.kairos.app.ui.theme.widgetPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import androidx.glance.color.ColorProvider as DayNightColorProvider

/**
 * The home-screen widget: today's date, a refresh and an add button, then the
 * next 7 days of tasks and events. Days with nothing on them are left out.
 *
 * Tasks (always the phone owner's own) sit first in each day in the theme
 * color; their box is a checkbox. Events follow, colored exactly as the
 * Calendar colors them — the server resolves that, so the widget and the app
 * can't disagree. Whether events are solid boxes or the home page's color bar,
 * and whose events show, are settings under Appearance.
 */
class KairosWidget : GlanceAppWidget() {

    // Exact, so the header can size itself to the widget's real width.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetStore.ensure(context)
        // Fresh data when it comes quickly; the cached copy otherwise.
        runCatching { WidgetStore.refresh(context) }
        provideContent {
            val st by WidgetStore.state.collectAsState()
            WidgetBody(context, st)
        }
    }

    companion object {
        /** Intent extra: the tapped event as CalEventDto JSON (MainActivity). */
        const val EXTRA_EVENT = "widgetEvent"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Refetch and redraw every placed widget. Safe to call from anywhere,
         * and free when no widget is on the home screen.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                runCatching {
                    val placed = GlanceAppWidgetManager(app).getGlanceIds(KairosWidget::class.java)
                    if (placed.isEmpty()) return@runCatching
                    WidgetStore.refresh(app, force = true)
                    KairosWidget().updateAll(app)
                }
            }
        }
    }
}

class KairosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairosWidget()
}

/** The refresh button. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.refresh(context, force = true)
        KairosWidget().updateAll(context)
    }
}

/** A task's checkbox: shows it done at once, then completes it on the server
 *  (or queues that, offline, through the same worker a notification uses). */
class CompleteTaskWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        WidgetStore.markDone(id)
        KairosWidget().updateAll(context)
        val container = (context.applicationContext as KairosApp).container
        val ok = runCatching {
            container.sessionRepository.completeTaskFromNotification(id)
        }.getOrDefault(false)
        if (ok) {
            WidgetStore.refresh(context, force = true)
            KairosWidget().updateAll(context)
        } else {
            CompleteTaskWorker.enqueue(context, id)
        }
    }
}

private val TaskIdKey = ActionParameters.Key<String>("taskId")

// ---- Drawing ----------------------------------------------------------------

/** The palette as Glance colors: fixed for Light/Dark, following the phone for
 *  System. */
private class WColors(private val mode: String, scheme: ThemeScheme) {
    private val light = widgetPalette(scheme, false)
    private val dark = widgetPalette(scheme, true)
    private fun c(pick: (WidgetPalette) -> Color): ColorProvider = when (mode) {
        "LIGHT" -> ColorProvider(pick(light))
        "DARK" -> ColorProvider(pick(dark))
        else -> DayNightColorProvider(day = pick(light), night = pick(dark))
    }
    val card = c { it.card }
    val ink = c { it.ink }
    val muted = c { it.muted }
    val chip = c { it.chip }
    val chipIcon = c { it.chipIcon }
    val accent = c { it.accent }
    val onAccent = c { it.onAccent }
    val soft = c { it.soft }
}

/** One line of the list. A day's label is drawn beside its first line only. */
private sealed interface WRow {
    data class Overdue(val count: Int) : WRow
    data class Message(val text: String) : WRow
    data class TaskLine(val day: String, val first: Boolean, val last: Boolean, val task: WidgetTaskDto) : WRow
    data class EventLine(val day: String, val first: Boolean, val last: Boolean, val event: CalEventDto) : WRow
}

private fun rowsFor(data: WidgetDto?): List<WRow> {
    if (data == null) return listOf(WRow.Message("Open Kairos to load your week."))
    val out = mutableListOf<WRow>()
    if (data.overdueTasks > 0) out += WRow.Overdue(data.overdueTasks)
    if (data.days.isEmpty()) out += WRow.Message("Nothing scheduled this week.")
    for (d in data.days) {
        val total = d.tasks.size + d.events.size
        var i = 0
        d.tasks.forEach { t -> out += WRow.TaskLine(d.date, i == 0, i == total - 1, t); i++ }
        d.events.forEach { e -> out += WRow.EventLine(d.date, i == 0, i == total - 1, e); i++ }
    }
    return out
}

private fun openApp(context: Context, route: String, event: CalEventDto? = null): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        putExtra(Notifications.EXTRA_OPEN, route)
        if (event != null) {
            putExtra(
                KairosWidget.EXTRA_EVENT,
                ApiClient.json.encodeToString(CalEventDto.serializer(), event),
            )
        }
    }
    return actionStartActivity(intent)
}

@Composable
private fun WidgetBody(context: Context, st: WidgetStore.State?) {
    val k = WColors(st?.themeMode ?: "SYSTEM", ThemeScheme.fromKey(st?.scheme))
    val data = st?.data
    val today = data?.today?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()
    val rows = rowsFor(data)
    // Narrow widgets get a smaller header, so the full month name ("October
    // 19") fits beside the two buttons instead of being cut off.
    val width = LocalSize.current.width
    val narrow = width < 280.dp
    val tiny = width < 220.dp
    val dateSize = if (tiny) 17.sp else if (narrow) 20.sp else 24.sp
    val buttonSize = if (tiny) 34.dp else 40.dp

    Column(
        GlanceModifier.fillMaxSize().background(k.card).cornerRadius(24.dp)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(openApp(context, "calendar"))) {
                Text(
                    today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
                    style = TextStyle(color = k.muted, fontSize = if (tiny) 12.sp else 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                // The month in a normal weight, the day number bold.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " ",
                        style = TextStyle(color = k.ink, fontSize = dateSize, fontWeight = FontWeight.Normal),
                        maxLines = 1,
                    )
                    Text(
                        today.dayOfMonth.toString(),
                        style = TextStyle(color = k.ink, fontSize = dateSize, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
            // Bare icons, no circles: refresh in the quiet text color so it
            // blends in, + in the theme color. The tap area stays full size.
            HeaderButton(
                icon = R.drawable.ic_widget_refresh,
                label = "Refresh",
                tint = k.muted,
                size = buttonSize,
                iconSize = if (tiny) 24.dp else 28.dp,
                action = actionRunCallback<RefreshWidgetAction>(),
            )
            Spacer(GlanceModifier.width(if (tiny) 2.dp else 4.dp))
            HeaderButton(
                icon = R.drawable.ic_widget_plus,
                label = "Add a task or event",
                tint = k.accent,
                size = buttonSize,
                iconSize = if (tiny) 28.dp else 32.dp,
                action =
                actionStartActivity(
                    Intent(context, WidgetAddActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                ),
            )
        }
        Spacer(GlanceModifier.height(12.dp))
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(rows) { row ->
                when (row) {
                    is WRow.Overdue -> OverdueLine(context, k, row.count)
                    is WRow.Message -> Text(
                        row.text,
                        modifier = GlanceModifier.padding(vertical = 8.dp).clickable(openApp(context, "calendar")),
                        style = TextStyle(color = k.muted, fontSize = 14.sp),
                    )
                    is WRow.TaskLine -> DayLine(k, row.day, data?.today, row.first, row.last) {
                        TaskBox(context, k, row.task, done = st?.done?.contains(row.task.id) == true, accent = st?.style == "BAR")
                    }
                    is WRow.EventLine -> DayLine(k, row.day, data?.today, row.first, row.last) {
                        if (st?.style == "BAR") EventBar(context, k, row.event, data?.today) else EventBox(context, row.event, data?.today)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: Int, label: String, tint: ColorProvider, size: Dp, iconSize: Dp, action: Action) {
    // No fill: the corner radius only shapes the press ripple.
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = label,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun OverdueLine(context: Context, k: WColors, count: Int) {
    Row(
        GlanceModifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            GlanceModifier.fillMaxWidth().background(k.soft).cornerRadius(12.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(openApp(context, "tasks")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_widget_task),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(k.accent),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                if (count == 1) "1 overdue task" else "$count overdue tasks",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = k.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Image(
                ImageProvider(R.drawable.ic_widget_chevron),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(k.accent),
            )
        }
    }
}

/** One line under a day: the day's label on its first line, space after its
 *  last. */
@Composable
private fun DayLine(
    k: WColors,
    day: String,
    todayIso: String?,
    first: Boolean,
    last: Boolean,
    content: @Composable () -> Unit,
) {
    val date = runCatching { LocalDate.parse(day) }.getOrNull()
    val label = if (first && date != null) {
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    } else ""
    val number = if (first && date != null) date.dayOfMonth.toString() else ""
    // No circle on today: the date is already at the top of the widget, and
    // every day reads the same. The number box still sets a (clear)
    // background — the launcher reuses list rows, and a row drawn without one
    // kept the last row's, which was the stray circle around Thu and Sat.
    val ring = false
    Row(
        GlanceModifier.fillMaxWidth().padding(bottom = if (last) 12.dp else 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(GlanceModifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = TextStyle(color = if (ring) k.accent else k.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Box(
                GlanceModifier.size(30.dp)
                    .background(if (ring) k.accent else ColorProvider(Color.Transparent))
                    .cornerRadius(15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number,
                    style = TextStyle(color = if (ring) k.onAccent else k.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) { content() }
    }
}

/** A task: a slim theme-color line whose icon is the checkbox, kept shorter
 *  than an event. Solid style fills it; the Accent style outlines it, with the
 *  checkbox and name in the theme color. Both draw the same views (an accent
 *  box with an inner box inset 1.5dp — Glance has no border), only the inner
 *  color and the text color change, so reused list rows stay correct. The rest
 *  of the line opens Tasks with this task pointed out. */
@Composable
private fun TaskBox(context: Context, k: WColors, task: WidgetTaskDto, done: Boolean, accent: Boolean) {
    val fg = if (accent) k.accent else k.onAccent
    Box(
        GlanceModifier.fillMaxWidth().height(30.dp).background(k.accent).cornerRadius(8.dp)
            .padding(if (accent) 1.5.dp else 0.dp),
    ) {
        Row(
            GlanceModifier.fillMaxSize().background(if (accent) k.card else k.accent).cornerRadius(7.dp)
                .padding(end = 10.dp)
                .clickable(openApp(context, "task:${task.id}")),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.width(34.dp).fillMaxHeight().clickable(
                    actionRunCallback<CompleteTaskWidgetAction>(actionParametersOf(TaskIdKey to task.id)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(if (done) R.drawable.ic_widget_task else R.drawable.ic_widget_box),
                    contentDescription = if (done) "Done" else "Mark ${task.title} done",
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(fg),
                )
            }
            Text(
                task.title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun eventColor(hex: String): Color {
    val s = hex.trim().removePrefix("#")
    return runCatching {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> null
        }
    }.getOrNull() ?: Color(0xFF64748B)
}

private fun subLine(e: CalEventDto, todayIso: String?): String = buildString {
    append(if (e.allDay) "All day" else e.timeLabel)
    // A multi-day all-day event is listed once (the server drops the repeats);
    // say how long it runs instead.
    throughLabel(e, todayIso)?.let { append(" · through ").append(it) }
    if (!e.location.isNullOrBlank()) append(" · ").append(e.location)
}

/** The last day of a multi-day all-day event, as "Sat" within the week shown or
 *  "Oct 31" beyond it. Null for anything that fits in one day. */
private fun throughLabel(e: CalEventDto, todayIso: String?): String? {
    if (!e.allDay) return null
    val end = runCatching { LocalDate.parse(e.endDayISO) }.getOrNull() ?: return null
    val day = runCatching { LocalDate.parse(e.dayISO) }.getOrNull() ?: return null
    // All-day ends are exclusive (midnight after the last day).
    val last = if (e.endMinExact == 0) end.minusDays(1) else end
    if (!last.isAfter(day)) return null
    val today = todayIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: day
    return if (last.isBefore(today.plusDays(7))) {
        last.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    } else {
        last.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " " + last.dayOfMonth
    }
}

/** An event as a solid box in its color, the text dark or white — whichever
 *  reads on it. */
@Composable
private fun EventBox(context: Context, e: CalEventDto, todayIso: String?) {
    val bg = eventColor(e.color)
    val fg = ColorProvider(if (bg.luminance() > 0.5f) Color(0xFF0B1220) else Color.White)
    Column(
        GlanceModifier.fillMaxWidth().background(ColorProvider(bg)).cornerRadius(10.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(openApp(context, "calendar", e)),
    ) {
        Text(e.title, style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(subLine(e, todayIso), style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
    }
}

/** The Accent style: a color bar, then the same title and time as a solid box —
 *  no names, which crowded the line until it couldn't be read. */
@Composable
private fun EventBar(context: Context, k: WColors, e: CalEventDto, todayIso: String?) {
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(openApp(context, "calendar", e)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(GlanceModifier.width(4.dp).height(34.dp).background(ColorProvider(eventColor(e.color))).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(e.title, style = TextStyle(color = k.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(subLine(e, todayIso), style = TextStyle(color = k.muted, fontSize = 12.sp), maxLines = 1)
        }
    }
}
