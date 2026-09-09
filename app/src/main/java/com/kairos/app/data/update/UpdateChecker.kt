package com.kairos.app.data.update

import com.kairos.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A newer release the app could update to (only set when it beats the installed build). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Checks GitHub Releases for a newer build.
 *
 * The app repo is public, so this is an unauthenticated read of the latest
 * release and its `latest.json` asset — done with the app's own HTTP client
 * straight to `api.github.com`. No server involvement and, crucially, no
 * browser: nothing here opens a web page, so it works on accounts where browser
 * apps are blocked but the network is open. Downloading and installing is a
 * separate step; this only decides whether an update exists.
 */
class UpdateChecker {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    /** Non-null when a release newer than the installed build is available. */
    val available: StateFlow<UpdateInfo?> = _available

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    /** The installed build, for display next to the available one. */
    val installedName: String get() = BuildConfig.VERSION_NAME
    val installedCode: Int get() = BuildConfig.VERSION_CODE
    val installedDate: String get() = BuildConfig.BUILD_DATE

    suspend fun check() = withContext(Dispatchers.IO) {
        _checking.value = true
        try {
            val release = fetch<GhRelease>(RELEASES_URL)
            val meta = release.assets.firstOrNull { it.name == "latest.json" }
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            _available.value = if (meta != null && apk != null) {
                val latest = fetch<LatestJson>(meta.browserDownloadUrl)
                if (latest.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateInfo(
                        versionCode = latest.versionCode,
                        versionName = latest.versionName,
                        notes = latest.notes,
                        apkUrl = apk.browserDownloadUrl,
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            // Offline / rate-limited / no release yet — keep the last known state.
        } finally {
            _checking.value = false
        }
    }

    private inline fun <reified T> fetch(url: String): T {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Kairos-App") // GitHub's API rejects requests without one
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("empty body")
            return json.decodeFromString(body)
        }
    }

    private companion object {
        const val REPO = "thorin29/kairos-app"
        const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    }
}

@Serializable
private data class GhRelease(val assets: List<GhAsset> = emptyList())

@Serializable
private data class GhAsset(
    val name: String = "",
    @kotlinx.serialization.SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)

@Serializable
private data class LatestJson(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String = "",
)
