package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.CurrentExtrasDto
import com.kairos.app.data.remote.dto.CurrentSetupDto
import com.kairos.app.data.remote.dto.ProgramChoiceDto
import com.kairos.app.data.remote.dto.ProgramOptionDto
import com.kairos.app.data.remote.dto.ProgramRoleDto
import com.kairos.app.data.remote.dto.StartProgramRequest
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.RollPicker

private val BUILDER_STEPS = listOf("Days", "Blocks", "Movements", "Extras", "Review")
private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Monday first. */
private val WEEK_ORDER = listOf(1, 2, 3, 4, 5, 6, 0)
private const val REST = -1
private const val CORE = -2

/** How a finisher is logged, from its sets. */
private fun finisherSub(scheme: String?): String = when {
    scheme.isNullOrBlank() -> ""
    scheme == "carry" -> "Logged in lengths"
    scheme == "clubs" -> "Two hands, then one"
    scheme.startsWith("hold:") -> "Logged in seconds"
    else -> "Logged in reps"
}

/**
 * The block builder (server 0.732), for a program set up by blocks (Odd
 * squad), the web's BlockBuilder: a muscle group for each weekday (Core and
 * Rest too), each day's blocks (A main lift, B density pair, C finisher; B and
 * C can come off), each block's movements from the day's own short list, then
 * a review with the weight for 5 reps on each main lift. Opened on the running
 * program it edits it in place; picked again after it ends, it starts over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockBuilder(
    program: ProgramChoiceDto,
    current: CurrentExtrasDto,
    /** Warm-up routines for the Extras step (v0.540). */
    warmups: List<com.kairos.app.data.remote.dto.WarmupRoutineDto> = emptyList(),
    busy: Boolean,
    error: String?,
    initial: CurrentSetupDto? = null,
    nextMaxes: Map<String, Double>? = null,
    /** The garage's length for a shuttle carry; null when it can't be changed here (v0.530). */
    garageFeet: Int? = null,
    onGarage: ((Int) -> Unit)? = null,
    onStart: (StartProgramRequest) -> Unit,
) {
    var step by remember(program.key) { mutableStateOf(0) }
    // Extras (v0.540), as the other programs: each can be left out.
    var warmup by remember(program.key) { mutableStateOf(if (current.warmup) current.warmupRoutine ?: "" else "none") }
    var cardioDays by remember(program.key) { mutableStateOf(current.cardioDays) }
    var conditioningDays by remember(program.key) { mutableStateOf(current.conditioningDays) }
    // Weekday → a program day's index, CORE or REST.
    val week = remember(program.key) {
        mutableStateListOf<Int>().apply {
            repeat(7) { add(REST) }
            val days = initial?.days?.takeIf { it.size == program.days.size } ?: program.defaultDays
            days.forEachIndexed { i, dow -> if (dow in 0..6) this[dow] = i }
            current.coreDay?.let { if (it in 0..6 && this[it] == REST) this[it] = CORE }
        }
    }
    val picks = remember(program.key) {
        mutableStateMapOf<String, String?>().apply {
            if (initial != null) putAll(initial.picks)
            else program.days.forEachIndexed { i, d -> d.roles.forEach { r -> put("$i:${r.key}", r.defaultId) } }
        }
    }
    val off = remember(program.key) { mutableStateListOf<String>().apply { addAll(initial?.removed.orEmpty()) } }
    val maxText = remember(program.key) { mutableStateMapOf<String, String>() }
    var dayTab by remember(program.key) { mutableStateOf(0) }
    var choosingDay by remember { mutableStateOf<Int?>(null) }

    val placed = WEEK_ORDER.filter { week[it] >= 0 }.map { it to week[it] }
    val allPlaced = program.days.indices.all { it in week }
    val tab = placed.getOrNull(dayTab.coerceAtMost((placed.size - 1).coerceAtLeast(0)))?.second ?: 0

    fun set(dow: Int, v: Int) {
        for (d in 0..6) if (d != dow && v != REST && week[d] == v) week[d] = REST
        week[dow] = v
    }
    fun roles(i: Int, block: String): List<ProgramRoleDto> = program.days[i].roles.filter { it.block == block }
    fun blockOff(i: Int, block: String) = roles(i, block).all { "$i:${it.key}" in off }
    fun toggleBlock(i: Int, block: String) {
        val keys = roles(i, block).map { "$i:${it.key}" }
        if (keys.all { it in off }) off.removeAll(keys) else keys.forEach { if (it !in off) off.add(it) }
    }
    fun nameOf(i: Int, key: String): String? {
        val r = program.days[i].roles.firstOrNull { it.key == key } ?: return null
        val id = picks["$i:$key"]
        return r.options.firstOrNull { it.id == id }?.name
    }
    fun dayName(v: Int) = when (v) {
        CORE -> "Core"
        REST -> "Rest"
        else -> program.days.getOrNull(v)?.name ?: ""
    }
    // Main lifts with a weight (a bodyweight pull-up has none to set).
    val mains = program.days.flatMapIndexed { i, d ->
        d.roles.filter { it.main && "$i:${it.key}" !in off }.mapNotNull { r ->
            val id = picks["$i:${r.key}"] ?: return@mapNotNull null
            r.options.firstOrNull { it.id == id }?.takeIf { !Regex("^(pull-up|chin-up)$", RegexOption.IGNORE_CASE).matches(it.name) }
                ?.let { id to it.name }
        }
    }.distinctBy { it.first }
    fun maxFor(id: String): String = maxText[id] ?: nextMaxes?.get(id)?.let { MovementSetsViewModel.fmt(it) } ?: ""

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SetupStepHeader(steps = BUILDER_STEPS, step = step)

            val dayTabs: @Composable () -> Unit = {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    placed.forEachIndexed { k, (_, i) ->
                        BuilderChip(label = program.days[i].name, on = tab == i, onClick = { dayTab = k })
                    }
                }
            }

            when (step) {
                0 -> BuilderCard(title = "Which muscle group each day") {
                    WEEK_ORDER.forEachIndexed { n, dow ->
                        if (n > 0) HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { choosingDay = dow }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(DAY_NAMES[dow], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                dayName(week[dow]),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                1 -> {
                    dayTabs()
                    BuilderCard(title = "${program.days[tab].name} day") {
                        listOf("A", "B", "C").forEachIndexed { n, b ->
                            if (n > 0) HorizontalDivider()
                            val isOff = b != "A" && blockOff(tab, b)
                            val label = when (b) {
                                "A" -> "A · Main lift"
                                "B" -> "B · Density pair"
                                else -> "C · Finisher"
                            }
                            val sub = when (b) {
                                "A" -> "Ladders · weight up when every ladder is done"
                                "B" -> "15 min · beat your rep total"
                                else -> roles(tab, "C").firstOrNull()?.let { nameOf(tab, it.key) } ?: ""
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isOff) FontWeight.Normal else FontWeight.SemiBold,
                                        color = if (isOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isOff) TextDecoration.LineThrough else null,
                                    )
                                    if (!isOff && sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (b != "A") {
                                    Text(
                                        if (isOff) "Add back" else "Remove",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { toggleBlock(tab, b) }.padding(6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    dayTabs()
                    roles(tab, "A").forEach { r ->
                        val k = "$tab:${r.key}"
                        val bw = r.options.firstOrNull { it.name.equals("Pull-up", ignoreCase = true) }
                        val wt = r.options.firstOrNull { it.name.equals("Weighted pull-up", ignoreCase = true) }
                        if (bw != null && wt != null) {
                            BuilderCard(title = "Pull-ups") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    listOf(bw to "Bodyweight", wt to "Weighted").forEach { (o, label) ->
                                        val on = picks[k] == o.id
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { picks[k] = o.id },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            BuilderCard(title = "A · Main lift · ${program.days[tab].name.lowercase()}") {
                                r.options.forEachIndexed { n, o ->
                                    if (n > 0) HorizontalDivider()
                                    BuilderRadio(option = o, on = picks[k] == o.id, sub = null, onPick = { picks[k] = o.id })
                                }
                            }
                        }
                    }
                    if (!blockOff(tab, "B")) {
                        val pair = roles(tab, "B")
                        if (pair.size >= 2) {
                            val k1 = "$tab:${pair[0].key}"
                            val k2 = "$tab:${pair[1].key}"
                            val chosen = listOfNotNull(picks[k1], picks[k2])
                            BuilderCard(title = "B · Density pair · pick two") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pair[0].options.forEach { o ->
                                        BuilderChip(label = o.name, on = o.id in chosen, onClick = {
                                            when {
                                                o.id in chosen -> {
                                                    if (picks[k1] == o.id) picks[k1] = null else picks[k2] = null
                                                }
                                                picks[k1] == null -> {
                                                    picks[k1] = o.id
                                                }
                                                picks[k2] == null -> {
                                                    picks[k2] = o.id
                                                }
                                                // Two already: the newer stays, this one joins it.
                                                else -> {
                                                    picks[k1] = picks[k2]
                                                    picks[k2] = o.id
                                                }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    if (!blockOff(tab, "C")) {
                        roles(tab, "C").forEach { r ->
                            val k = "$tab:${r.key}"
                            BuilderCard(title = "C · Finisher · ${program.days[tab].name.lowercase()} day") {
                                r.options.forEachIndexed { n, o ->
                                    if (n > 0) HorizontalDivider()
                                    BuilderRadio(
                                        option = o,
                                        on = picks[k] == o.id,
                                        sub = finisherSub(r.schemes[o.id] ?: r.scheme),
                                        onPick = { picks[k] = o.id },
                                    )
                                }
                                // The garage's length, for a shuttle carry's lengths (v0.530).
                                if (garageFeet != null && onGarage != null) {
                                    HorizontalDivider()
                                    RollPicker(
                                        label = "Garage length",
                                        selectedLabel = "$garageFeet ft",
                                        options = (10..60).map { "$it" to "$it ft" },
                                        onSelect = { v -> v.toIntOrNull()?.let(onGarage) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> ExtrasStep(
                    warmups = warmups,
                    warmup = warmup,
                    onWarmup = { warmup = it },
                    cardioDays = cardioDays,
                    onCardio = { cardioDays = it },
                    conditioningDays = conditioningDays,
                    onConditioning = { conditioningDays = it },
                )
                else -> {
                    BuilderCard(title = "${program.name} · ${program.weeks} weeks") {
                        var first = true
                        WEEK_ORDER.forEach { dow ->
                            val v = week[dow]
                            if (v == REST) return@forEach
                            if (!first) HorizontalDivider()
                            first = false
                            val body = if (v == CORE) {
                                "Your core rotation · test stays monthly"
                            } else {
                                buildList {
                                    roles(v, "A").firstOrNull()?.let { add(nameOf(v, it.key) ?: "Main lift") }
                                    if (!blockOff(v, "B")) {
                                        val pair = roles(v, "B").mapNotNull { nameOf(v, it.key) }
                                        if (pair.isNotEmpty()) add(pair.joinToString(" + "))
                                    }
                                    roles(v, "C").firstOrNull()?.takeIf { !blockOff(v, "C") }?.let { add(nameOf(v, it.key) ?: "Finisher") }
                                }.joinToString(" · ")
                            }
                            Column(Modifier.padding(vertical = 9.dp)) {
                                Text("${DAY_NAMES[dow]} · ${dayName(v)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (mains.isNotEmpty()) {
                        BuilderCard(title = "Weight for 5 reps") {
                            mains.forEachIndexed { n, (id, name) ->
                                if (n > 0) HorizontalDivider()
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = maxFor(id),
                                        onValueChange = { maxText[id] = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(96.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) ProgramPill(label = "Back", modifier = Modifier.weight(1f), onClick = { step -= 1 })
            if (step < BUILDER_STEPS.size - 1) {
                ProgramPill(
                    label = "Next",
                    filled = true,
                    enabled = step != 0 || allPlaced,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        dayTab = 0
                        step += 1
                    },
                )
            } else {
                ProgramPill(
                    label = if (busy) "Starting…" else if (initial != null) "Save changes" else "Start program",
                    filled = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val core = week.indexOf(CORE)
                        onStart(
                            StartProgramRequest(
                                key = program.key,
                                days = program.days.indices.map { week.indexOf(it) },
                                picks = picks.mapValues { (k, v) -> if (k in off) null else v },
                                tops = emptyMap(),
                                maxes = mains.mapNotNull { (id, _) -> maxFor(id).toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it } }.toMap(),
                                warmup = warmup != "none",
                                warmupRoutine = if (warmup != "none") warmup else null,
                                cardioDays = cardioDays,
                                conditioningDays = conditioningDays,
                                coreDay = core.takeIf { it >= 0 },
                            ),
                        )
                    },
                )
            }
        }
    }

    choosingDay?.let { dow ->
        AnimatedDialog(onDismissRequest = { choosingDay = null }, title = DAY_NAMES[dow]) {
            Column {
                (program.days.indices.toList() + listOf(CORE, REST)).forEach { v ->
                    Text(
                        dayName(v),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (week[dow] == v) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (week[dow] == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                set(dow, v)
                                choosingDay = null
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuilderCard(title: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun BuilderChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BuilderRadio(option: ProgramOptionDto, on: Boolean, sub: String?, onPick: () -> Unit) {
    var info by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable { onPick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .border(2.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Column {
                Text(option.name, style = MaterialTheme.typography.bodyMedium)
                if (!sub.isNullOrBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MovementInfoButton(name = option.name, onClick = { info = true })
    }
    if (info) MovementInfoDialog(name = option.name, about = option.about, onDismiss = { info = false })
}
