package com.kairos.app.ui.settings
import com.kairos.app.ui.nav.KairosIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
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
import com.kairos.app.ui.theme.ThemeScheme
import kotlinx.coroutines.launch

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
    val current = ThemeScheme.fromKey(schemeKey)
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
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "System follows your phone's light or dark setting. The widget matches.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Home screen widget",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Events",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Segmented(
                                options = listOf("SOLID" to "Solid boxes", "BAR" to "Like Home"),
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
                                options = listOf("APP" to "Like app", "SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
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
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Event colors follow your calendar color settings. Tasks are always your own.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                        AlertDialog(
                            onDismissRequest = { showFmt = false },
                            title = { Text("Time format") },
                            text = {
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
                            },
                            confirmButton = {
                                TextButton(onClick = { showFmt = false }) { Text("Done") }
                            },
                        )
                    }
                }
                item {
                    Text(
                        "Color theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            ThemeScheme.entries.forEach { scheme ->
                                SchemeRow(
                                    scheme = scheme,
                                    selected = scheme == current,
                                    onPick = { save { settings.setThemeScheme(scheme.name) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchemeRow(scheme: ThemeScheme, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onPick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(scheme.swatch)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
        Text(
            scheme.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                KairosIcons.Check,
                contentDescription = "Selected",
                tint = scheme.swatch,
            )
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
