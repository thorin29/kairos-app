package com.kairos.app.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AddressDuplicateDto
import com.kairos.app.data.remote.dto.SavedAddressDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.ui.common.SentenceCaps
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * The event "Where" field. Tapping it opens a full-screen address search (a
 * "search mode") rather than a dropdown: the search box sits at the top and the
 * matching saved addresses fill the space above the keyboard as a scrollable
 * list, so the keyboard can never cover results. A brand-new address offers
 * "save for next time" with a duplicate check.
 */
@Composable
fun LocationField(
    value: String,
    onOpenSearch: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenSearch() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            KairosIcons.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            value.ifBlank { "Add location" },
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun AddressSearchScreen(
    initial: String,
    repo: SessionRepository,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initial) }
    var addresses by remember { mutableStateOf<List<SavedAddressDto>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveNavByName by remember { mutableStateOf(true) }
    var dup by remember { mutableStateOf<AddressDuplicateDto?>(null) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.listSavedAddresses() }.getOrNull()?.let { addresses = it.addresses }
        runCatching { focusRequester.requestFocus() }
    }

    val q = query.trim().lowercase()
    val matches =
        if (q.isEmpty()) {
            addresses.sortedBy { it.name }
        } else {
            addresses
                .filter { it.name.lowercase().contains(q) || it.address.lowercase().contains(q) }
                .sortedWith(
                    compareByDescending<SavedAddressDto> {
                        it.name.lowercase().startsWith(q) || it.address.lowercase().startsWith(q)
                    }.thenBy { it.name },
                )
        }
    val exact = q.isNotEmpty() && addresses.any { it.address.trim().lowercase() == q }
    val canSave = query.trim().isNotEmpty() && !exact

    fun submit(force: Boolean) {
        if (saveName.isBlank()) return
        submitting = true
        scope.launch {
            val res = runCatching {
                repo.submitAddress(saveName.trim(), query.trim(), saveNavByName, force)
            }.getOrNull()
            submitting = false
            if (res == null) return@launch
            if (!res.ok && res.duplicate != null) {
                dup = res.duplicate
                return@launch
            }
            showSave = false
            onPick(query.trim())
        }
    }

    BackHandler(onBack = onDismiss)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CloseButton(onClick = onDismiss)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search or type an address",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }

            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(matches, key = { it.id }) { a ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(a.address) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            a.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            a.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                if (canSave) {
                    item {
                        Text(
                            "＋ Save \"${query.trim()}\" for next time",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    saveName = ""
                                    saveNavByName = true
                                    dup = null
                                    showSave = true
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showSave = false },
            title = { Text("Save address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        query.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Short name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = saveNavByName, onCheckedChange = { saveNavByName = it })
                        Text(
                            "Open in maps by name (a business). Turn off for a home.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    dup?.let { d ->
                        Text(
                            "Already saved as \"${d.name}\" — ${d.address}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                if (dup != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onPick(dup!!.address) }) { Text("Use that") }
                        TextButton(enabled = !submitting, onClick = { submit(true) }) {
                            Text("Save anyway")
                        }
                    }
                } else {
                    TextButton(
                        enabled = !submitting && saveName.isNotBlank(),
                        onClick = { submit(false) },
                    ) { Text("Save") }
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { showSave = false }) { Text("Cancel") }
            },
        )
    }
}

/** A round ✕ close target for the search bar. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("\u2715", style = MaterialTheme.typography.titleMedium)
    }
}
