package com.kairos.app.data.remote

import com.kairos.app.data.remote.dto.ApiErrorEnvelope
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response as OkResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okio.Buffer
import java.util.UUID
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Builds an [ApiService] for a given base URL. The base URL is user-configured
 * and can change (switching servers, LAN vs public), so this is a factory rather
 * than a singleton — the session layer rebuilds the service when the URL changes.
 */
object ApiClient {

    /** Lenient JSON: unknown fields are ignored so the server can add response
     *  fields within v1 without breaking the client (docs/API.md). */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(
        baseUrl: String,
        cache: Cache? = null,
        monitor: NetworkMonitor? = null,
        queue: WriteQueue? = null,
        tokenProvider: () -> String?,
    ): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            // Headers only — never log bodies, which would print the token.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider) { baseHost(baseUrl) })
        if (cache != null && monitor != null) {
            // Read-through offline cache: when online, GET responses are stored
            // (server sends no-cache, so the network interceptor makes them
            // storable); when offline, GETs are served from the last stored copy
            // and writes are queued (or fail fast) instead of hanging.
            builder
                .cache(cache)
                .addInterceptor(OfflineInterceptor(monitor, queue))
                .addNetworkInterceptor(CacheableResponseInterceptor())
        }
        val ok = builder
            .addInterceptor(logging)
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(normalizeBase(baseUrl))
            .client(ok)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }

    /** The API root is `<base>/api/v1/`. We accept the bare host from the user
     *  and append the versioned path here, so the stored setting stays a plain
     *  origin the user can read and edit. A trailing slash is required by
     *  Retrofit for correct relative-path resolution. */
    fun normalizeBase(raw: String): String {
        var b = raw.trim().removeSuffix("/")
        if (b.endsWith("/api/v1")) b = b.removeSuffix("/api/v1")
        return "$b/api/v1/"
    }

    /** The host of the configured server, used to scope the auth token so it's
     *  only ever attached to requests going to the server this device is signed
     *  into (see [AuthInterceptor]). */
    fun baseHost(rawBase: String): String? =
        runCatching { normalizeBase(rawBase).toHttpUrlOrNull()?.host }.getOrNull()

    /** Resolve a possibly-relative avatar URL (e.g. "/api/avatars/x.png")
     *  against the configured origin. */
    fun resolveUrl(rawBase: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val origin = rawBase.trim().removeSuffix("/").removeSuffix("/api/v1")
        return origin + path
    }
}

/** In-flight background GET refreshes, so the UI can show a thin progress line
 *  while data is being refreshed behind already-visible content. */
object RefreshTracker {
    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active.asStateFlow()
    fun begin() { _active.update { it + 1 } }
    fun end() { _active.update { (it - 1).coerceAtLeast(0) } }
}

/** Tracks whether the Kairos server is reachable while the phone itself is
 *  online. Set when a live GET comes back 502/503/504 (proxy up, upstream down)
 *  and we serve a cached copy instead; cleared on the next successful live
 *  response. Lets the UI say "server unavailable" rather than showing an error. */
object ServerStatusTracker {
    private val _unavailable = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()
    fun markUnavailable() { _unavailable.value = true }
    fun markReachable() { _unavailable.value = false }
}

/** Network interceptor (online path only): make GET responses storable in the
 *  disk cache even though the server marks them no-cache. A tiny max-age keeps
 *  data effectively live online while giving offline a copy to fall back on. */
private class CacheableResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkResponse {
        val res = chain.proceed(chain.request())
        if (chain.request().method != "GET") return res
        return res.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", "public, max-age=0")
            .build()
    }
}

/** Application interceptor: when offline, serve GETs from the disk cache (any
 *  age) and capture writes into the [WriteQueue] so they replay on reconnect
 *  (returning a synthetic success so the action isn't lost or shown as an
 *  error). Auth calls are never queued — they only make sense online. */
