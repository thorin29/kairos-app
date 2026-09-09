package com.kairos.app.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
    val dark by settings.darkMode.collectAsState(initial = false)
    val current = ThemeScheme.fromKey(schemeKey)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Dark mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Darker backgrounds, easier on the eyes at night.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = dark,
                                onCheckedChange = { on -> scope.launch { settings.setDarkMode(on) } },
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
                                    onPick = { scope.launch { settings.setThemeScheme(scheme.name) } },
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
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = scheme.swatch,
            )
        }
    }
}
