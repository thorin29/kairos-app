package com.kairos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

// Matches PERSON_PALETTE in the web (src/lib/palette.ts).
private val PERSON_COLORS = listOf(
    "#2563eb", "#db2777", "#059669", "#d97706",
    "#7c3aed", "#0891b2", "#c2410c", "#4d7c0f",
)

private fun parseHex(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF64748B)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF64748B)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF64748B)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(person: PersonDto, onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(person.color ?: PERSON_COLORS.first()) }
    var saving by remember { mutableStateOf(false) }
    var savedTick by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    AvatarPreview(person = person, ring = parseHex(selected))
                }
                item {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Your colour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Used for your avatar ring, your calendar events, and everywhere your colour shows \u2014 on the web too.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                            )
                            SwatchGrid(selected = selected, onPick = { selected = it })
                        }
                    }
                }

                item {
                    val dirty = selected != (person.color ?: PERSON_COLORS.first())
                    androidx.compose.material3.Button(
                        onClick = {
                            if (saving) return@Button
                            scope.launch {
                                saving = true
                                runCatching {
                                    container.sessionRepository.setMyColor(selected)
                                    if (container.sessionRepository.isOnline()) {
                                        container.sessionRepository.refreshPerson()
                                    }
                                }
                                saving = false
                                savedTick++
                            }
                        },
                        enabled = dirty && !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (!dirty && savedTick > 0) "Saved \u2713" else "Save")
                        }
                    }
                }

                item {
                    Text(
                        "Changing your photo and how it's framed is coming in an update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarPreview(person: PersonDto, ring: Color) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val url = person.avatarUrl
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(ring.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null && base != null) {
                SubcomposeAsyncImage(
                    model = ApiClient.resolveUrl(base, url),
                    imageLoader = container.imageLoader,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    person.shortName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ring,
                )
            }
        }
        Box(Modifier.fillMaxSize().border(3.dp, ring, CircleShape))
    }
}

@Composable
private fun SwatchGrid(selected: String, onPick: (String) -> Unit) {
    // Two rows of four.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PERSON_COLORS.chunked(4).forEach { row ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { hex ->
                    val isSel = hex.equals(selected, ignoreCase = true)
                    Box(
                        Modifier
                            .weight(1f)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(parseHex(hex))
                            .border(
                                width = if (isSel) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { onPick(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
