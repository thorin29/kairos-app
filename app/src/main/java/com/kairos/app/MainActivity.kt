package com.kairos.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kairos.app.ui.common.dismissKeyboardOnTapOutside
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.kairos.app.ui.AppRoot
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.ui.theme.ThemeScheme
import com.kairos.app.di.PendingIcs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KairosApp).container
        // A recreated activity (rotation, restore) must not import the same
        // calendar file a second time.
        handleIntent(intent, fresh = savedInstanceState == null)
        com.kairos.app.data.notifications.Notifications.ensureChannel(this)
        com.kairos.app.data.notifications.NotificationWorker.enqueuePeriodic(this)
        com.kairos.app.data.notifications.NotificationWorker.enqueueAfterMidnight(this)
        com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(this)
        val settings = container.settingsStore
        setContent {
            val schemeKey by settings.themeScheme.collectAsState(initial = "TEAL")
            val mode by settings.themeMode.collectAsState(initial = "SYSTEM")
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (mode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            val timeFormat by settings.timeFormat.collectAsState(initial = "SYSTEM")
            val ctx = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(timeFormat) {
                com.kairos.app.ui.common.TimeFmt.military = when (timeFormat) {
                    "H24" -> true
                    "H12" -> false
                    else -> android.text.format.DateFormat.is24HourFormat(ctx)
                }
            }

            // Status-bar icons: dark on the light theme, light on dark.
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !dark
            }

            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            KairosTheme(scheme = ThemeScheme.fromKey(schemeKey), darkTheme = dark) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .dismissKeyboardOnTapOutside(focusManager),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(container = container)
                }
            }
        }
    }

    /** Leaving the app refreshes the home-screen widget, so anything changed
     *  in here (a task ticked, an event added) is on it when you get back. */
    override fun onStop() {
        super.onStop()
        com.kairos.app.widget.KairosWidget.refresh(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, fresh = true)
    }

    /** A notification tap carries a target screen; hand it to AppRoot. */
    private fun handleIntent(intent: Intent?, fresh: Boolean) {
        val container = (application as KairosApp).container
        // A widget event tap carries the event itself, so the Calendar screen
        // can open its details whatever day it is showing.
        intent?.getStringExtra(com.kairos.app.widget.KairosWidget.EXTRA_EVENT)?.let { json ->
            runCatching {
                com.kairos.app.data.remote.ApiClient.json.decodeFromString(
                    com.kairos.app.data.remote.dto.CalEventDto.serializer(),
                    json,
                )
            }.getOrNull()?.let {
                container.pendingCalendarOpen.value = com.kairos.app.di.CalendarOpen.ShowEvent(it)
            }
        }
        // A notification tap carries a target screen.
        intent?.getStringExtra(
            com.kairos.app.data.notifications.Notifications.EXTRA_OPEN,
        )?.let { container.pendingRoute.value = it }
        // A kairos://join?token=... invite deep link.
        val data = intent?.data
        if (data != null && data.scheme == "kairos" && data.host == "join") {
            data.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let {
                container.pendingJoinToken.value = it
            }
        }
        if (fresh) handleCalendarFile(intent)
    }

    /** An .ics opened (VIEW) or shared (SEND) into the app: read it off the main
     *  thread — a mail app may still be fetching the attachment — then hand it
     *  to the Calendar screen, which waits for sign-in if need be. */
    private fun handleCalendarFile(intent: Intent?) {
        intent ?: return
        val container = (application as KairosApp).container
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            else -> null
        }
        val sharedText = if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.contains("BEGIN:VCALENDAR", ignoreCase = true) }
        } else null
        if (uri == null && sharedText == null) return
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) {
                if (uri == null) {
                    PendingIcs(sharedText)
                } else {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readNBytesCompat(com.kairos.app.ui.calendar.ICS_MAX_BYTES + 1)
                            if (bytes.size > com.kairos.app.ui.calendar.ICS_MAX_BYTES) {
                                PendingIcs(null, tooBig = true)
                            } else {
                                PendingIcs(String(bytes, Charsets.UTF_8))
                            }
                        }
                    }.getOrNull() ?: PendingIcs(null)
                }
            }
            container.pendingIcs.value = pending
            container.pendingRoute.value = "calendar"
        }
    }
}

/** Up to [limit] bytes (InputStream.readNBytes is API 33+; minSdk is 26). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
