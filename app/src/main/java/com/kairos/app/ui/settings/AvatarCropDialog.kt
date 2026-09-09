package com.kairos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.ui.common.rememberContainer

/**
 * Circular pan/zoom cropper. Produces the web's "tx ty scale" framing: tx/ty are
 * percentages of the frame, scale a multiplier — applied the same way avatars are
 * drawn everywhere, so what you see here is what shows.
 */
@Composable
fun AvatarCropDialog(
    model: Any?,
    initialTx: Float,
    initialTy: Float,
    initialScale: Float,
    onApply: (tx: Float, ty: Float, scale: Float) -> Unit,
    onCancel: () -> Unit,
) {
    val container = rememberContainer()
    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(1f, 4f)) }
    var tx by remember { mutableFloatStateOf(initialTx) }
    var ty by remember { mutableFloatStateOf(initialTy) }

    val frameDp = 288.dp
    val framePx = with(LocalDensity.current) { frameDp.toPx() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Frame your photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Drag to move \u00b7 pinch to zoom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    Modifier
                        .size(frameDp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                tx = (tx + pan.x / framePx * 100f).coerceIn(-100f, 100f)
                                ty = (ty + pan.y / framePx * 100f).coerceIn(-100f, 100f)
                            }
                        },
                ) {
                    SubcomposeAsyncImage(
                        model = model,
                        imageLoader = container.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = tx / 100f * framePx
                                translationY = ty / 100f * framePx
                            },
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = {
                        // Store scale to 2 decimals, matching the web's format.
                        val s = (Math.round(scale * 100f) / 100f)
                        onApply(Math.round(tx).toFloat(), Math.round(ty).toFloat(), s)
                    }) { Text("Apply") }
                }
            }
        }
    }
}
