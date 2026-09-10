package com.kairos.app.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AddressDuplicateDto
import com.kairos.app.data.remote.dto.SavedAddressDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.ui.common.SentenceCaps
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * The event "Where" field, backed by the saved-address book. Tapping in shows
 * the list (grouped-ish by relevance); typing filters on name and address. A
 * brand-new address offers "Save for next time", which submits to the server —
 * a parent/admin's saves straight away, anyone else's is sent for approval —
 * with a "did you mean?" check first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationField(
    value: String,
    onValueChange: (String) -> Unit,
    repo: SessionRepository,
) {
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf<List<SavedAddressDto>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }

    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveNavByName by remember { mutableStateOf(true) }
    var dup by remember { mutableStateOf<AddressDuplicateDto?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.listSavedAddresses() }.getOrNull()?.let {
            addresses = it.addresses
        }
    }

    // When suggestions appear, scroll them above the soft keyboard.
    val bringIntoView = remember { BringIntoViewRequester() }

    val q = value.trim().lowercase()
    val matches = if (q.isEmpty()) {
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
    val canSave = value.trim().isNotEmpty() && !exact

    LaunchedEffect(focused, matches.size, canSave) {
        if (focused && (matches.isNotEmpty() || canSave)) {
            delay(60)
            runCatching { bringIntoView.bringIntoView() }
        }
    }

    fun submit(force: Boolean) {
        if (saveName.isBlank()) return
        submitting = true
        scope.launch {
            val res = runCatching {
                repo.submitAddress(saveName.trim(), value.trim(), saveNavByName, force)
            }.getOrNull()
            submitting = false
            if (res == null) return@launch
            if (!res.ok && res.duplicate != null) {
                dup = res.duplicate
                return@launch
            }
            addresses = addresses + SavedAddressDto(res.id, saveName.trim(), value.trim(), saveNavByName)
            savedNote = if (res.status == "PENDING") "Sent for approval." else "Saved for next time."
            showSave = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (focused && (matches.isNotEmpty() || canSave)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, bottom = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    matches.take(6).forEach { a ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(a.address)
                                    focused = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                a.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                a.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (canSave) {
                        Text(
                            "＋ Save \"${value.trim()}\" for next time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    saveName = ""
                                    saveNavByName = true
                                    dup = null
                                    showSave = true
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                KairosIcons.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    savedNote = null
                },
                keyboardOptions = SentenceCaps,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "Add location",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }

        savedNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp),
            )
        }
            }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showSave = false },
            title = { Text("Save address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        value.trim(),
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
                        TextButton(onClick = {
                            onValueChange(dup!!.address)
                            showSave = false
                        }) { Text("Use that") }
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
