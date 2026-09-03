package com.kairos.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.BuildConfig
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.LogoMenuButton

private val SidebarColor = Color(0xFF86A0A3) // --color-sidebar
private val OnSidebar = Color.White

/**
 * The nav rail, matching the web sidebar in both states. The teal panel starts
 * just below the status bar (a flat top above the logo) and runs to the bottom;
 * content is inset from the nav bar so nothing lands in the curved corner. The
 * logo sits where the top-bar logo is, so opening looks like it unfurls from it.
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
    onSignOut: () -> Unit,
) {
    Column(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()          // transparent strip above the teal
                .background(SidebarColor)      // teal: below status bar -> full bottom
                .navigationBarsPadding()       // keep content out of the gesture/curve zone
                .padding(bottom = 6.dp),
        ) {
            // Header: logo aligned with the top-bar logo, + page name when open.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoMenuButton(onClick = onLogoClick)
                if (expanded) {
                    Spacer(Modifier.width(8.dp))
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                APP_SECTIONS.forEach { section ->
                    RailRow(section, section.key == selectedKey, expanded) { onSection(section) }
                }
            }

            HorizontalDivider(color = OnSidebar.copy(alpha = 0.2f))

            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                FooterPerson(person, expanded, onSignOut)
                if (expanded) {
                    // Collapse control on the left, version pinned far-right.
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onToggleExpanded)
                                .height(40.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                KairosIcons.ChevronLeft, "Collapse",
                                tint = OnSidebar.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Collapse",
                                color = OnSidebar.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            color = OnSidebar.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onToggleExpanded),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                KairosIcons.ChevronRight, "Expand",
                                tint = OnSidebar.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
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
        Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
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
private fun FooterPerson(person: PersonDto, expanded: Boolean, onSignOut: () -> Unit) {
    val label = person.avatarIcon ?: initials(person.name)
    if (expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 2.dp),
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
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSignOut) {
                Icon(
                    KairosIcons.Switch,
                    contentDescription = "Sign out / switch",
                    tint = OnSidebar.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
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

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { name.take(2).uppercase() }
