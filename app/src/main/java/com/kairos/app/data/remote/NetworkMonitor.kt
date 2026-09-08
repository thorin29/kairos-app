package com.kairos.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Tracks whether the device currently has a usable network. Used two ways:
 * synchronously by the OkHttp offline interceptor (to force the disk cache), and
 * as a [StateFlow] the UI observes to show the offline banner.
 */
class NetworkMonitor(context: Context, scope: CoroutineScope) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** Best-effort synchronous check. Assumes online if we genuinely can't tell,
     *  so a permissions/edge case never wrongly forces stale cache. */
    fun isOnline(): Boolean {
        val manager = cm ?: return true
        val net = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isOnline())
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        trySend(isOnline())
        awaitClose { cm?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, isOnline())
}
