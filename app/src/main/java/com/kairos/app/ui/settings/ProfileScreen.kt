package com.kairos.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private data class Xf(val tx: Float, val ty: Float, val scale: Float)

private fun parseXf(value: String?): Xf {
    val p = value?.trim()?.split(" ") ?: return Xf(0f, 0f, 1f)
    if (p.size != 3) return Xf(0f, 0f, 1f)
    val tx = p[0].toFloatOrNull() ?: 0f
    val ty = p[1].toFloatOrNull() ?: 0f
    val sc = p[2].toFloatOrNull() ?: 1f
    return Xf(tx, ty, sc)
}

private fun fmtXf(tx: Float, ty: Float, scale: Float): String {
    val s = if (scale == scale.toInt().toFloat()) scale.toInt().toString() else "%.2f".format(scale)
    return "${tx.toInt()} ${ty.toInt()} $s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(person: PersonDto, onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = container.sessionRepository.baseUrlRaw

    val lastCustom by container.settingsStore.lastCustomColor.collectAsState(initial = null)

    var selected by remember { mutableStateOf(person.color ?: PERSON_COLORS.first()) }
    var savingColor by remember { mutableStateOf(false) }
    var savedTick by remember { mutableIntStateOf(0) }
    var busyPhoto by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }

    // Cropper state.
    var cropModel by remember { mutableStateOf<Any?>(null) }
    var cropNewUri by remember { mutableStateOf<Uri?>(null) }
    var cropInit by remember { mutableStateOf(Xf(0f, 0f, 1f)) }
    var showPicker by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            cropNewUri = uri
            cropModel = uri
            cropInit = Xf(0f, 0f, 1f)
        }
    }

    fun uploadCrop(tx: Float, ty: Float, scale: Float) {
        val newUri = cropNewUri
        cropModel = null
        if (!container.sessionRepository.isOnline()) {
            cropNewUri = null
            photoMessage = "You're offline \u2014 connect to change your photo."
            return
        }
        scope.launch {
            busyPhoto = true
            photoMessage = null
            val ok = runCatching {
                val bytesAndMime = newUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) bytes to mime else null
                    }
                }
                container.sessionRepository.setAvatar(
                    imageBytes = bytesAndMime?.first,
                    mime = bytesAndMime?.second,
                    position = fmtXf(tx, ty, scale),
                )
                container.sessionRepository.refreshPerson()
            }.isSuccess
            cropNewUri = null
            busyPhoto = false
            if (!ok) photoMessage = "Couldn't save the photo. Please try again."
        }
    }

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
                item { AvatarPreview(person = person, ring = parseHex(selected), busy = busyPhoto) }
                item {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !busyPhoto,
                            modifier = Modifier.weight(1f),
                        ) { Text("Change photo") }
                        if (person.avatarUrl != null && base != null) {
                            OutlinedButton(
                                onClick = {
                                    cropNewUri = null
                                    cropInit = parseXf(person.avatarPosition)
                                    cropModel = ApiClient.resolveUrl(base, person.avatarUrl)
                                },
                                enabled = !busyPhoto,
                                modifier = Modifier.weight(1f),
                            ) { Text("Adjust framing") }
                        }
                    }
                }
                photoMessage?.let { msg ->
                    item {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "User color",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Avatar color shared across the platform.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                            )
                            SwatchGrid(
                                selected = selected,
                                lastCustom = lastCustom,
                                onPick = { selected = it },
                                onCustom = { showPicker = true },
                            )
                        }
                    }
                }

                item {
                    val dirty = !selected.equals(person.color, ignoreCase = true)
                    Button(
                        onClick = {
                            if (savingColor) return@Button
                            scope.launch {
                                savingColor = true
                                runCatching {
                                    container.sessionRepository.setMyColor(selected)
                                    if (container.sessionRepository.isOnline()) {
                                        container.sessionRepository.refreshPerson()
                                    }
                                }
                                savingColor = false
                                savedTick++
                            }
                        },
                        enabled = dirty && !savingColor,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (savingColor) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (!dirty && savedTick > 0) "Saved \u2713" else "Save color")
                        }
                    }
                }
            }
        }
    }

    cropModel?.let { model ->
        AvatarCropDialog(
            model = model,
            initialTx = cropInit.tx,
            initialTy = cropInit.ty,
            initialScale = cropInit.scale,
            onApply = { tx, ty, scale -> uploadCrop(tx, ty, scale) },
            onCancel = { cropModel = null; cropNewUri = null },
        )
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = parseHex(selected),
            onPick = { _, hex ->
                selected = hex
                scope.launch { container.settingsStore.setLastCustomColor(hex) }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AvatarPreview(person: PersonDto, ring: Color, busy: Boolean) {
    val container = rememberContainer()
    val base = container.sessionRepository.baseUrlRaw
    val url = person.avatarUrl
    val xf = parseXf(person.avatarPosition)
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .graphicsLayer {
                            scaleX = xf.scale
                            scaleY = xf.scale
                            translationX = xf.tx / 100f * size.width
                            translationY = xf.ty / 100f * size.height
                        },
                )
            } else {
                Text(
                    person.shortName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ring,
                )
            }
        }
        Box(Modifier.fillMaxSize().border(3.dp, ring, CircleShape))
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = ring,
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
private fun SwatchGrid(
    selected: String,
    lastCustom: String?,
    onPick: (String) -> Unit,
    onCustom: () -> Unit,
) {
    val paletteLower = PERSON_COLORS.map { it.lowercase() }.toSet()
    val selLower = selected.lowercase()
    // A single "recent custom" slot: the selected custom, else the last custom.
    val customSlot = when {
        selLower !in paletteLower -> selected
        lastCustom != null && lastCustom.lowercase() !in paletteLower -> lastCustom
        else -> null
    }
    val swatches = PERSON_COLORS + listOfNotNull(customSlot)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        swatches.plus("__custom__").chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { hex ->
                    if (hex == "__custom__") {
                        Box(
                            Modifier
                                .weight(1f)
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onCustom() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Custom color",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val isSel = hex.equals(selected, ignoreCase = true)
                        Box(
                            Modifier
                                .weight(1f)
                                .size(44.dp)
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
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
                            }
                        }
                    }
                }
                // Pad the row so weights align when it isn't full.
                repeat(5 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
