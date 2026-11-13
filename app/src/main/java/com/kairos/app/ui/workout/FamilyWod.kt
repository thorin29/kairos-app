package com.kairos.app.ui.workout

import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.common.QuickDatePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.ConditioningPlanDto
import com.kairos.app.data.remote.dto.FamilyBoardRowDto
import com.kairos.app.data.remote.dto.FamilyWodChoiceDto
import com.kairos.app.data.remote.dto.FamilyWodDto
import com.kairos.app.data.remote.dto.FamilyWodOverviewDto
import com.kairos.app.data.remote.dto.FamilyWodRequest
import com.kairos.app.data.remote.dto.FamilyWodSetBody
import com.kairos.app.data.remote.dto.FamilyWodSuggestBody
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.rememberContainer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import com.kairos.app.ui.common.AnimatedDialog

/** The family WOD's colour: its badge and card edge (v0.489). */
internal val FAMILY_WOD_AMBER = Color(0xFFD48E00)

private fun wodDay(iso: String): String = try {
    val d = LocalDate.parse(iso)
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + ", " + d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
} catch (_: Exception) {
    iso
}

private fun wodWhen(iso: String): String {
    val today = LocalDate.now()
    return when (iso) {
        today.toString() -> "Today"
        today.plusDays(1).toString() -> "Tomorrow"
        else -> wodDay(iso)
    }
}

