package com.kairos.app.ui.settings
import com.kairos.app.ui.nav.KairosIcons

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.data.notifications.NotifySync
import com.kairos.app.data.remote.dto.NotifyOptionsDto
import com.kairos.app.data.remote.dto.NotifyPrefsDto
import com.kairos.app.data.remote.dto.PlaceRuleDto
import com.kairos.app.data.remote.dto.QuietHoursDto
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * Notifications (v0.553; server 0.772+): everything this person is told,
 * set here and saved on the server so their next phone has the same:
 * calendar reminders (their own events / ones others set), task alerts,
 * Family map arrive/leave, safety alerts about the children (parents), and
 * quiet hours. Default reminders and sound stay below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prefs by remember { mutableStateOf<NotifyPrefsDto?>(null) }
    var options by remember { mutableStateOf(NotifyOptionsDto()) }
    var failed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.hasPermission(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        Notifications.ensureChannel(context)
        val res = container.sessionRepository.notifyPrefsFromBackground()
        if (res == null) {
            failed = true
            return@LaunchedEffect
        }
        options = res.options
        if (res.saved) {
            prefs = res.prefs
        } else {
            // First time: start from this phone's own switches.
            val local = container.settingsStore.currentNotifPrefs()
            val seeded = res.prefs.copy(
                calendar = res.prefs.calendar.copy(mine = local.enabled, others = local.enabled),
                tasks = local.tasksEnabled,
            )
            prefs = container.sessionRepository.saveNotifyPrefs(seeded) ?: seeded
        }
    }

    fun update(next: NotifyPrefsDto) {
        prefs = next
        scope.launch {
            val saved = container.sessionRepository.saveNotifyPrefs(next)
            if (saved != null) prefs = saved
            NotifySync.apply(context, saved ?: next)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val p = prefs
            if (p == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (failed) Text("Couldn’t reach Kairos.") else CircularProgressIndicator()
                }
                return@Box
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { SettingsLabel("Calendar") }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        SwitchRow(
                            title = "Reminders on my events",
                            checked = p.calendar.mine,
                            onChange = { on ->
                                if (on) askPermission()
                                update(p.copy(calendar = p.calendar.copy(mine = on)))
                            },
                        )
                        HorizontalDivider()
                        SwitchRow(
                            title = "Reminders others set for me",
                            subtitle = "Family events and events someone else made",
                            checked = p.calendar.others,
                            onChange = { on ->
                                if (on) askPermission()
                                update(p.copy(calendar = p.calendar.copy(others = on)))
                            },
                        )
                    }
                }

                item { SettingsLabel("Tasks") }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        SwitchRow(
                            title = "Task alerts",
                            checked = p.tasks,
                            onChange = { on ->
                                if (on) askPermission()
                                update(p.copy(tasks = on))
                            },
                        )
                    }
                }

                if (options.familyMap) {
                    item { SettingsLabel("Family map") }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            if (p.places.isEmpty()) {
                                Text(
                                    "Nothing yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            p.places.forEachIndexed { i, r ->
                                if (i > 0) HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        ruleText(r, options),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { update(p.copy(places = p.places.filter { it.id != r.id })) }) {
                                        Icon(KairosIcons.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                            HorizontalDivider()
                            TextButton(
                                onClick = { adding = true },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) { Text("+ Tell me when…") }
                        }
                    }
                }

                if (options.isParent && options.children.isNotEmpty()) {
                    item { SettingsLabel("Safety") }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            options.children.forEachIndexed { i, c ->
                                if (i > 0) HorizontalDivider()
                                ChoiceRow(
                                    title = "${c.name} driving over",
                                    value = p.safety.speed[c.id],
                                    choices = SPEEDS,
                                    label = { if (it == null) "Off" else "$it mph" },
                                    onPick = { v ->
                                        val speed = if (v == null) p.safety.speed - c.id else p.safety.speed + (c.id to v)
                                        update(p.copy(safety = p.safety.copy(speed = speed)))
                                    },
                                )
                            }
                            HorizontalDivider()
                            ChoiceRow(
                                title = "No update from a child’s phone",
                                value = p.safety.noUpdateHours,
                                choices = HOURS,
                                label = { if (it == null) "Off" else "$it hours" },
                                onPick = { v -> update(p.copy(safety = p.safety.copy(noUpdateHours = v))) },
                            )
                            HorizontalDivider()
                            ChoiceRow(
                                title = "A child’s battery below",
                                value = p.safety.lowBattery,
                                choices = BATTERY,
                                label = { if (it == null) "Off" else "$it%" },
                                onPick = { v -> update(p.copy(safety = p.safety.copy(lowBattery = v))) },
                            )
                        }
                    }
                }

                item { SettingsLabel("Quiet hours") }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        val current = QUIET.indexOfFirst { it?.from == p.quiet?.from && it?.to == p.quiet?.to }.coerceAtLeast(0)
                        ChoiceRow(
                            title = "Hold Family map news",
                            value = current,
                            choices = QUIET.indices.toList(),
                            label = { quietLabel(QUIET[it]) },
                            onPick = { v -> update(p.copy(quiet = QUIET[v])) },
                        )
                    }
                }

                item { SettingsLabel("Default reminders") }
                item { DefaultRemindersCard() }

                item { SettingsLabel("Sound") }
                item {
                    OutlinedCard(
                        onClick = { Notifications.openChannelSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sound & vibration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            askPermission()
                            Notifications.post(context, 990001, "Test reminder", "Notifications are working on this device.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Send a test notification") }
                }
            }

            if (adding) {
                AddRuleDialog(
                    options = options,
                    onAdd = { r ->
                        askPermission()
                        update(p.copy(places = p.places + r))
                        adding = false
                    },
                    onDismiss = { adding = false },
                )
            }
        }
    }
}

