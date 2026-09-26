package com.kairos.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.GamePersonDto
import com.kairos.app.data.remote.dto.WeekDayDto
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val XboxGreen = Color(0xFF107C10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onOpenDrawer: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: GamesViewModel = viewModel(
        factory = viewModelFactory { initializer { GamesViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    var selected by remember { mutableStateOf<GamePersonDto?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Game time") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
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
                // A single person (a child seeing only themselves) goes straight
                // to the detailed view — that's their main screen.
                data.people.size == 1 -> PersonDetail(data.people[0], onBack = null)
                // A parent picked someone from the list.
                selected != null -> PersonDetail(selected!!, onBack = { selected = null })
                // A parent sees the household list; tapping opens the detail.
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    data.people.forEachIndexed { index, p ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        PersonCard(p, onClick = { selected = p })
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PersonCard(p: GamePersonDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(p, 40.dp)
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        PlatformIcons(p.platforms)
                    }
                    StatusRow(p)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Today", p.today)
                Stat("This week", p.week)
                Stat("This month", p.month)
            }
            TopGames(p)
        }
    }
}

@Composable
private fun PersonDetail(p: GamePersonDto, onBack: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("\u2190  Back")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarCircle(p, 48.dp)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    PlatformIcons(p.platforms)
                }
                StatusRow(p)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Today", p.today)
            Stat("This week", p.week)
            Stat("This month", p.month)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "THIS WEEK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WeekChart(p.weekDaily)
        }
        TopGames(p)
    }
}

@Composable
private fun AvatarCircle(p: GamePersonDto, diameter: Dp) {
    Box(
        Modifier.size(diameter).clip(CircleShape).background(colorFromHex(p.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(p.name), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PlatformIcons(platforms: List<String>) {
    if (platforms.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (platforms.contains("xbox")) {
            Icon(KairosIcons.Xbox, "Xbox", tint = XboxGreen, modifier = Modifier.size(15.dp))
        }
        if (platforms.contains("steam")) {
            Icon(KairosIcons.Steam, "Steam", tint = Color(0xFF1B2838), modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun StatusRow(p: GamePersonDto) {
    Row(
        Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        p.gamerscore?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(XboxGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "G",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
                Text(
                    "%,d".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (p.hasGamePass == true) {
            Text(
                "Game Pass",
                style = MaterialTheme.typography.bodySmall,
                color = XboxGreen,
                fontWeight = FontWeight.Medium,
            )
        }
        p.msBalance?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    KairosIcons.Wallet,
                    "Wallet balance",
                    tint = XboxGreen,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun TopGames(p: GamePersonDto) {
    if (p.games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Top games this week",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        p.games.forEach { g ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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

@Composable
private fun WeekChart(days: List<WeekDayDto>) {
    val max = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (d.minutes > 0) {
                        Text(
                            hhmm(d.minutes),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    val h = (d.minutes.toFloat() / max * 100f).dp
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (d.minutes > 0) h.coerceAtLeast(3.dp) else 0.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(XboxGreen),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { d ->
                Text(
                    d.label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
