package com.kairos.app.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.BuildConfig
import com.kairos.app.R
import com.kairos.app.data.remote.dto.PersonDto

private val SidebarColor = Color(0xFF86A0A3) // --color-sidebar
private val OnSidebar = Color.White

/**
 * The nav rail, matching the web sidebar in both states: a narrow icon-only
 * collapsed rail and a wider expanded rail with labels. Active row turns white
 * with the section's brand colour. Caller animates width + the roll-out.
 */
@Composable
fun KairosRail(
    modifier: Modifier,
    expanded: Boolean,
    person: PersonDto,
    selectedKey: String,
    activeLabel: String,
    onSection: (AppSection) -> Unit,
    onToggleExpanded: () -> Unit,
    onLogoClick: () -> Unit,
    onDevices: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier
            .background(SidebarColor)
            .padding(vertical = 8.dp),
    ) {
        // Header: logo (tap to close) + current page name when expanded.
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = if (expanded) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.kairos_logo),
                contentDescription = "Close menu",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLogoClick),
            )
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    activeLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSidebar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            APP_SECTIONS.forEach { section ->
                RailRow(section, section.key == selectedKey, expanded) { onSection(section) }
            }
        }

        HorizontalDivider(color = OnSidebar.copy(alpha = 0.2f))

        // Footer: person, then the collapse/expand control (+ version when open).
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            FooterPerson(person, expanded)
            ToggleRow(expanded, onToggleExpanded)
            if (expanded) {
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    color = OnSidebar.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, end = 8.dp),
                )
            }
            if (expanded) {
                FooterTextRow("Devices", onDevices)
                FooterTextRow("Sign out", onSignOut)
            }
        }
    }
}

@Composable
private fun RailRow(
    section: AppSection,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) section.color else OnSidebar.copy(alpha = 0.85f)
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Color.White else Color.Transparent)
                .clickable(onClick = onClick)
                .height(44.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(section.icon, section.label, tint = content, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                section.label,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.icon, section.label, tint = content, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun FooterPerson(person: PersonDto, expanded: Boolean) {
    val label = person.avatarIcon ?: initials(person.name)
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(label)
            Spacer(Modifier.width(12.dp))
            Text(
                person.name,
                color = OnSidebar,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Avatar(label)
        }
    }
}

@Composable
private fun Avatar(label: String) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OnSidebar, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ToggleRow(expanded: Boolean, onToggle: () -> Unit) {
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .height(40.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KairosIcons.ChevronLeft, "Collapse", tint = OnSidebar.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Collapse", color = OnSidebar.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KairosIcons.ChevronRight, "Expand", tint = OnSidebar.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FooterTextRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnSidebar.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { name.take(2).uppercase() }