private val SPEEDS: List<Int?> = listOf(null, 45, 55, 60, 65, 70, 75, 80, 85, 90)
private val HOURS: List<Int?> = listOf(null, 2, 4, 6, 8, 12, 24)
private val BATTERY: List<Int?> = listOf(null, 10, 15, 20, 25, 30)
private val QUIET: List<QuietHoursDto?> = listOf(
    null,
    QuietHoursDto(from = "21:00", to = "06:00"),
    QuietHoursDto(from = "22:00", to = "06:00"),
    QuietHoursDto(from = "22:00", to = "07:00"),
    QuietHoursDto(from = "23:00", to = "07:00"),
)

private fun clock12(hhmm: String): String {
    val h = hhmm.substringBefore(":").toIntOrNull() ?: 0
    val m = hhmm.substringAfter(":")
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return if (m == "00") "$h12 $suffix" else "$h12:$m $suffix"
}

private fun quietLabel(q: QuietHoursDto?): String =
    if (q == null) "Off" else "${clock12(q.from)} – ${clock12(q.to)}"

private fun ruleText(r: PlaceRuleDto, o: NotifyOptionsDto): String {
    val who = r.about?.let { id -> o.people.firstOrNull { it.id == id }?.name ?: "Someone" } ?: "Anyone"
    val what = when {
        r.arrive && r.leave -> "arrives at or leaves"
        r.arrive -> "arrives at"
        else -> "leaves"
    }
    val place = o.places.firstOrNull { it.key == r.place }?.label ?: r.place.substringAfter(":")
    return "$who $what $place"
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    value: T,
    choices: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(label(value)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(label(c)) },
                        onClick = {
                            open = false
                            onPick(c)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRuleDialog(options: NotifyOptionsDto, onAdd: (PlaceRuleDto) -> Unit, onDismiss: () -> Unit) {
    var about by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf(options.places.firstOrNull()?.key) }
    var arrive by remember { mutableStateOf(true) }
    var leave by remember { mutableStateOf(false) }
    val people: List<String?> = listOf<String?>(null) + options.people.map { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tell me when") },
        text = {
            Column {
                ChoiceRow(
                    title = "Who",
                    value = about,
                    choices = people,
                    label = { id -> id?.let { i -> options.people.firstOrNull { it.id == i }?.name } ?: "Anyone" },
                    onPick = { about = it },
                )
                ChoiceRow(
                    title = "Place",
                    value = place,
                    choices = options.places.map { it.key },
                    label = { k -> options.places.firstOrNull { it.key == k }?.label ?: "Pick a place" },
                    onPick = { place = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = arrive, onCheckedChange = { arrive = it })
                    Text("Arrives")
                    Checkbox(checked = leave, onCheckedChange = { leave = it })
                    Text("Leaves")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = place != null && (arrive || leave),
                onClick = {
                    val key = place ?: return@TextButton
                    onAdd(
                        PlaceRuleDto(
                            id = "r" + System.currentTimeMillis().toString(36),
                            about = about,
                            place = key,
                            arrive = arrive,
                            leave = leave,
                        ),
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
