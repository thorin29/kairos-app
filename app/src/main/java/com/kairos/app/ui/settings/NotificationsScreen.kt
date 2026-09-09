package com.kairos.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.notifications.LEAD_OPTIONS
import com.kairos.app.data.notifications.NotifPrefs
import com.kairos.app.data.notifications.NotifScope
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.data.notifications.TypePref
import com.kairos.app.data.notifications.leadLabel
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs by container.settingsStore.notifPrefs.collectAsState(initial = NotifPrefs())

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* posting re-checks; nothing else to do */ }

    LaunchedEffect(Unit) { Notifications.ensureChannel(context) }

    fun save(p: NotifPrefs) = scope.launch { container.settingsStore.setNotifPrefs(p) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Reminders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Calendar and birthday reminders on this device. Everything below is off until you turn it on.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = prefs.enabled,
                                onCheckedChange = { on ->
                                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !Notifications.hasPermission(context)
                                    ) {
                                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    save(prefs.copy(enabled = on))
                                },
                            )
                        }
                    }
                }

                if (prefs.enabled) {
                    item {
                        Text(
                            "Which events",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item { ScopeSelector(prefs.scope) { save(prefs.copy(scope = it)) } }
                    item {
                        Text(
                            "Set a reminder time on each event when you create or edit it. This just controls whether those reminders show on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    item {
                        Text(
                            "Birthdays",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item {
                        ToggleLeadRow(
                            title = "Birthdays",
                            subtitle = "Everyone's birthday, family and personal.",
                            dot = Color(0xFFEC4899),
                            enabled = prefs.birthdayEnabled,
                            lead = prefs.birthdayLead,
                            onToggle = { save(prefs.copy(birthdayEnabled = it)) },
                            onLead = { save(prefs.copy(birthdayLead = it)) },
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                Notifications.post(
                                    context, 990001, "Test reminder",
                                    "Notifications are working on this device.",
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Send a test notification") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSelector(scope: NotifScope, onSelect: (NotifScope) -> Unit) {
    val items = listOf(
        NotifScope.MINE to "My events",
        NotifScope.ALL to "All events",
        NotifScope.FAMILY to "Family only",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            val sel = value == scope
            Box(
                Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .background(
                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ToggleLeadRow(
    title: String,
    subtitle: String?,
    dot: Color,
    enabled: Boolean,
    lead: Int,
    onToggle: (Boolean) -> Unit,
    onLead: (Int) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(dot, CircleShape))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                LeadDropdown(lead = lead, onLead = onLead, modifier = Modifier.padding(top = 8.dp, start = 22.dp))
            }
        }
    }
}

@Composable
private fun LeadDropdown(lead: Int, onLead: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(leadLabel(lead), style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LEAD_OPTIONS.forEach { m ->
                DropdownMenuItem(
                    text = { Text(leadLabel(m)) },
                    onClick = { onLead(m); open = false },
                )
            }
        }
    }
}
