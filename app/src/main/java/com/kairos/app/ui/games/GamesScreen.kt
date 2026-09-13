package com.kairos.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.GamePersonDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onOpenDrawer: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: GamesViewModel = viewModel(
        factory = viewModelFactory { initializer { GamesViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game time") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn\u2019t load game time.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                data.people.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No game time recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    data.people.forEach { p -> PersonGameCard(p) }
                }
            }
        }
    }
}

@Composable
private fun PersonGameCard(p: GamePersonDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colorFromHex(p.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initials(p.name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    val chips = buildList {
                        p.gamerscore?.let { add("G " + "%,d".format(it)) }
                        if (p.hasGamePass == true) add("Game Pass")
                        p.msBalance?.let { add(it) }
                    }
                    if (chips.isNotEmpty()) {
                        Text(
                            chips.joinToString("  \u00b7  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Today", p.today)
                Stat("This week", p.week)
                Stat("This month", p.month)
            }

            if (p.games.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Top games this week",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    p.games.forEach { g ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(g.game, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                hhmm(g.minutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, minutes: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(hhmm(minutes), style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hhmm(m: Int): String {
    val h = m / 60
    val mm = m % 60
    return if (h == 0) "${mm}m" else if (mm == 0) "${h}h" else "${h}h ${mm}m"
}

private fun colorFromHex(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(if (hex.isBlank()) "#888888" else hex))
    } catch (e: Exception) {
        Color(0xFF888888)
    }

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return parts.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}
