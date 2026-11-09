package com.kairos.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.kairos.app.data.diag.Breadcrumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Tracks whether the device currently has a usable network. It listens to the
 * ConnectivityManager callbacks and maintains the live set of connected networks
 * *from the events themselves* — it never re-queries the "active network" inside
 * a callback (that read lags right after a change and used to leave the state
 * stuck). Exposed as a hot [StateFlow] the UI observes for the offline banner,
 * and read synchronously (via [isOnline]) by the offline interceptor.
 *
 * v0.459: the callbacks alone could leave the banner saying "offline" while the
 * app was plainly talking to the server — after the phone slept, Android can
 * unblock the app's network without a fresh onAvailable. So the state is also
 * corrected by evidence: any answer from the server ([confirmOnline]), coming
 * back to the app ([recheck]), and onBlockedStatusChanged. Every change is a
 * diagnostics breadcrumb, with what caused it.
 */
class NetworkMonitor(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val connected = Collections.synchronizedSet(HashSet<Network>())

    /** Point-in-time check, for the initial value and [recheck]. */
    private fun snapshot(): Boolean {
        val manager = cm ?: return true
        val net = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _online = MutableStateFlow(snapshot())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** The app is on screen (v0.462): MainActivity sets it on resume/pause. */
    @Volatile var foreground: Boolean = false

    private fun set(value: Boolean, why: String) {
        if (_online.value == value) return
        _online.value = value
        // Android pauses a backgrounded app's network and resumes it for every
        // widget refresh; logging each pair filled diagnostics with noise. Only
        // changes while the app is in use, or real losses, are kept (v0.462).
        val routine = why == "blocked" || why == "unblocked"
        if (foreground || !routine) Breadcrumbs.drop("network ${if (value) "online" else "offline"}: $why")
    }

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected.add(network)
                set(value = true, why = "available")
            }
            override fun onLost(network: Network) {
                connected.remove(network)
                if (connected.isEmpty()) set(value = false, why = "lost")
            }
            override fun onUnavailable() {
                if (connected.isEmpty()) set(value = false, why = "unavailable")
            }
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                // Doze / data saver blocks this app's network without losing it;
                // unblocking is the "back" signal that onAvailable won't repeat.
                if (blocked) {
                    connected.remove(network)
                    if (connected.isEmpty()) set(value = false, why = "blocked")
                } else {
                    connected.add(network)
                    set(value = true, why = "unblocked")
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
    }

    /** The server answered (any HTTP status): the network is plainly there. */
    fun confirmOnline(why: String = "server answered") = set(value = true, why = why)

    /** Re-read the system's view, e.g. when the app comes back to the front. */
    fun recheck(why: String = "app resumed") {
        if (snapshot()) set(value = true, why = why)
    }

    /** Live connectivity for the offline interceptor: a *fresh* synchronous read
     *  each call, so a request made the instant the radio drops is decided on the
     *  current state (and fails fast) rather than a slightly-stale flow value. The
     *  reactive [online] flow drives the UI banner. */
    fun isOnline(): Boolean = snapshot() || online.value
    // Either says online (v0.431): right after the app comes back from the
    // background the system can report no active network for it for a moment
    // while the callbacks still hold a connected one. Treating that moment as
    // offline queued writes that then sat until the app was restarted.
}
