package com.kairos.app.ui.workout

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
import androidx.compose.material3.AlertDialog
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
import com.kairos.app.data.remote.dto.FamilyWodPersonDto
import com.kairos.app.data.remote.dto.FamilyWodRequest
import com.kairos.app.data.remote.dto.FamilyWodSetBody
import com.kairos.app.data.remote.dto.FamilyWodSuggestBody
import com.kairos.app.data.remote.dto.FamilyWodSuggestionDto
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.rememberContainer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

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

// --- the Workouts menu --------------------------------------------------------

/**
 * Family WOD in the Workouts menu (v0.489): today's or the next one, Join,
 * the board and past ones, Suggest one, and for parents what's ahead and the
 * suggestions to look at.
 */
@Composable
internal fun FamilyWodPanel() {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FamilyWodOverviewDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var boardId by remember { mutableStateOf<String?>(null) }
    var suggesting by remember { mutableStateOf(false) }
    var parents by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { data = runCatching { session.familyWod() }.getOrNull() }
    val d = data ?: return
    fun act(req: FamilyWodRequest) {
        busy = true
        scope.launch {
            runCatching { session.familyWodAction(req) }.onSuccess { data = it }
            busy = false
        }
    }
    val today = LocalDate.now().toString()
    val next = d.next
    val last = d.past.firstOrNull()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WodLabel("Family WOD")
            if (next != null) {
                Text(wodWhen(next.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(next.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.who, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                next.movements.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (next.mine == null) {
                    Button(onClick = { act(FamilyWodRequest(join = next.id, on = true)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Join") }
                } else if (next.mine == "JOINED" && next.logged == 0) {
                    TextButton(onClick = { act(FamilyWodRequest(join = next.id, on = false)) }, enabled = !busy) { Text("Leave") }
                }
            } else if (last == null) {
                Text("None set.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next != null || last != null) {
                    AccentOutlinedButton(
                        onClick = { boardId = (if (next != null && next.date <= today) next else last ?: next)?.id },
                        modifier = Modifier.weight(1f),
                    ) { Text("Board") }
                }
                AccentOutlinedButton(onClick = { suggesting = true }, modifier = Modifier.weight(1f)) { Text("Suggest one") }
            }
            if (last != null && last.board.isNotEmpty()) {
                HorizontalDivider()
                Text(wodDay(last.date) + " · " + last.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FamilyBoardRows(rows = last.board, limit = 3)
            }
            if (d.parent) {
                HorizontalDivider()
                val n = d.suggestions.size
                Row(
                    Modifier.fillMaxWidth().clickable { parents = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (n > 0) "$n ${if (n == 1) "suggestion" else "suggestions"} to look at" else "Set a family WOD",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                d.suggestions.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "You suggested " + s.name + " for " + wodDay(s.date) + " · waiting",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { act(FamilyWodRequest(withdraw = s.id)) }, enabled = !busy) { Text("Take back") }
                    }
                }
            }
        }
    }

    boardId?.let { id ->
        FamilyBoardDialog(data = d, id = id, onPick = { boardId = it }, onDismiss = { boardId = null })
    }
    if (suggesting) {
        FamilySuggestDialog(
            fixedDate = null,
            onDone = {
                suggesting = false
                scope.launch { data = runCatching { session.familyWod() }.getOrNull() ?: data }
            },
            onDismiss = { suggesting = false },
        )
    }
    if (parents) {
        FamilyParentsDialog(
            data = d,
            onData = { data = it },
            onDismiss = { parents = false },
        )
    }
}

@Composable
private fun FamilyBoardDialog(data: FamilyWodOverviewDto, id: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now().toString()
    val all = data.ahead.filter { it.date <= today } + data.past
    val wod = all.firstOrNull { it.id == id } ?: data.ahead.firstOrNull { it.id == id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Family WOD") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wod != null) {
                    Text(wodDay(wod.date) + " · " + wod.name + " · " + wod.typeLabel, fontWeight = FontWeight.SemiBold)
                    if (wod.board.isNotEmpty()) FamilyBoardRows(rows = wod.board) else Text("No results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wod.waiting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val others = all.filter { it.id != id }
                if (others.isNotEmpty()) {
                    WodLabel("Past WODs")
                    others.forEach { w ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(w.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    wodDay(w.date) + " · " + w.logged + " of " + w.members.size + " logged",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            w.board.firstOrNull()?.let {
                                Text(it.name + " " + it.result, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A day from today to 60 days on, a day at a time. */
@Composable
private fun DayStepper(date: String, onChange: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(d.minusDays(1).toString()) }, enabled = d.isAfter(today)) { Text("‹") }
        Text(wodDay(d.toString()), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange(d.plusDays(1).toString()) }, enabled = d.isBefore(today.plusDays(60))) { Text("›") }
    }
}

/** A searchable list of the shared workouts. */
@Composable
private fun WodPickList(choices: List<FamilyWodChoiceDto>?, picked: String?, onPick: (FamilyWodChoiceDto) -> Unit) {
    var q by remember { mutableStateOf("") }
    if (choices == null) {
        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Search workouts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val t = q.trim().lowercase()
    val list = choices.filter { t.isEmpty() || it.name.lowercase().contains(t) }
    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        list.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (picked == c.hiitWorkoutId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onPick(c) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.typeLabel + (c.minutes?.let { " · $it min" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (list.isEmpty()) Text("No match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Anyone suggests a workout for a day (fixed from a day's + Additional). */
@Composable
internal fun FamilySuggestDialog(fixedDate: String?, onDone: () -> Unit, onDismiss: () -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<FamilyWodChoiceDto>?>(null) }
    var date by remember { mutableStateOf(fixedDate ?: LocalDate.now().toString()) }
    var pick by remember { mutableStateOf<FamilyWodChoiceDto?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { choices = runCatching { session.familyWod(choices = true).choices }.getOrNull() ?: emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggest a family WOD") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fixedDate != null) Text(wodDay(date), fontWeight = FontWeight.SemiBold) else DayStepper(date = date, onChange = { date = it })
                WodPickList(choices = choices, picked = pick?.hiitWorkoutId, onPick = { pick = it })
                OutlinedTextField(value = note, onValueChange = { note = it.take(140) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pick != null && !busy,
                onClick = {
                    val p = pick ?: return@TextButton
                    busy = true
                    scope.launch {
                        runCatching {
                            session.familyWodAction(
                                FamilyWodRequest(suggest = FamilyWodSuggestBody(date = date, hiitWorkoutId = p.hiitWorkoutId, note = note.ifBlank { null })),
                            )
                        }.onSuccess { onDone() }.onFailure { error = it.message ?: "Couldn’t send it." }
                        busy = false
                    }
                },
            ) { Text("Suggest") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PersonChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A parent sets (or edits) a day's family WOD: the day, the workout, who's in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FamilySetDialog(
    people: List<FamilyWodPersonDto>,
    fixedDate: String?,
    wod: FamilyWodDto?,
    suggestion: FamilyWodSuggestionDto?,
    onData: (FamilyWodOverviewDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<FamilyWodChoiceDto>?>(null) }
    var date by remember { mutableStateOf(wod?.date ?: suggestion?.date ?: fixedDate ?: LocalDate.now().toString()) }
    var pickId by remember { mutableStateOf(wod?.hiitWorkoutId ?: suggestion?.hiitWorkoutId) }
    var pickName by remember { mutableStateOf(wod?.name ?: suggestion?.name) }
    var changing by remember { mutableStateOf(pickId == null) }
    var who by remember {
        mutableStateOf(wod?.members?.filter { it.how == "ASSIGNED" }?.map { it.userId }?.toSet() ?: people.map { it.userId }.toSet())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(changing) {
        if (changing && choices == null) choices = runCatching { session.familyWod(choices = true).choices }.getOrNull() ?: emptyList()
    }
    fun send(req: FamilyWodRequest) {
        busy = true
        scope.launch {
            runCatching { session.familyWodAction(req) }
                .onSuccess {
                    onData(it)
                    onDismiss()
                }
                .onFailure { error = it.message ?: "Couldn’t save it." }
            busy = false
        }
    }
    val everyone = people.all { it.userId in who }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wod != null) "Edit family WOD" else "New family WOD") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wod != null || fixedDate != null) Text(wodDay(date), fontWeight = FontWeight.SemiBold) else DayStepper(date = date, onChange = { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pickName ?: "Pick a workout", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (pickId != null && !changing) TextButton(onClick = { changing = true }) { Text("Change") }
                }
                if (changing) {
                    WodPickList(
                        choices = choices,
                        picked = pickId,
                        onPick = {
                            pickId = it.hiitWorkoutId
                            pickName = it.name
                            changing = false
                        },
                    )
                }
                Text("Who’s in", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonChip(label = "Everyone", on = everyone) { who = if (everyone) emptySet() else people.map { it.userId }.toSet() }
                    people.forEach { p ->
                        PersonChip(label = p.name, on = p.userId in who) { who = if (p.userId in who) who - p.userId else who + p.userId }
                    }
                }
                if (wod != null) {
                    TextButton(onClick = { send(FamilyWodRequest(remove = wod.id)) }, enabled = !busy) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pickId != null && who.isNotEmpty() && !busy,
                onClick = {
                    val id = pickId ?: return@TextButton
                    send(
                        FamilyWodRequest(
                            set = FamilyWodSetBody(date = date, hiitWorkoutId = id, users = who.toList(), suggestionId = suggestion?.id),
                        ),
                    )
                },
            ) { Text(if (suggestion != null) "Approve" else "Set family WOD") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FamilyParentsDialog(data: FamilyWodOverviewDto, onData: (FamilyWodOverviewDto) -> Unit, onDismiss: () -> Unit) {
    val session = rememberContainer().sessionRepository
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<FamilyWodDto?>(null) }
    var approving by remember { mutableStateOf<FamilyWodSuggestionDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Family WOD") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("New family WOD") }
                if (data.ahead.isNotEmpty()) {
                    WodLabel("Coming up")
                    data.ahead.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(wodWhen(w.date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(w.name + " · " + w.who, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { editing = w }) { Text("Edit") }
                        }
                    }
                }
                if (data.suggestions.isNotEmpty()) {
                    WodLabel("Suggestions")
                    data.suggestions.forEach { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                s.by + " suggested it for " + wodDay(s.date) + (s.note?.let { " · “$it”" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { approving = s }) { Text("Approve") }
                                AccentOutlinedButton(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            runCatching { session.familyWodAction(FamilyWodRequest(decline = s.id)) }.onSuccess { onData(it) }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                ) { Text("Decline") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (creating || editing != null || approving != null) {
        FamilySetDialog(
            people = data.people,
            fixedDate = null,
            wod = editing,
            suggestion = approving,
            onData = onData,
            onDismiss = {
                creating = false
                editing = null
                approving = null
            },
        )
    }
}

/** + Additional → Family WOD on a day: a parent sets one, anyone else suggests one. */
@Composable
internal fun FamilyWodForDay(date: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val session = rememberContainer().sessionRepository
    var data by remember { mutableStateOf<FamilyWodOverviewDto?>(null) }
    LaunchedEffect(Unit) { data = runCatching { session.familyWod() }.getOrNull() }
    val d = data ?: return
    if (d.parent) {
        FamilySetDialog(people = d.people, fixedDate = date, wod = null, suggestion = null, onData = { onDone() }, onDismiss = onDismiss)
    } else {
        FamilySuggestDialog(fixedDate = date, onDone = onDone, onDismiss = onDismiss)
    }
}
