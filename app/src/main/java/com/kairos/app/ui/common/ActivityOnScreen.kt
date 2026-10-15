package com.kairos.app.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState

/**
 * True while the ACTIVITY hosting this composition is at least STARTED (on screen).
 *
 * More than one MainActivity can be alive at once — opening a file from a mail app
 * starts a fresh one while an older, stopped one lingers — and they share the
 * app-wide one-shot hand-offs in AppContainer (`pendingRoute`, `pendingIcs`). A
 * stopped activity's composition still runs, so without this gate the hidden one
 * consumed the hand-off first and the visible one showed Home.
 *
 * Reads the activity's lifecycle, never the NavBackStackEntry's: drawer navigation
 * can leave an entry below RESUMED while its screen is showing (see DECISIONS).
 */
@Composable
fun rememberActivityOnScreen(): Boolean {
    val activity = LocalContext.current.findComponentActivity() ?: return true
    val state by activity.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
