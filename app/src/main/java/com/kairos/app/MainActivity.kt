package com.kairos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KairosApp).container
        handleIntent(intent)
        com.kairos.app.data.notifications.Notifications.ensureChannel(this)
        com.kairos.app.data.notifications.NotificationWorker.enqueuePeriodic(this)
        com.kairos.app.data.notifications.NotificationWorker.enqueueOnce(this)
        val settings = container.settingsStore
        setContent {
            val schemeKey by settings.themeScheme.collectAsState(initial = "TEAL")
            val dark by settings.darkMode.collectAsState(initial = false)
            val military by settings.militaryTime.collectAsState(initial = false)
            LaunchedEffect(military) { com.kairos.app.ui.common.TimeFmt.military = military }

            // Status-bar icons: dark on the light theme, light on dark.
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !dark
            }

            KairosTheme(scheme = ThemeScheme.fromKey(schemeKey), darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A notification tap carries a target screen; hand it to AppRoot. */
    private fun handleIntent(intent: Intent?) {
        val open = intent?.getStringExtra(
            com.kairos.app.data.notifications.Notifications.EXTRA_OPEN,
        ) ?: return
        (application as KairosApp).container.pendingRoute.value = open
    }
}
