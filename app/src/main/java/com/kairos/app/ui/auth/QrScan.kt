package com.kairos.app.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.kairos.app.ui.nav.KairosIcons
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** What a scanned (or pasted) invitation holds: the code, and the server's
 *  address when the QR carries one (web 0.719+). */
data class JoinScan(val token: String, val server: String?)

/** `kairos://join?token=…&server=…`, a bare code, or a pasted link. */
fun parseJoin(raw: String): JoinScan? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    if (!text.contains("token=")) return JoinScan(token = text, server = null)
    val query = text.substringAfter("?", missingDelimiterValue = text)
    val params = query.split("&").mapNotNull { part ->
        val k = part.substringBefore("=", missingDelimiterValue = "")
        if (k.isEmpty()) null else k to java.net.URLDecoder.decode(part.substringAfter("="), "UTF-8")
    }.toMap()
    val token = params["token"]?.trim().orEmpty()
    if (token.isEmpty()) return null
    val server = params["server"]?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    return JoinScan(token = token, server = server)
}

/**
 * Scan code (v0.519): the camera, full screen, reading a QR with ZXing. Asks
 * for the camera when it opens; [onResult] gets the QR's text once.
 */
@Composable
fun QrScanDialog(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (granted) CameraScan(onResult = onResult)
                if (denied) {
                    Text(
                        "Camera access is off. Type the code instead.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(240.dp)
                            .border(width = 3.dp, color = Color.White, shape = RoundedCornerShape(16.dp)),
                    )
                    Text(
                        "Point at the code",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 48.dp),
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp),
                ) {
                    Icon(KairosIcons.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CameraScan(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val found = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener(
                {
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(view.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val reader = QRCodeReader()
                    analysis.setAnalyzer(executor) { image ->
                        val text = decode(image, reader)
                        image.close()
                        if (text != null && found.compareAndSet(false, true)) view.post { onResult(text) }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            view
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The frame's brightness plane, read as a QR. Null when there isn't one. */
private fun decode(image: ImageProxy, reader: QRCodeReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
