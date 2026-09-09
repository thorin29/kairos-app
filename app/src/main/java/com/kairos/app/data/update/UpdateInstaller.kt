package com.kairos.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads an update APK and hands it to Android's package installer — all
 * in-app, no browser. The user still taps "Update" on the system installer
 * screen (a normal app can't install silently), and grants "install unknown
 * apps" for Kairos once; after that every update is a single tap.
 */
class UpdateInstaller(private val app: Context) {

    sealed interface State {
        data object Idle : State
        data object NeedsPermission : State
        data class Downloading(val pct: Int) : State
        data object Installing : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val client = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Whether the OS will let Kairos install packages (the one-time grant). */
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    /** Opens the system screen to allow Kairos to install apps. */
    fun openInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${app.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun reset() {
        _state.value = State.Idle
    }

    suspend fun downloadAndInstall(url: String) = withContext(Dispatchers.IO) {
        if (!canInstall()) {
            _state.value = State.NeedsPermission
            return@withContext
        }
        try {
            _state.value = State.Downloading(0)
            val file = File(app.cacheDir, "kairos-update.apk")
            if (file.exists()) file.delete()

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Kairos-App")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
                val body = resp.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                _state.value = State.Downloading(pct)
                            }
                        }
                    }
                }
            }

            _state.value = State.Installing
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            // The system installer is now in front; return to Idle so coming back
            // (e.g. if the user cancels) shows the normal button, not a stuck state.
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Update failed")
        }
    }
}
