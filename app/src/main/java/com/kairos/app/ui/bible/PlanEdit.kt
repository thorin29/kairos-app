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

/** "Add to: Family plan / My plan" — only parents get the choice. */
@Composable
private fun ScopeSwitch(isAdmin: Boolean, hasMine: Boolean, toFamily: Boolean, onChange: (Boolean) -> Unit) {
    if (!isAdmin || !hasMine) return
    Segmented(options = listOf("Family plan", "My plan"), selected = if (toFamily) 0 else 1) { onChange(it == 0) }
}

/**
 * Add readings (mirrors the web form): pick chapters — each tap is a day, a
 * long press puts it on the last reading's day — or type passages, then choose
 * how they fit. The preview comes from the server, which saves with the same
 * rule.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AddReadingsPage(
    vm: BibleViewModel,
    isAdmin: Boolean,
    hasMine: Boolean,
    today: String,
    startFamily: Boolean,
    familyEndsWhenDone: Boolean,
    mineEndsWhenDone: Boolean,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toFamily by remember { mutableStateOf(startFamily || !hasMine) }
    var tab by remember { mutableStateOf(0) }
    var book by remember { mutableStateOf("Luke") }
    val items = remember { mutableStateListOf<ReadingItem>() }
    var nextId by remember { mutableStateOf(1) }
    var typed by remember { mutableStateOf("") }
    var startISO by remember { mutableStateOf(today) }
    var mode by remember { mutableStateOf(0) }
    var ownDays by remember { mutableStateOf(1) }
    var preview by remember { mutableStateOf<AddReadingsDto?>(null) }
    var working by remember { mutableStateOf(false) }

    // A plan that ends when its readings are done always pushes back.
    val pushOnly = if (toFamily) familyEndsWhenDone else mineEndsWhenDone
    fun changed() { preview = null }
    fun request(save: Boolean) = AddReadingsRequest(
        scope = if (toFamily) "family" else "mine",
        readings = items.map { it.label },
        startISO = startISO,
        mode = if (pushOnly) "push" else listOf("push", "add", "mix")[mode],
        ownDays = ownDays,
        preview = !save,
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScopeSwitch(isAdmin = isAdmin, hasMine = hasMine, toFamily = toFamily) { toFamily = it; changed() }
        Segmented(options = listOf("Pick chapters", "Type passages"), selected = tab) { tab = it }

        if (tab == 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BIBLE_BOOKS.forEach { b ->
                    val on = b.name == book
                    Text(
                        b.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { book = b.name }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            val chapters = BIBLE_BOOKS.firstOrNull { it.name == book }?.chapters ?: 0
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 1..chapters) {
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    items.add(ReadingItem(id = nextId++, refs = listOf(book to c)))
                                    changed()
                                },
                                onLongClick = {
                                    val last = items.lastOrNull()
                                    if (last != null && last.text == null) {
                                        items[items.size - 1] = last.copy(refs = last.refs + (book to c))
                                    } else {
                                        items.add(ReadingItem(id = nextId++, refs = listOf(book to c)))
                                    }
                                    changed()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text("$c", style = MaterialTheme.typography.bodyMedium) }
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
                changed()
            }) { Text("Add to the list") }
        }

        if (items.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).clickable { items.remove(item); changed() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(KairosIcons.Close, contentDescription = "Remove ${item.label}", modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }

        DateField("Starting", startISO) { startISO = it; changed() }
        if (!pushOnly) {
            Segmented(options = listOf("Push back", "Add to days", "Some of each"), selected = mode) { mode = it; changed() }
        }
        if (!pushOnly && mode == 2 && items.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Move the end by", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { ownDays = (ownDays - 1).coerceAtLeast(0); changed() }) { Text("−") }
                Text("$ownDays", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { ownDays = (ownDays + 1).coerceAtMost(items.size); changed() }) { Text("+") }
            }
        }

        preview?.let { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preview", fontWeight = FontWeight.SemiBold)
                p.endISO?.let { end ->
                    val was = p.oldEndISO?.takeIf { it != end }?.let { " (was ${formatShortISO(it)})" } ?: ""
                    Text("Ends ${formatShortISO(end)}$was", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                p.rows.forEach { d ->
                    val isNew = d.tag == "new" || d.tag == "added"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(formatShortISO(d.iso), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                        Text(d.passage, fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                d.tag == "new" -> "new"
                                d.tag == "added" -> "added"
                                d.fromISO != null -> "was ${formatShortISO(d.fromISO)}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    working = true
                    scope.launch { preview = vm.addReadings(request(save = false)); working = false }
                },
                enabled = items.isNotEmpty() && !working,
                modifier = Modifier.weight(1f),
            ) { Text("Preview") }
            Button(
                onClick = {
                    working = true
                    scope.launch {
                        val res = vm.addReadings(request(save = true))
                        working = false
                        if (res != null) onDone()
                    }
                },
                enabled = preview != null && !working,
                modifier = Modifier.weight(1f),
            ) { Text("Add ${items.size} ${if (items.size == 1) "reading" else "readings"}") }
        }
    }
}

private val DATED_LINE = Regex("""^\s*(\d{4}-\d{1,2}-\d{1,2}|\d{1,2}/\d{1,2}/\d{4})\s*[,\t]""")
private val WEEKDAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")

/** Import a plan from pasted text or a file: dated lines keep their dates,
 *  passages alone are laid out from a start date on the chosen weekdays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportPlanPage(vm: BibleViewModel, isAdmin: Boolean, today: String, startFamily: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var toFamily by remember { mutableStateOf(startFamily && isAdmin) }
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
        scope = if (toFamily) "family" else "mine",
        text = text,
        startISO = if (dated) null else startISO,
        weekdays = if (dated) null else weekdays.sorted(),
        preview = !save,
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isAdmin) {
            Segmented(options = listOf("Family plan", "My plan"), selected = if (toFamily) 0 else 1) { toFamily = it == 0; preview = null }
        }
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
