package com.kairos.app.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Android 17 (API level 37) Local Network Protection: an app targeting API 37+
 * cannot reach a private/LAN address without the ACCESS_LOCAL_NETWORK runtime
 * permission. Kairos supports self-hosted servers that may live on the LAN, so
 * we request the permission when the configured server address looks local.
 * Public domains never trigger it, so nothing changes for internet-facing setups.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /**
     * True when we should ask for ACCESS_LOCAL_NETWORK before reaching [rawUrl]:
     * only on Android 17+, only for an obviously-local host, and only if it isn't
     * already granted. A normal hostname that resolves to a LAN address through
     * split DNS is NOT caught here — detecting that reliably needs the reactive
     * (connection-failure) path, which is a separate follow-up.
     */
    fun needsPermission(context: Context, rawUrl: String?): Boolean {
        if (Build.VERSION.SDK_INT < 37) return false
        if (rawUrl == null || !isLikelyLocalHost(rawUrl)) return false
        return ContextCompat.checkSelfPermission(context, PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Best-effort check for an obviously-local server address: a private IPv4
     * literal, loopback, link-local, or a local-only hostname/TLD. Deliberately
     * excludes Tailscale's 100.64.0.0/10 range — Tailscale runs as a VPN on
     * Android, and VPN traffic is outside Local Network Protection.
     */
    fun isLikelyLocalHost(rawUrl: String): Boolean {
        val host = rawUrl.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        if (host == "localhost") return true
        if (host.endsWith(".local") || host.endsWith(".home") ||
            host.endsWith(".lan") || host.endsWith(".internal")
        ) {
            return true
        }
        return isPrivateIpv4(host)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val o = parts.map { it.toIntOrNull() ?: return false }
        if (o.any { it !in 0..255 }) return false
        return when {
            o[0] == 10 -> true // 10.0.0.0/8
            o[0] == 127 -> true // loopback
            o[0] == 172 && o[1] in 16..31 -> true // 172.16.0.0/12
            o[0] == 192 && o[1] == 168 -> true // 192.168.0.0/16
            o[0] == 169 && o[1] == 254 -> true // link-local
            else -> false
        }
    }
}