private class OfflineInterceptor(
    private val monitor: NetworkMonitor,
    private val queue: WriteQueue?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkResponse {
        val req = chain.request()
        if (monitor.isOnline()) {
            if (req.method != "GET") return chain.proceed(req)
            // Online GET: hit the network, but count it so the UI can show a thin
            // refresh line, and if the server is actually unreachable/slow (Wi-Fi
            // up but Kairos down) fall back to the cached copy instead of failing.
            RefreshTracker.begin()
            try {
                val live = chain.proceed(req)
                // Proxy answered but the Kairos upstream is down/slow (502/503/504):
                // treat it like being offline and serve the last cached copy if we
                // have one, so a rebooting host doesn't blank the screen.
                if (live.code == 502 || live.code == 503 || live.code == 504) {
                    val cachedReq = req.newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=$OFFLINE_MAX_STALE")
                        .build()
                    val cachedRes = chain.proceed(cachedReq)
                    if (cachedRes.code == 504) {
                        // Nothing cached — keep the real server error for the UI.
                        cachedRes.close()
                        return live
                    }
                    live.close()
                    ServerStatusTracker.markUnavailable()
                    return cachedRes
                }
                if (live.isSuccessful) ServerStatusTracker.markReachable()
                return live
            } catch (e: IOException) {
                val cached = req.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=$OFFLINE_MAX_STALE")
                    .build()
                val res = chain.proceed(cached)
                if (res.code == 504) {
                    res.close()
                    throw e
                }
                return res
            } finally {
                RefreshTracker.end()
            }
        }

        if (req.method != "GET") {
            val path = req.url.encodedPath
            // Multipart uploads (avatar photo) carry binary bytes that can't be
            // stored in the text queue without corruption, so they need a live
            // connection rather than being queued.
            val isMultipart = req.body?.contentType()?.type == "multipart"
            val queueable = queue != null && !isMultipart &&
                !path.contains("/auth/") && !path.endsWith("/revoke")
            if (queueable) {
                val bodyStr = req.body?.let { b ->
                    val buffer = Buffer()
                    b.writeTo(buffer)
                    buffer.readUtf8()
                }
                // Deleting an item created offline and not yet synced: it only
                // exists as a queued create, so drop that create instead of queuing
                // a delete against an id the server never had (add-then-delete = no-op).
                val isDelete = path.endsWith("/delete") || path.endsWith("/remove")
                val cancelId = if (isDelete) {
                    Regex("temp-([0-9a-fA-F-]+)").find(req.url.toString() + (bodyStr ?: ""))?.groupValues?.get(1)
                } else {
                    null
                }
                if (cancelId != null) {
                    runBlocking { queue!!.remove(cancelId) }
                    return synthetic(req)
                }
                runBlocking {
                    queue!!.enqueue(
                        PendingWrite(
                            id = UUID.randomUUID().toString(),
                            method = req.method,
                            // Store the server-relative path only; the replayer
                            // resolves it against the current base so a queued
                            // write never targets a stale/other host.
                            url = req.url.encodedPath +
                                (req.url.encodedQuery?.let { "?$it" } ?: ""),
                            body = bodyStr,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                return synthetic(req)
            }
            throw IOException("You're offline. Reconnect to make changes.")
        }
        val offline = req.newBuilder()
            .header("Cache-Control", "public, only-if-cached, max-stale=$OFFLINE_MAX_STALE")
            .build()
        val res = chain.proceed(offline)
        if (res.code == 504) {
            // only-if-cached with nothing stored: this page was never loaded online.
            res.close()
            throw IOException("You're offline \u2014 this page hasn't been cached yet.")
        }
        return res
    }

    /** A 200 {} response so a queued (or cancelled) offline write looks successful. */
    private fun synthetic(req: okhttp3.Request): OkResponse =
        OkResponse.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Queued offline")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
}

private const val OFFLINE_MAX_STALE = 60 * 60 * 24 * 30 // 30 days

/**
 * Maps a Retrofit [Response] to either its body or a thrown [ApiException].
 * This is the one place HTTP status + the { error } envelope become an
 * [ApiError]. Callers get a plain value or a typed failure.
 */
fun <T> Response<T>.bodyOrThrow(): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(ApiError.Unknown("Empty response body."))
    }
    val raw = errorBody()?.string()
    val parsed = raw?.let {
        runCatching { ApiClient.json.decodeFromString<ApiErrorEnvelope>(it).error }.getOrNull()
    }
    val message = parsed?.message ?: "Request failed (${code()})."
    throw ApiException(
        when (parsed?.code) {
            "missing_bearer" -> ApiError.Server(message)
            "unauthenticated", "invalid_token", "device_revoked", "device_expired" ->
                // A genuine rejected device credential means the token is dead
                // (-> DeviceInvalid). But if the request went out with NO
                // Authorization header, it's a missing-credential race, not a lost
                // enrollment — treat it as recoverable so it can never wipe this
                // phone. (missing_bearer is the explicit server code for that; the
                // header check also covers older servers and login/reauth 401s,
                // whose human-readable message is preserved for their screens.)
                if (raw().request.header("Authorization") == null) ApiError.Server(message)
                else ApiError.Unauthenticated
            "reauth_required" -> ApiError.ReauthRequired
            "forbidden" -> ApiError.Forbidden(message)
            "not_found" -> ApiError.NotFound(message)
            "rate_limited" -> ApiError.RateLimited(
                message,
                headers()["Retry-After"]?.toIntOrNull(),
            )
            "validation" -> ApiError.Validation(message, parsed.fields ?: emptyMap())
            "conflict" -> ApiError.Conflict(message)
            "server" -> ApiError.Server(message)
            else -> when (code()) {
                // A 401 that is NOT a Kairos { error: { code: "unauthenticated" } }
                // envelope is almost always the gateway (Authelia / Traefik /
                // Cloudflare), not a dead device token. Treat it as a recoverable
                // server/proxy error so a transient proxy 401 can never wipe this
                // phone's enrollment. Only the genuine Kairos "unauthenticated"
                // code (handled above) clears the token.
                401 -> ApiError.Server(message)
                in 500..599 -> ApiError.Server(message)
                else -> ApiError.Unknown(message)
            }
        },
    )
}

/** Runs an API call, turning transport failures (no network, timeout, TLS) into
 *  [ApiError.Network] so the whole call site has a single failure type. */
suspend fun <T> apiCall(block: suspend () -> Response<T>): T {
    val response = try {
        block()
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        throw ApiException(ApiError.Network(e.message ?: "Network error."))
    } catch (e: Exception) {
        throw ApiException(ApiError.Unknown(e.message ?: "Unexpected error."))
    }
    return response.bodyOrThrow()
}
