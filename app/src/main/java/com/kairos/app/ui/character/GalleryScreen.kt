package com.kairos.app.ui.character

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.CollectSpeciesDto
import com.kairos.app.data.remote.dto.EraDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer

private fun rarityColor(rarity: String): Color = when (rarity) {
    "legendary" -> Color(0xFFF59E0B)
    "rare" -> Color(0xFF3B82F6)
    "uncommon" -> Color(0xFF10B981)
    else -> Color(0xFF94A3B8)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: CollectionViewModel = viewModel(
        factory = viewModelFactory { initializer { CollectionViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    var enlarged by remember { mutableStateOf<CollectSpeciesDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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

    enlarged?.let { sp -> EnlargeDialog(sp) { enlarged = null } }
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
                era.species.forEach { sp -> Slot(sp, onOpen) }
            }
        }
    }
}

@Composable
private fun Slot(sp: CollectSpeciesDto, onOpen: (CollectSpeciesDto) -> Unit) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val ring = rarityColor(sp.rarity)

    Box(
        Modifier.size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, if (sp.owned) ring else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .then(if (sp.owned) Modifier.clickable { onOpen(sp) } else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (sp.owned && base != null && sp.image != null) {
            SubcomposeAsyncImage(
                model = ApiClient.resolveUrl(base, sp.image),
                imageLoader = container.imageLoader,
                contentDescription = sp.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = { Text("?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        } else {
            Text("?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EnlargeDialog(sp: CollectSpeciesDto, onDismiss: () -> Unit) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = sp.name ?: "",
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
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
    }
}