@Composable
private fun WodLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A board's rows: rank, name, Best yet, Rx or Scaled, the result. */
@Composable
internal fun FamilyBoardRows(rows: List<FamilyBoardRowDto>, limit: Int? = null) {
    Column(Modifier.fillMaxWidth()) {
        (if (limit != null) rows.take(limit) else rows).forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.rank.toString(),
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.bestYet) {
                        Text("Best yet", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2F6B2F))
                    }
                }
                r.version?.let { v ->
                    val rx = v == "RX"
                    Text(
                        if (rx) "Rx" else "Scaled",
                        modifier = Modifier
                            .border(1.dp, if (rx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    r.result,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BoardCard(wod: FamilyWodDto, past: Boolean) {
    if (wod.board.isEmpty() && wod.waiting == null) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            WodLabel(if (past) "Board" else "Board so far")
            if (wod.board.isNotEmpty()) FamilyBoardRows(rows = wod.board)
            wod.waiting?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The day's family WOD (v0.489), at the top of the day: for someone in it, its
 * conditioning card (no Skip: it's an extra) and the board; for anyone else,
 * Join. Shown on rest and paused days too.
 */
@Composable
internal fun FamilyWodDayCard(
    wod: FamilyWodDto,
    date: String,
    onLog: (ConditioningPlanDto, Int, String?) -> Unit,
    onFeedback: (ConditioningPlanDto, String?, String?) -> Unit,
    onJoin: (Boolean) -> Unit,
) {
    val today = LocalDate.now().toString()
    val past = date < today
    val future = date > today
    val plan = wod.plan
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plan != null && !future) {
            ConditioningCard(
                workout = plan,
                skipped = false,
                onLog = { v, version -> onLog(plan, v, version) },
                onSkip = {},
                onFeedback = { e, h -> onFeedback(plan, e, h) },
                badge = "FAMILY WOD · " + wod.who,
                noSkip = true,
            )
            if (wod.mine == "JOINED" && plan.today == null) {
                TextButton(onClick = { onJoin(false) }) { Text("Leave the family WOD") }
            }
        } else {
            OutlinedCard(
                Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FAMILY_WOD_AMBER),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FAMILY WOD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = FAMILY_WOD_AMBER)
                        Text(
                            wod.name + " · " + wod.typeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val line = when {
                            wod.inPlan -> "In your plan today"
                            wod.who == "Everyone" -> "Everyone’s in"
                            wod.members.size == 1 -> wod.who + " is in"
                            wod.members.isNotEmpty() -> wod.who + " are in"
                            else -> ""
                        }
                        if (line.isNotEmpty()) {
                            Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!wod.inPlan && !past && wod.mine == null) {
                        Button(onClick = { onJoin(true) }) { Text("Join") }
                    } else if (!wod.inPlan && !past && future && wod.mine == "JOINED") {
                        TextButton(onClick = { onJoin(false) }) { Text("Leave") }
                    }
                }
            }
        }
        BoardCard(wod = wod, past = past)
    }
}

// --- the Workouts screen and its pages -----------------------------------------

/**
 * What the Workouts screen's Family WOD button says under its label (v0.494):
 * the next one's day ("Today", "Tomorrow", "Sat, Nov 14"), else suggestions
 * waiting for a parent; the Boolean is true when it's today.
 */
@Composable
internal fun familyWodLine(refreshKey: Int): Pair<String, Boolean>? {
    val session = rememberContainer().sessionRepository
    var data by remember { mutableStateOf<FamilyWodOverviewDto?>(null) }
    LaunchedEffect(refreshKey) { data = runCatching { session.familyWod() }.getOrNull() ?: data }
    val d = data ?: return null
    val next = d.next
    val n = if (d.parent) d.suggestions.size else 0
    return when {
        next != null -> wodWhen(next.date) to (next.date == LocalDate.now().toString())
        n > 0 -> "$n to look at" to false
        else -> null
    }
}

/**
 * The Family WOD page (v0.490): the next one with Join, Suggest one, boards;
 * for parents New family WOD, Coming up and the suggestions to approve. A
 * board opens in place, Back returning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyWodScreen(
    onBack: () -> Unit,
    onEdit: (com.kairos.app.ui.nav.Route.FamilyWodEdit) -> Unit,
    refreshKey: Int = 0,
) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FamilyWodOverviewDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var boardId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refreshKey) { data = runCatching { session.familyWod() }.getOrNull() ?: data }
    fun act(req: FamilyWodRequest) {
        busy = true
        scope.launch {
            runCatching { session.familyWodAction(req) }.onSuccess { data = it }
            busy = false
        }
    }
    BackHandler(enabled = boardId != null) { boardId = null }
    val today = LocalDate.now().toString()
    val d = data
    val shown = if (d == null) emptyList() else d.ahead.filter { it.date <= today } + d.past
    val board = boardId?.let { id -> shown.firstOrNull { it.id == id } ?: d?.ahead?.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(board?.name ?: "Family WOD") },
                navigationIcon = {
                    IconButton(onClick = { if (boardId != null) boardId = null else onBack() }) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (d == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (board != null) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(wodDay(board.date) + " · " + board.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (board.board.isNotEmpty()) FamilyBoardRows(rows = board.board) else Text("No results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        board.waiting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                return@Column
            }

            val next = d.next
            if (next != null) {
                OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, FAMILY_WOD_AMBER)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(wodWhen(next.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(next.typeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.who, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        next.movements.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (next.mine == null) {
                            Button(onClick = { act(FamilyWodRequest(join = next.id, on = true)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Join") }
                        } else if (next.mine == "JOINED" && next.logged == 0) {
                            TextButton(onClick = { act(FamilyWodRequest(join = next.id, on = false)) }, enabled = !busy) { Text("Leave") }
                        }
                        if (next.date <= today && (next.board.isNotEmpty() || next.waiting != null)) {
                            HorizontalDivider()
                            if (next.board.isNotEmpty()) FamilyBoardRows(rows = next.board)
                            next.waiting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            } else {
                Text("None set.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // One button (v0.495): a parent sets one; anyone else suggests one.
                if (d.parent) {
                    Button(onClick = { onEdit(com.kairos.app.ui.nav.Route.FamilyWodEdit()) }, modifier = Modifier.weight(1f)) { Text("New family WOD") }
                } else {
                    Button(onClick = { onEdit(com.kairos.app.ui.nav.Route.FamilyWodEdit(suggest = true)) }, modifier = Modifier.weight(1f)) {
                        Text("Suggest one")
                    }
                }
            }

            if (d.parent && d.ahead.isNotEmpty()) {
                WodLabel("Coming up")
                d.ahead.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(wodWhen(w.date) + " · " + w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(w.who, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onEdit(com.kairos.app.ui.nav.Route.FamilyWodEdit(wodId = w.id)) }) { Text("Edit") }
                    }
                }
            }

            if (d.suggestions.isNotEmpty()) {
                WodLabel(if (d.parent) "Suggestions" else "Your suggestions")
                d.suggestions.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (d.parent) s.by + " suggested it for " else "For ") + wodDay(s.date) +
                                (s.note?.let { " · “$it”" } ?: "") + if (d.parent) "" else " · waiting",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (d.parent) {
                                Button(onClick = { onEdit(com.kairos.app.ui.nav.Route.FamilyWodEdit(suggestionId = s.id)) }) { Text("Approve") }
                                AccentOutlinedButton(onClick = { act(FamilyWodRequest(decline = s.id)) }, enabled = !busy) { Text("Decline") }
                            } else {
                                TextButton(onClick = { act(FamilyWodRequest(withdraw = s.id)) }, enabled = !busy) { Text("Take back") }
                            }
                        }
                    }
                }
            }

            if (shown.isNotEmpty()) {
                WodLabel("Boards")
                shown.forEach { w ->
                    Row(Modifier.fillMaxWidth().clickable { boardId = w.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                wodDay(w.date) + " · " + w.logged + " of " + w.members.size + " logged" +
                                    (w.board.firstOrNull()?.let { " · " + it.name + " " + it.result } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** What a workout is: its type, movements and note, in a pop-up. */
@Composable
private fun WodInfoDialog(c: FamilyWodChoiceDto, onDismiss: () -> Unit) {
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = c.name,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.typeLabel + (c.minutes?.let { " · about $it min" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.movements.forEach { Text(it) }
            c.about?.let { Text(it) }
        }
    }
}

/** The shared workouts, searchable: full names, ⓘ for what each is. */
@Composable
private fun WodPickList(choices: List<FamilyWodChoiceDto>?, picked: String?, onPick: (FamilyWodChoiceDto) -> Unit) {
    var q by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<FamilyWodChoiceDto?>(null) }
    if (choices == null) {
        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Search workouts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val t = q.trim().lowercase()
    val list = choices.filter { t.isEmpty() || it.name.lowercase().contains(t) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        run {
            list.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (picked == c.hiitWorkoutId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).clickable { onPick(c) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            c.typeLabel + (c.minutes?.takeIf { !c.typeLabel.contains("min") }?.let { " · $it min" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { info = c }) { Text("ⓘ", style = MaterialTheme.typography.titleMedium) }
                }
            }
            if (list.isEmpty()) Text("No match.", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    info?.let { WodInfoDialog(c = it, onDismiss = { info = null }) }
}

@Composable
private fun PersonChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Set (a parent) or suggest a family WOD, a full page (v0.490). It's for one
 * day only. Nobody is in until picked: tap who's doing it. From a day's
 * + Additional, [args] carries that day; someone who isn't a parent suggests.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyWodEditScreen(args: com.kairos.app.ui.nav.Route.FamilyWodEdit, onBack: () -> Unit, onSaved: () -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var data by remember { mutableStateOf<FamilyWodOverviewDto?>(null) }
    var date by remember { mutableStateOf(args.date ?: today.toString()) }
    var pickId by remember { mutableStateOf<String?>(null) }
    var pickName by remember { mutableStateOf<String?>(null) }
    var who by remember { mutableStateOf(emptySet<String>()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val d = runCatching { session.familyWod(choices = true) }.getOrNull() ?: FamilyWodOverviewDto(choices = emptyList())
        data = d
        (d.ahead + d.past).firstOrNull { it.id == args.wodId }?.let { w ->
            date = w.date
            pickId = w.hiitWorkoutId
            pickName = w.name
            who = w.members.filter { it.how == "ASSIGNED" }.map { it.userId }.toSet()
        }
        d.suggestions.firstOrNull { it.id == args.suggestionId }?.let { s ->
            date = s.date
            pickId = s.hiitWorkoutId
            pickName = s.name
        }
    }
    val d = data
    val suggest = args.suggest || (d != null && !d.parent)
    val wod = d?.let { (it.ahead + it.past).firstOrNull { w -> w.id == args.wodId } }
    val title = when {
        suggest -> "Suggest a family WOD"
        wod != null -> "Edit family WOD"
        args.suggestionId != null -> "Approve family WOD"
        else -> "New family WOD"
    }
    fun send(req: FamilyWodRequest) {
        busy = true
        error = null
        scope.launch {
            runCatching { session.familyWodAction(req) }
                .onSuccess { onSaved() }
                .onFailure { error = it.message ?: "Couldn’t save it." }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            // The day and the workout on one card (v0.490), who’s in on the next.
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (wod != null || args.date != null) {
                        Text(wodDay(date), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        TextButton(onClick = { pickingDay = true }) { Text(wodDay(date)) }
                    }
                    Text(
                        "Workout" + (pickName?.let { ": $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    WodPickList(
                        choices = d.choices,
                        picked = pickId,
                        onPick = {
                            pickId = it.hiitWorkoutId
                            pickName = it.name
                        },
                    )
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!suggest) {
                        Text("Who’s doing it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val everyone = d.people.isNotEmpty() && d.people.all { it.userId in who }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.people.forEach { p ->
                                PersonChip(label = p.name, on = p.userId in who) { who = if (p.userId in who) who - p.userId else who + p.userId }
                            }
                            PersonChip(label = "Everyone", on = everyone) { who = if (everyone) emptySet() else d.people.map { it.userId }.toSet() }
                        }
                    } else {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(140) },
                            label = { Text("Note (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = pickId != null && (suggest || who.isNotEmpty()) && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val id = pickId ?: return@Button
                    send(
                        if (suggest) {
                            FamilyWodRequest(suggest = FamilyWodSuggestBody(date = date, hiitWorkoutId = id, note = note.ifBlank { null }))
                        } else {
                            FamilyWodRequest(set = FamilyWodSetBody(date = date, hiitWorkoutId = id, users = who.toList(), suggestionId = args.suggestionId))
                        },
                    )
                },
            ) {
                Text(
                    when {
                        suggest -> "Suggest"
                        args.suggestionId != null -> "Approve"
                        wod != null -> "Save"
                        else -> "Set family WOD"
                    },
                )
            }
            if (wod != null && !suggest) {
                TextButton(onClick = { send(FamilyWodRequest(remove = wod.id)) }, enabled = !busy) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (pickingDay) {
        QuickDatePickerDialog(
            initial = runCatching { LocalDate.parse(date) }.getOrNull(),
            minDate = today,
            maxDate = today.plusDays(60),
            title = "Day",
            onPick = {
                date = it.toString()
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
}
