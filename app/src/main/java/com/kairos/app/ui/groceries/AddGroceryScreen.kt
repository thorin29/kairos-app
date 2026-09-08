package com.kairos.app.ui.groceries

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.kairos.app.ui.theme.KairosThemeState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.kairos.app.data.remote.dto.GroceriesDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons


internal data class PendingAdd(val label: String, val catalogId: String?, val defaultStoreId: String?)

/**
 * Full-screen add wizard. Step one is a searchable list of everything in the
 * catalog (so you reuse an item instead of making a duplicate) with a row to
 * create whatever you typed; step two asks which store (skipped when there's
 * one, pre-selecting the item's usual store). The ViewModel is shared with the
 * groceries hub via its back-stack entry, so the add survives popping back and
 * the hub shows it on return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroceryScreen(parentEntry: NavBackStackEntry?, onClose: () -> Unit) {
    val container = rememberContainer()
    val owner = parentEntry ?: LocalViewModelStoreOwner.current!!
    val vm: GroceriesViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { GroceriesViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    val data = ui.data

    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingAdd?>(null) }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(if (pending == null) "Add an item" else "Which store?") },
                navigationIcon = {
                    IconButton(onClick = { if (pending != null) pending = null else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (data == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val only = if (data.stores.size == 1) data.stores.first().id else null

        fun finish(p: PendingAdd, storeId: String) {
            if (p.catalogId != null) vm.addFromCatalog(p.catalogId, storeId) else vm.add(p.label, storeId)
            onClose()
        }
        fun choose(p: PendingAdd) {
            if (only != null) finish(p, only) else pending = p
        }

        val p = pending
        if (p == null) {
            ItemStep(
                data = data,
                query = query,
                onQuery = { query = it },
                busy = ui.busy,
                onPick = { choose(it) },
                contentPadding = inner,
            )
        } else {
            StoreStep(
                data = data,
                label = p.label,
                usualStoreId = p.defaultStoreId,
                busy = ui.busy,
                onPick = { storeId -> finish(p, storeId) },
                contentPadding = inner,
            )
        }
    }
}

@Composable
private fun ItemStep(
    data: GroceriesDto,
    query: String,
    onQuery: (String) -> Unit,
    busy: Boolean,
    onPick: (PendingAdd) -> Unit,
    contentPadding: PaddingValues,
) {
    val q = query.trim().lowercase()
    val list = (if (q.isEmpty()) data.catalog else data.catalog.filter { it.name.lowercase().contains(q) })
        .sortedBy { it.name.lowercase() }
    val exact = data.catalog.any { it.name.lowercase() == q }

    Column(Modifier.padding(contentPadding).fillMaxSize().padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { onQuery(it.take(60)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search or add an item\u2026", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
            )
        }

        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (q.isNotEmpty() && !exact) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !busy) { onPick(PendingAdd(query.trim(), null, null)) }
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(KairosIcons.Plus, contentDescription = null, tint = KairosThemeState.accent, modifier = Modifier.size(20.dp))
                        Text("Add \u201c${query.trim()}\u201d", style = MaterialTheme.typography.bodyLarge, color = KairosThemeState.accent, fontWeight = FontWeight.Medium)
                    }
                }
            }
            items(list, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !busy) { onPick(PendingAdd(c.name, c.id, c.defaultStoreId)) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GroceryGlyph(c.icon, emojiStyle = MaterialTheme.typography.titleMedium, size = 22.dp)
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(KairosIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StoreStep(
    data: GroceriesDto,
    label: String,
    usualStoreId: String?,
    busy: Boolean,
    onPick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val ordered = remember(data.stores, usualStoreId) {
        val usual = data.stores.filter { it.id == usualStoreId }
        val rest = data.stores.filter { it.id != usualStoreId }
        usual + rest
    }
    Column(Modifier.padding(contentPadding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Add \u201c$label\u201d to:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        ordered.forEach { store ->
            val usual = store.id == usualStoreId
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .then(if (usual) Modifier.border(1.5.dp, KairosThemeState.accent, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(enabled = !busy) { onPick(store.id) }
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(store.icon, style = MaterialTheme.typography.titleMedium)
                Text(store.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (usual) Text("usual", style = MaterialTheme.typography.labelSmall, color = KairosThemeState.accent)
            }
        }
    }
}
