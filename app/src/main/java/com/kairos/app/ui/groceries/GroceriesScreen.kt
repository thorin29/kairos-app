package com.kairos.app.ui.groceries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.GroceriesDto
import com.kairos.app.data.remote.dto.GroceryLineDto
import com.kairos.app.data.remote.dto.GroceryStoreDto
import com.kairos.app.data.remote.dto.GroceryTripDto
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val ACCENT = Color(0xFF0F5C63)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceriesScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: GroceriesViewModel = viewModel(
        factory = viewModelFactory { initializer { GroceriesViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groceries") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load groceries.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> GroceriesContent(vm, ui, data)
            }
        }
    }
}

@Composable
private fun GroceriesContent(vm: GroceriesViewModel, ui: GroceriesUiState, data: GroceriesDto) {
    var showAdd by remember { mutableStateOf(false) }
    var showShopPicker by remember { mutableStateOf(false) }

    val tripStoreIds = data.trips.map { it.storeId }.toSet()
    val storeById = data.stores.associateBy { it.id }
    val savedByStore = data.saved.groupBy { it.storeId }
    // Stores that have a saved list but no active trip.
    val savedStores = data.stores.filter { it.id in savedByStore && it.id !in tripStoreIds }
    // Stores you can start a run for (no run under way).
    val shoppableStores = data.stores.filter { it.id !in tripStoreIds }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("I'm going shopping", KairosIcons.Cart, filled = true, enabled = !ui.busy) {
                vm.clearMessage()
                showShopPicker = true
            }
            PillButton("Add item", KairosIcons.Plus, filled = false, enabled = !ui.busy) {
                vm.clearMessage()
                showAdd = true
            }
        }

        ui.message?.let { msg ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.clearMessage() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Active shopping runs first.
        data.trips.forEach { trip ->
            val store = storeById[trip.storeId]
            if (store != null) TripCard(trip, store, ui.busy, vm)
        }

        // The saved list, grouped by store.
        savedStores.forEach { store ->
            SavedStoreCard(store, savedByStore[store.id].orEmpty(), ui.busy, vm)
        }

        if (data.trips.isEmpty() && savedStores.isEmpty()) {
            Text(
                "The list is empty. Add something with \u201cAdd item\u201d.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAdd) {
        AddItemDialog(
            data = data,
            saving = ui.busy,
            onAdd = { name, storeId -> vm.add(name, storeId); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }

    if (showShopPicker) {
        ShopPickerDialog(
            stores = shoppableStores,
            onPick = { storeId -> vm.startTrip(storeId); showShopPicker = false },
            onDismiss = { showShopPicker = false },
        )
    }
}

@Composable
private fun TripCard(trip: GroceryTripDto, store: GroceryStoreDto, busy: Boolean, vm: GroceriesViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${store.icon}  ${store.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${trip.got}/${trip.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trip.shopper.name.takeIf { it.isNotBlank() }?.let {
                Text("Shopping: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (trip.total > 0) trip.got * 100 / trip.total else 0)
            Spacer(Modifier.height(10.dp))

            if (trip.items.isEmpty()) {
                Text("Nothing in the cart yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    trip.items.forEach { item ->
                        ItemRow(
                            item = item,
                            showTick = true,
                            busy = busy,
                            onToggle = { vm.setPurchased(item.id, !item.purchased) },
                            onRemove = { vm.remove(item.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(ACCENT)
                    .clickable(enabled = !busy) { vm.completeTrip(trip.id) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Text("Done shopping", style = MaterialTheme.typography.labelLarge, color = Color.White) }
        }
    }
}

@Composable
private fun SavedStoreCard(store: GroceryStoreDto, items: List<GroceryLineDto>, busy: Boolean, vm: GroceriesViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${store.icon}  ${store.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    ItemRow(
                        item = item,
                        showTick = false,
                        busy = busy,
                        onToggle = {},
                        onRemove = { vm.remove(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: GroceryLineDto,
    showTick: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = !busy && showTick) { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTick) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(999.dp))
                    .border(2.dp, if (item.purchased) ACCENT else MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .background(if (item.purchased) ACCENT else Color.Transparent),
            )
        }
        Text(item.icon, style = MaterialTheme.typography.bodyLarge)
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.purchased) TextDecoration.LineThrough else null,
            color = if (item.purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Box(Modifier.clickable(enabled = !busy) { onRemove() }.padding(4.dp)) {
            Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddItemDialog(
    data: GroceriesDto,
    saving: Boolean,
    onAdd: (name: String, storeId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var chosenStore by remember { mutableStateOf<String?>(null) }

    val catalogStore = data.catalog.firstOrNull {
        it.name.trim().equals(name.trim(), ignoreCase = true)
    }?.defaultStoreId
    val effectiveStore = chosenStore ?: catalogStore ?: data.stores.firstOrNull()?.id
    val single = data.stores.size <= 1

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Add an item",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !saving && name.trim().isNotEmpty() && effectiveStore != null,
                onClick = { effectiveStore?.let { onAdd(name.trim(), it) } },
            ) { Text("Add") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("What do you need?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
            }
            if (!single) {
                Text("Store", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.stores.forEach { store ->
                        val selected = store.id == effectiveStore
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (selected) ACCENT else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { chosenStore = store.id }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(store.icon, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                store.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopPickerDialog(
    stores: List<GroceryStoreDto>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Which store?",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        if (stores.isEmpty()) {
            Text("Every store already has someone shopping it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stores.forEach { store ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(store.id) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(store.icon, style = MaterialTheme.typography.titleMedium)
                        Text(store.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ---- building blocks ----

@Composable
private fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .then(if (filled) Modifier.background(ACCENT) else Modifier.border(1.dp, ACCENT, RoundedCornerShape(999.dp)))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else ACCENT, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = if (filled) Color.White else ACCENT)
    }
}

@Composable
private fun ProgressBar(pct: Int) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction = (pct.coerceIn(0, 100)) / 100f)
                .height(8.dp).clip(RoundedCornerShape(999.dp)).background(ACCENT),
        )
    }
}
