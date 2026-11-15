package com.kairos.app.ui.bible

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AddReadingsDto
import com.kairos.app.data.remote.dto.AddReadingsRequest
import com.kairos.app.data.remote.dto.ImportPlanDto
import com.kairos.app.data.remote.dto.ImportPlanRequest
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/** One reading to add: picked chapters (joined into a label) or typed text. */
private data class ReadingItem(val id: Int, val refs: List<Pair<String, Int>> = emptyList(), val text: String? = null) {
    val label: String get() = text ?: joinChapters(refs)
}

/** "Luke 2-3; Acts 1" — consecutive chapters of a book join up (web joinChapters). */
internal fun joinChapters(refs: List<Pair<String, Int>>): String {
    val parts = mutableListOf<String>()
    var i = 0
    while (i < refs.size) {
        val (book, ch) = refs[i]
        var end = ch
        while (i + 1 < refs.size && refs[i + 1].first == book && refs[i + 1].second == end + 1) {
            end++
            i++
        }
        parts.add(if (end == ch) "$book $ch" else "$book $ch-$end")
        i++
    }
    return parts.joinToString("; ")
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, accent, RoundedCornerShape(10.dp)),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .background(if (i == selected) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (i == selected) Color.White else accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, iso: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatShortISO(iso), fontWeight = FontWeight.Medium)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(iso))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) }; open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** A choice card: radio, title, lines of explanation. */
@Composable
internal fun ChoiceCard(title: String, lines: List<String>, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            lines.filter { it.isNotEmpty() }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("What", "How", "Review").forEachIndexed { i, label ->
            val on = i == step
            val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(20.dp).border(1.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = color) }
                Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ListRow(title: String, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(KairosIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

private fun sectionLabel(group: String) = if (group == "General Epistles") "General letters" else group

/**
 * Add readings in three steps (mirrors the web form): what — pick chapters a
 * level at a time (testament, section, book, chapters; a long press puts a
 * chapter on the day before) or type passages; how — additional readings on
 * the same days, or inserted into the plan; and a review from the server,
 * which saves with the same rule.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AddReadingsPage(
    vm: BibleViewModel,
    isAdmin: Boolean,
    hasMine: Boolean,
    today: String,
    startFamily: Boolean,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toFamily by remember { mutableStateOf(startFamily || !hasMine) }
    var step by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var testament by remember { mutableStateOf<Testament?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var book by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<ReadingItem>() }
    var nextId by remember { mutableStateOf(1) }
    var typed by remember { mutableStateOf("") }
    var startISO by remember { mutableStateOf(today) }
    var insert by remember { mutableStateOf(false) }
    var insertPreview by remember { mutableStateOf<AddReadingsDto?>(null) }
    var preview by remember { mutableStateOf<AddReadingsDto?>(null) }
    var working by remember { mutableStateOf(false) }

    fun request(mode: String, save: Boolean) = AddReadingsRequest(
        scope = if (toFamily) "family" else "mine",
        readings = items.map { it.label },
        startISO = startISO,
        mode = mode,
        preview = !save,
    )
    fun refreshInsert() {
        insertPreview = null
        if (items.isEmpty()) return
        scope.launch { insertPreview = vm.addReadings(request(mode = "push", save = false)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader(step)

        when (step) {
            0 -> {
                if (isAdmin && hasMine) {
                    Segmented(options = listOf("Family plan", "My plan"), selected = if (toFamily) 0 else 1) { toFamily = it == 0 }
                }
                Segmented(options = listOf("Pick chapters", "Type passages"), selected = tab) { tab = it }
                if (tab == 0) {
                    // Breadcrumb: tap a level to go back to it.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Books", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { testament = null; group = null; book = null })
                        testament?.let { t ->
                            Text("\u203a ${if (t == Testament.OT) "Old Testament" else "New Testament"}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { group = null; book = null })
                        }
                        group?.let { g ->
                            Text("\u203a ${sectionLabel(g)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { book = null })
                        }
                        book?.let { Text("\u203a $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                    }
                    when {
                        testament == null -> Column {
                            Testament.entries.forEach { t ->
                                ListRow(
                                    title = if (t == Testament.OT) "Old Testament" else "New Testament",
                                    sub = "${BIBLE_BOOKS.count { it.testament == t }} books",
                                ) { testament = t }
                            }
                        }
                        group == null -> Column {
                            BIBLE_GROUPS.filter { g -> BIBLE_BOOKS.firstOrNull { it.group == g }?.testament == testament }.forEach { g ->
                                val inGroup = BIBLE_BOOKS.filter { it.group == g }
                                ListRow(
                                    title = sectionLabel(g),
                                    sub = if (inGroup.size > 1) "${inGroup.first().name} \u2013 ${inGroup.last().name}" else null,
                                ) { group = g }
                            }
                        }
                        book == null -> Column {
                            BIBLE_BOOKS.filter { it.group == group }.forEach { b -> ListRow(title = b.name) { book = b.name } }
                        }
                        else -> {
                            val name = book!!
                            val chapters = BIBLE_BOOKS.firstOrNull { it.name == name }?.chapters ?: 0
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 1..chapters) {
                                    val picked = items.any { it.refs.any { r -> r.first == name && r.second == c } }
                                    Box(
                                        Modifier
                                            .size(width = 44.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (picked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .border(1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = { items.add(ReadingItem(id = nextId++, refs = listOf(name to c))) },
                                                onLongClick = {
                                                    val last = items.lastOrNull()
                                                    if (last != null && last.text == null) {
                                                        items[items.size - 1] = last.copy(refs = last.refs + (name to c))
                                                    } else {
                                                        items.add(ReadingItem(id = nextId++, refs = listOf(name to c)))
                                                    }
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("$c", color = if (picked) Color.White else MaterialTheme.colorScheme.onSurface) }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Passages, one per day") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = {
                        typed.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            items.add(ReadingItem(id = nextId++, text = it))
                        }
                        typed = ""
                    }) { Text("Add to the list") }
                }

                Text("READINGS TO ADD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                if (items.isEmpty()) {
                    Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Day ${i + 1} \u00b7 ${item.label}", modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable { items.remove(item) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(KairosIcons.Close, contentDescription = "Remove ${item.label}", modifier = Modifier.size(14.dp)) }
                    }
                }
                Text(
                    "Each chapter is a day. Long-press to add it to the day before.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            1 -> {
                DateField("Starting", startISO) { startISO = it; refreshInsert() }
                Text("${items.size} ${if (items.size == 1) "reading" else "readings"} \u00b7 starting ${formatShortISO(startISO)}")
                ChoiceCard(
                    title = "Additional readings",
                    lines = listOf(
                        "Additional passages on the same days as your current reading plan.",
                        "End date of the overall plan does not change.",
                    ),
                    selected = !insert,
                ) { insert = false }
                val p = insertPreview
                ChoiceCard(
                    title = "Insert into the plan",
                    lines = listOf(
                        "Shifts the end date of the plan.",
                        "Readings after ${formatShortISO(startISO)} move ${items.size} ${if (items.size == 1) "day" else "days"} later.",
                        if (p?.endISO != null && p.oldEndISO != null) "Plan will end ${formatShortISO(p.endISO)} instead of ${formatShortISO(p.oldEndISO)}." else "",
                    ),
                    selected = insert,
                ) { insert = true }
            }
            else -> {
                val p = preview
                if (p == null) {
                    Text("Working\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        if (insert) "Inserted \u00b7 plan ends ${p.endISO?.let { formatShortISO(it) } ?: ""}"
                        else "Additional readings \u00b7 plan still ends ${p.oldEndISO?.let { formatShortISO(it) } ?: ""}",
                    )
                    p.rows.take(30).forEach { d ->
                        val isNew = d.tag == "new" || d.tag == "added"
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(formatShortISO(d.iso), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                            Text(
                                d.passage,
                                fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            d.fromISO?.let {
                                Text("was ${formatShortISO(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (step == 0) onDone() else step -= 1 },
                modifier = Modifier.weight(1f),
            ) { Text(if (step == 0) "Cancel" else "Back") }
            Button(
                onClick = {
                    when (step) {
                        0 -> { step = 1; refreshInsert() }
                        1 -> {
                            step = 2
                            preview = null
                            scope.launch { preview = vm.addReadings(request(mode = if (insert) "push" else "add", save = false)) }
                        }
                        else -> {
                            working = true
                            scope.launch {
                                val res = vm.addReadings(request(mode = if (insert) "push" else "add", save = true))
                                working = false
                                if (res != null) onDone()
                            }
                        }
                    }
                },
                enabled = items.isNotEmpty() && !working && (step < 2 || preview != null),
                modifier = Modifier.weight(1f),
            ) { Text(if (step < 2) "Next" else "Add ${items.size} ${if (items.size == 1) "reading" else "readings"}") }
        }
    }
}

private val DATED_LINE = Regex("""^\s*(\d{4}-\d{1,2}-\d{1,2}|\d{1,2}/\d{1,2}/\d{4})\s*[,\t]""")
private val WEEKDAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")

/** Import a plan from pasted text or a file: dated lines keep their dates,
 *  passages alone are laid out from a start date on the chosen weekdays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportPlanPage(vm: BibleViewModel, today: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var startISO by remember { mutableStateOf(today) }
    val weekdays = remember { mutableStateListOf(0, 1, 2, 3, 4, 5, 6) }
    var preview by remember { mutableStateOf<ImportPlanDto?>(null) }
    var working by remember { mutableStateOf(false) }
    val dated = text.lines().any { DATED_LINE.containsMatchIn(it) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()?.let { text = it; preview = null }
        }
    }

    fun request(save: Boolean) = ImportPlanRequest(
        scope = "mine",
        text = text,
        startISO = if (dated) null else startISO,
        weekdays = if (dated) null else weekdays.sorted(),
        preview = !save,
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; preview = null },
            label = { Text("Plan") },
            placeholder = { Text("2026-12-01, John 1\n2026-12-02, John 2-3") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { pickFile.launch(arrayOf("text/*")) }) {
            Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Upload a file")
        }

        if (text.isNotBlank() && !dated) {
            DateField("Starting", startISO) { startISO = it; preview = null }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEKDAY_LETTERS.forEachIndexed { i, l ->
                    val on = i in weekdays
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { if (on) weekdays.remove(i) else weekdays.add(i); preview = null },
                        contentAlignment = Alignment.Center,
                    ) { Text(l, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        preview?.let { p ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(p.name, "${p.count} days", p.firstISO?.let { f -> "${formatShortISO(f)} – ${formatShortISO(p.lastISO ?: f)}" })
                        .joinToString(" · "),
                    fontWeight = FontWeight.SemiBold,
                )
                p.days.take(20).forEach { d ->
                    Text("${formatShortISO(d.iso)} · ${d.passage}", style = MaterialTheme.typography.bodySmall)
                }
                if (p.count > 20) Text("…", style = MaterialTheme.typography.bodySmall)
                if (p.skipped.isNotEmpty()) {
                    Text(
                        "${p.skipped.size} skipped: ${p.skipped.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    working = true
                    scope.launch { preview = vm.importPlan(request(save = false)); working = false }
                },
                enabled = text.isNotBlank() && !working,
                modifier = Modifier.weight(1f),
            ) { Text("Preview") }
            Button(
                onClick = {
                    working = true
                    scope.launch {
                        val res = vm.importPlan(request(save = true))
                        working = false
                        if (res != null) onDone()
                    }
                },
                enabled = preview != null && !working,
                modifier = Modifier.weight(1f),
            ) { Text("Import") }
        }
        Box(Modifier.height(4.dp))
    }
}

/**
 * Start over: keep the plan (progress back to 0%), or — for a personal plan —
 * end it and start a new one. Either way finished books and milestones stay.
 */
@Composable
fun StartOverPage(allowNewPlan: Boolean, busy: Boolean, onCancel: () -> Unit, onConfirm: (endPlan: Boolean) -> Unit) {
    var endPlan by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("How do you want to start over?")
        ChoiceCard(
            title = "Keep my current plan",
            lines = listOf("Progress bars go back to 0%.", "Your plan resets starting with today\u2019s reading."),
            selected = !endPlan,
        ) { endPlan = false }
        if (allowNewPlan) {
            ChoiceCard(
                title = "End this plan and start a new one",
                lines = listOf("Your current plan ends today.", "Progress resets to zero, and the plan builder will open to create a new plan."),
                selected = endPlan,
            ) { endPlan = true }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("NOTE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("\u2022  Books you\u2019ve already finished stay counted. If you\u2019ve read Romans twice, it still shows \u00d72.", style = MaterialTheme.typography.bodySmall)
            Text("\u2022  Your milestones stay, and \u201cStarted over\u201d is added.", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onConfirm(endPlan) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Start over") }
        }
    }
}
