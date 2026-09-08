package com.kairos.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.util.Collections

/**
 * Tracks whether the device currently has a usable network. It listens to the
 * ConnectivityManager callbacks and maintains the live set of connected networks
 * *from the events themselves* — it never re-queries the "active network" inside
 * a callback (that read lags right after a change and used to leave the state
 * stuck). Exposed as a hot [StateFlow] the UI observes for the offline banner,
 * and read synchronously (via [isOnline]) by the offline interceptor.
 */
class NetworkMonitor(context: Context, scope: CoroutineScope) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** Point-in-time check, only for the flow's initial value before the first
     *  callback lands. */
    private fun snapshot(): Boolean {
        val manager = cm ?: return true
        val net = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val online: StateFlow<Boolean> = callbackFlow {
        val connected = Collections.synchronizedSet(HashSet<Network>())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected.add(network)
                trySend(connected.isNotEmpty())
            }
            override fun onLost(network: Network) {
                connected.remove(network)
                trySend(connected.isNotEmpty())
            }
            override fun onUnavailable() {
                trySend(connected.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
        trySend(snapshot())
        awaitClose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, snapshot())

    /** Live connectivity for the offline interceptor — same source as the UI. */
    fun isOnline(): Boolean = online.value
}
