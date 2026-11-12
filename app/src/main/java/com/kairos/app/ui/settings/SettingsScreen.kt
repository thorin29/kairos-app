package com.kairos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.BuildConfig
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.PersonAvatar
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.theme.KairosThemeState

/** One row in a Settings group. */
private data class SettingRow(
    val icon: ImageVector,
    val title: String,
    val sub: String? = null,
    val value: String? = null,
    val badge: String? = null,
    val onOpen: () -> Unit,
)

private data class SettingGroup(val label: String, val rows: List<SettingRow>)

/**
 * Settings (v0.501): your profile on top, then grouped rows — Account, Look &
 * feel, Notifications, Connected apps, Parents (parents only), Help & about —
 * instead of ten separate cards. Devices and Sign out are reachable here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    person: PersonDto? = null,
    onOpenUpdate: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenScoring: () -> Unit = {},
    onOpenPassword: () -> Unit = {},
    onOpenConcept2: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenApprovals: (() -> Unit)? = null,
    approvalsCount: Int = 0,
    updateAvailable: Boolean = false,
) {
    val groups = listOfNotNull(
        SettingGroup(
            "Account",
            listOf(
                SettingRow(icon = KairosIcons.Lock, title = "Password", onOpen = onOpenPassword),
                SettingRow(icon = KairosIcons.Phone, title = "Devices", sub = "Phones signed in", onOpen = onOpenDevices),
                SettingRow(icon = KairosIcons.Switch, title = "Sign out or switch person", onOpen = onSignOut),
            ),
        ),
        SettingGroup(
            "Look & feel",
            listOf(
                SettingRow(
                    icon = KairosIcons.Palette,
                    title = "Appearance",
                    sub = "Theme, colours, widget, workout timer",
                    onOpen = onOpenAppearance,
                ),
            ),
        ),
        SettingGroup(
            "Notifications",
            listOf(
                SettingRow(
                    icon = KairosIcons.Bell,
                    title = "Notifications & reminders",
                    sub = "Alerts, default reminders, sound",
                    onOpen = onOpenNotifications,
                ),
            ),
        ),
        SettingGroup(
            "Connected apps",
            listOf(SettingRow(icon = KairosIcons.Link, title = "Concept2", sub = "Rower logbook", onOpen = onOpenConcept2)),
        ),
        onOpenApprovals?.let {
            SettingGroup(
                "Parents",
                listOf(
                    SettingRow(
                        icon = KairosIcons.Book,
                        title = "Class approvals",
                        badge = if (approvalsCount > 0) "$approvalsCount" else null,
                        onOpen = it,
                    ),
                ),
            )
        },
        SettingGroup(
            "Help & about",
            listOf(
                SettingRow(icon = KairosIcons.Trophy, title = "How scoring works", onOpen = onOpenScoring),
                SettingRow(
                    icon = KairosIcons.Download,
                    title = "Software update",
                    value = if (updateAvailable) null else "v${BuildConfig.VERSION_NAME}",
                    badge = if (updateAvailable) "New" else null,
                    onOpen = onOpenUpdate,
                ),
                SettingRow(icon = KairosIcons.Search, title = "Diagnostics", onOpen = onOpenDiagnostics),
            ),
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "profile") { ProfileCard(person = person, onOpen = onOpenProfile) }
                groups.forEach { g ->
                    item(key = "label-${g.label}") {
                        SettingsLabel(g.label, modifier = Modifier.padding(top = 12.dp))
                    }
                    item(key = "group-${g.label}") { GroupCard(g) }
                }
            }
        }
    }
}

/** A section's small uppercase label (v0.501), shared by the settings pages. */
@Composable
internal fun SettingsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ProfileCard(person: PersonDto?, onOpen: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (person != null) {
                PersonAvatar(person = person, size = 52.dp)
            } else {
                Icon(KairosIcons.PersonCircle, contentDescription = null, tint = KairosThemeState.accent, modifier = Modifier.size(52.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    person?.name ?: "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Photo, colour, birthday",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupCard(group: SettingGroup) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            group.rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { row.onOpen() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(row.icon, contentDescription = null, tint = KairosThemeState.accent, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        row.sub?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    row.value?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    row.badge?.let {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF0B6FA4))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
