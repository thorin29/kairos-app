package com.kairos.app.ui.settings
import com.kairos.app.ui.nav.KairosIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.theme.BackgroundScheme
import com.kairos.app.ui.theme.KairosThemeState
import com.kairos.app.ui.theme.ThemeScheme
import com.kairos.app.ui.theme.neutrals
import kotlinx.coroutines.launch
import com.kairos.app.ui.common.AnimatedDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val settings = container.settingsStore
    val scope = rememberCoroutineScope()

    val schemeKey by settings.themeScheme.collectAsState(initial = "TEAL")
    val mode by settings.themeMode.collectAsState(initial = "SYSTEM")
    val widgetStyle by settings.widgetStyle.collectAsState(initial = "SOLID")
    val widgetScope by settings.widgetScope.collectAsState(initial = "FAMILY")
    val widgetTheme by settings.widgetTheme.collectAsState(initial = "APP")
    val timerTheme by settings.timerTheme.collectAsState(initial = "DARK")
    val current = ThemeScheme.fromKey(schemeKey)
    val backgroundKey by settings.backgroundScheme.collectAsState(initial = "CLASSIC")
    val currentBackground = BackgroundScheme.fromKey(backgroundKey)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Every choice here shows on the home-screen widget too, so redraw it.
    fun save(block: suspend () -> Unit) {
        scope.launch {
            block()
            com.kairos.app.widget.KairosWidget.refresh(ctx.applicationContext)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Three sections (v0.501): the app's look, then the widget
                // and the workout timer, so this phone's extras sit together.
                item { SettingsLabel("App") }
                item { AppearancePreview() }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Light or dark",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(12.dp))
                            Segmented(
                                options = listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
                                selected = mode,
                                onPick = { v -> save { settings.setThemeMode(v) } },
                            )
                        }
                    }
                }
                item {
                    ColorPicker(
                        current = current,
                        onPick = { scheme -> save { settings.setThemeScheme(scheme.name) } },
                    )
                }
                item {
                    BackgroundPicker(
                        current = currentBackground,
                        onPick = { b -> save { settings.setBackgroundScheme(b.name) } },
                    )
                }
                item {
                    val fmt by settings.timeFormat.collectAsState(initial = "SYSTEM")
                    var showFmt by remember { mutableStateOf(false) }
                    val fmtLabel = when (fmt) {
                        "H24" -> "13:00"
                        "H12" -> "1 PM"
                        else -> "System time"
                    }
                    OutlinedCard(onClick = { showFmt = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Time format",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                fmtLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showFmt) {
                        AnimatedDialog(
                            onDismissRequest = { showFmt = false },
                            title = "Time format",
                            confirmButton = {
                                TextButton(onClick = { showFmt = false }) { Text("Done") }
                            },
                        ) {
                            Column {
                                listOf(
                                    "SYSTEM" to "System time",
                                    "H24" to "13:00",
                                    "H12" to "1 PM",
                                ).forEach { (v, label) ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch { settings.setTimeFormat(v) }
                                                showFmt = false
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = fmt == v,
                                            onClick = {
                                                scope.launch { settings.setTimeFormat(v) }
                                                showFmt = false
                                            },
                                        )
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                item { SettingsLabel("Home screen widget") }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Events",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                options = listOf("SOLID" to "Solid", "BAR" to "Accent"),
                                selected = widgetStyle,
                                onPick = { v -> save { settings.setWidgetStyle(v) } },
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Light or dark",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                options = listOf("APP" to "App", "SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
                                selected = widgetTheme,
                                onPick = { v -> save { settings.setWidgetTheme(v) } },
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Show",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                options = listOf("FAMILY" to "Whole family", "MINE" to "Only mine"),
                                selected = widgetScope,
                                onPick = { v -> save { settings.setWidgetScope(v) } },
                            )
                        }
                    }
                }
                item { SettingsLabel("Workout timer") }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Look",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                options = listOf("DARK" to "Dark", "LIGHT" to "Light", "APP" to "Match app"),
                                selected = timerTheme,
                                onPick = { v -> save { settings.setTimerTheme(v) } },
                            )
                            // Digits and what a running timer does when you leave
                            // the app (v0.438).
                            val timerFont by settings.timerFont.collectAsState(initial = "STANDARD")
                            val timerColor by settings.timerColor.collectAsState(initial = "PLAIN")
                            val timerAway by settings.timerAway.collectAsState(initial = "BOTH")
                            val primary = MaterialTheme.colorScheme.primary
                            Spacer(Modifier.height(12.dp))
                            // Each choice drawn as it looks (v0.440).
                            Text("Numbers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            com.kairos.app.ui.workout.TimerLookPicker(
                                options = com.kairos.app.ui.workout.TIMER_FONTS,
                                selected = timerFont,
                                font = { com.kairos.app.ui.workout.timerFontFor(it) },
                                color = { com.kairos.app.ui.workout.timerColorFor(timerColor, primary) ?: Color.White },
                                onPick = { v -> scope.launch { settings.setTimerFont(v) } },
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Number colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            com.kairos.app.ui.workout.TimerLookPicker(
                                options = com.kairos.app.ui.workout.TIMER_COLORS,
                                selected = timerColor,
                                font = { com.kairos.app.ui.workout.timerFontFor(timerFont) },
                                color = { com.kairos.app.ui.workout.timerColorFor(it, primary) ?: Color.White },
                                onPick = { v -> scope.launch { settings.setTimerColor(v) } },
                            )
                            Spacer(Modifier.height(10.dp))
                            com.kairos.app.ui.workout.Dropdown(
                                label = "When you leave the app",
                                selected = com.kairos.app.ui.workout.TIMER_AWAY.firstOrNull { it.first == timerAway }?.second ?: "Notification and floating window",
                                options = com.kairos.app.ui.workout.TIMER_AWAY,
                                onSelect = { v -> scope.launch { settings.setTimerAway(v) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A small live sample of the current look (v0.509): a person card and the
 *  Skip / Log buttons, drawn with the theme's own colors. */
@Composable
private fun AppearancePreview() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp))
            .background(cs.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
                .background(cs.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "S", color = cs.onPrimary, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Sam", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(text = "3 of 5 chores", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(cs.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(7.dp).clip(CircleShape).background(cs.primary))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                    .background(cs.surface),
                contentAlignment = Alignment.Center,
            ) { Text(text = "Skip", color = cs.primary, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) { Text(text = "Log", color = cs.onPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** One row of color dots, the chosen one ringed, its name on the right (v0.509;
 *  was a list of one row per color). */
@Composable
private fun ColorPicker(current: ThemeScheme, onPick: (ThemeScheme) -> Unit) {
    val dark = KairosThemeState.dark
    val cs = MaterialTheme.colorScheme
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = current.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ThemeScheme.entries.forEach { scheme ->
                    val selected = scheme == current
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(if (selected) Modifier.border(2.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable(onClickLabel = scheme.label) { onPick(scheme) }
                            .padding(if (selected) 5.dp else 2.dp)
                            .clip(CircleShape)
                            .background(if (dark) scheme.swatchDark else scheme.swatch),
                    )
                }
            }
        }
    }
}

/** Background tiles (v0.509): each a small card on its page color, following
 *  light / dark, with its name under it. */
@Composable
private fun BackgroundPicker(current: BackgroundScheme, onPick: (BackgroundScheme) -> Unit) {
    val dark = KairosThemeState.dark
    val cs = MaterialTheme.colorScheme
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Background", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = current.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            BackgroundScheme.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { b ->
                        val n = neutrals(b = b, dark = dark)
                        val selected = b == current
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = b.label) { onPick(b) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(n.page)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) cs.primary else cs.outlineVariant,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(start = 8.dp, end = 8.dp, bottom = 7.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(n.card)
                                        .border(1.dp, n.line, RoundedCornerShape(6.dp)),
                                )
                            }
                            Text(
                                text = b.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A row of equal choices, one selected — the same shape as the calendar's
 *  Custom / System switch. */
@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { if (!on) onPick(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
