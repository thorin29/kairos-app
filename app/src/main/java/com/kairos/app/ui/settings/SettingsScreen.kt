package com.kairos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.nav.KairosIcons
import com.kairos.app.ui.theme.KairosThemeState

private data class Section(
    val icon: ImageVector,
    val title: String,
    val blurb: String,
    val ready: Boolean,
    val onOpen: (() -> Unit)? = null,
    val badge: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenUpdate: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    updateAvailable: Boolean = false,
) {
    val sections = listOf(
        Section(
            KairosIcons.Palette,
            "Appearance",
            "Dark/light mode, color themes",
            ready = true,
            onOpen = onOpenAppearance,
        ),
        Section(
            KairosIcons.PersonCircle,
            "Profile",
            "Edit profile color and image",
            ready = true,
            onOpen = onOpenProfile,
        ),
        Section(
            KairosIcons.Bell,
            "Notifications",
            "Reminders, sounds, and vibration settings",
            ready = true,
            onOpen = onOpenNotifications,
        ),
        Section(
            KairosIcons.Calendar,
            "Default reminders",
            "Default reminder for each event type",
            ready = true,
            onOpen = onOpenReminders,
        ),
        Section(
            KairosIcons.Download,
            "Software update",
            if (updateAvailable) "An update is ready to install" else "Check for app updates",
            ready = true,
            onOpen = onOpenUpdate,
            badge = updateAvailable,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.forEach { s ->
                    item(key = s.title) { SectionCard(s) }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: Section) {
    val clickMod =
        if (section.onOpen != null) Modifier.clickable { section.onOpen.invoke() } else Modifier
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(clickMod)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = KairosThemeState.accent,
                modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    section.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (section.badge) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4FC3F7)),
                )
            }
            if (section.ready) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
