package com.kairos.app.ui.character
import com.kairos.app.ui.nav.KairosIcons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.R
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.CollectSpeciesDto
import com.kairos.app.data.remote.dto.EraDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer

/** The one shiny gold, shared with the character card and the web. */
internal val SHINY = Color(0xFFF5B400)

private fun rarityColor(rarity: String): Color = when (rarity) {
    "legendary" -> Color(0xFFF59E0B)
    "rare" -> Color(0xFF3B82F6)
    "uncommon" -> Color(0xFF10B981)
    else -> Color(0xFF94A3B8)
}

/** Per-era silhouette shown for locked (mystery) creatures. */
private fun mysteryDrawable(eraKey: String): Int = when (eraKey) {
    "MODERN" -> R.drawable.mystery_modern
    "TOON" -> R.drawable.mystery_toon
    "ARCADE" -> R.drawable.mystery_arcade
    "DRAGON" -> R.drawable.mystery_dragon
    "VINTAGE" -> R.drawable.mystery_vintage
    "WW2" -> R.drawable.mystery_ww2
    else -> R.drawable.mystery_generic
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: CollectionViewModel = viewModel(
        factory = viewModelFactory { initializer { CollectionViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    var enlarged by remember { mutableStateOf<CollectSpeciesDto?>(null) }
    // Follow the refreshed list: after a swap the dialog's own copy is stale,
    // so it would still offer to activate what is already active.
    val shown = enlarged?.let { e ->
        ui.data?.eras?.flatMap { it.species }?.firstOrNull { it.id == e.id } ?: e
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, "Back") }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load your gallery.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    data.eras.forEach { era -> EraSection(era, onOpen = { enlarged = it }) }
                }
            }
        }
    }

    // The picture currently in use, to ring the matching stage in the picker.
    val session by container.sessionRepository.state.collectAsState()
    val currentPick = (session as? com.kairos.app.data.session.SessionState.Ready)?.person?.avatarCompanion

    shown?.let { sp ->
        EnlargeDialog(
            sp = sp,
            busy = ui.busy,
            currentPick = currentPick,
            onActivate = { vm.activate(it) },
            onPickStage = { stage -> vm.useAsProfilePicture(sp.id, stage) { enlarged = null } },
            onDismiss = { enlarged = null },
        )
    }

    ui.pictureNotice?.let { msg ->
        AnimatedDialog(
            onDismissRequest = { vm.clearPictureNotice() },
            confirmButton = { TextButton(onClick = { vm.clearPictureNotice() }) { Text("OK") } },
        ) { Text(msg) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EraSection(era: EraDto, onOpen: (CollectSpeciesDto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(era.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${era.unlocked}/${era.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (era.species.isEmpty()) {
            Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                era.species.forEach { sp -> Slot(sp, era.key, onOpen) }
            }
        }
    }
}

@Composable
private fun Slot(sp: CollectSpeciesDto, eraKey: String, onOpen: (CollectSpeciesDto) -> Unit) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val ring = rarityColor(sp.rarity)
    val placeholder = mysteryDrawable(eraKey)

    // A shiny creature keeps its gilding on the shelf: gold frame, lit cell and a
    // star, so the gallery shows what was deepened long after the fact.
    val shiny = sp.owned && sp.shiny
    Box(
        Modifier.size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (shiny) SHINY.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(
                if (shiny) 2.5.dp else 2.dp,
                when {
                    shiny -> SHINY
                    sp.owned -> ring
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                RoundedCornerShape(14.dp),
            )
            .then(if (sp.owned) Modifier.clickable { onOpen(sp) } else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (shiny) {
            Text(
                "\u2726",
                style = MaterialTheme.typography.labelSmall,
                color = SHINY,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        if (sp.owned && base != null && sp.image != null) {
            SubcomposeAsyncImage(
                model = ApiClient.resolveUrl(base, sp.image),
                imageLoader = container.imageLoader,
                contentDescription = sp.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {
                    Image(painterResource(placeholder), contentDescription = "Locked", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                },
            )
        } else {
            Image(
                painter = painterResource(placeholder),
                contentDescription = "Locked",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EnlargeDialog(
    sp: CollectSpeciesDto,
    busy: Boolean,
    currentPick: com.kairos.app.data.remote.dto.AvatarCompanionDto?,
    onActivate: (String) -> Unit,
    onPickStage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    var picking by remember(sp.id) { mutableStateOf(false) }
    if (picking) {
        StagePickerDialog(
            sp = sp,
            busy = busy,
            currentStage = currentPick?.takeIf { it.species == sp.id }?.stage,
            onPick = onPickStage,
            onBack = { picking = false },
        )
        return
    }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = if (sp.shiny) "${sp.name ?: ""} \u2726" else (sp.name ?: ""),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Box(
            Modifier.fillMaxWidth().height(220.dp)
                .then(if (sp.shiny) Modifier.background(SHINY.copy(alpha = 0.14f), RoundedCornerShape(16.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (base != null && sp.image != null) {
                SubcomposeAsyncImage(
                    model = ApiClient.resolveUrl(base, sp.image),
                    imageLoader = container.imageLoader,
                    contentDescription = sp.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) },
                    error = {},
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sp.isActive) {
            Text(
                "This one is with you now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Bringing one back retires whoever is in the slot, which is worth
            // saying before the tap rather than after it.
            Button(
                onClick = { onActivate(sp.id) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Swapping\u2026" else "Make this my companion") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Your current companion goes back to the gallery, keeping everything it has grown. " +
                    "${sp.name ?: "This one"} picks up where it left off" +
                    if (sp.shiny) "." else ", and can be deepened once it is fully grown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // An older server sends no stage art; the option simply isn't offered.
        if (sp.stageImages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { picking = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use as profile picture") }
        }
    }
}

private val STAGE_LABELS = listOf("Hatchling", "Juvenile", "Adult")

/**
 * The three stages side by side. A stage reached shows its art and is
 * tappable; one not reached shows an egg and "Not yet" — its art is never sent,
 * so nothing is spoiled. The picture already in use is ringed.
 */
@Composable
private fun StagePickerDialog(
    sp: CollectSpeciesDto,
    busy: Boolean,
    currentStage: Int?,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val ring = MaterialTheme.colorScheme.primary
    AnimatedDialog(
        onDismissRequest = onBack,
        title = sp.name ?: "",
        confirmButton = { TextButton(onClick = onBack, enabled = !busy) { Text("Back") } },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            STAGE_LABELS.forEachIndexed { stage, label ->
                val image = sp.stageImages.getOrNull(stage)
                val selected = currentStage == stage
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                if (selected) 2.5.dp else 1.dp,
                                if (selected) ring else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .then(
                                if (image != null && !busy) Modifier.clickable { onPick(stage) } else Modifier,
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null && base != null) {
                            SubcomposeAsyncImage(
                                model = ApiClient.resolveUrl(base, image),
                                imageLoader = container.imageLoader,
                                contentDescription = "${sp.name ?: ""} $label",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                loading = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                                error = {},
                            )
                        } else {
                            Icon(
                                KairosIcons.Egg,
                                contentDescription = "Not reached yet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (image != null) label else "Not yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (image != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (busy) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
