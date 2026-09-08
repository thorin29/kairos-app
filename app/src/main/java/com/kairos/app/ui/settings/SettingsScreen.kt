package com.kairos.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.nav.KairosIcons

private val PageGrey = Color(0xFFE9EDF3)

private data class Section(
    val icon: ImageVector,
    val title: String,
    val blurb: String,
)

private val SECTIONS = listOf(
    Section(
        KairosIcons.Palette,
        "Appearance",
        "Dark mode and a colour theme for the whole app \u2014 teal, olive, blue, and more.",
    ),
    Section(
        KairosIcons.PersonCircle,
        "Profile",
        "Your photo, how it's framed, and your colour (which follows you onto the web and calendar).",
    ),
    Section(
        KairosIcons.Bell,
        "Notifications",
        "Reminders for calendar events and birthdays. Off by default \u2014 turn on only what you want.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
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
                .background(PageGrey),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "These are rolling out over the next few updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                SECTIONS.forEach { s ->
                    item(key = s.title) { SectionCard(s) }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: Section) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = Color(0xFF0F5C63),
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
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
