package com.kairos.app.data.remote

import com.kairos.app.data.remote.dto.ApiErrorEnvelope
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    fun create(baseUrl: String, tokenProvider: () -> String?): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            // Headers only — never log bodies, which would print the token.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
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

    /** Resolve a possibly-relative avatar URL (e.g. "/api/avatars/x.png")
     *  against the configured origin. */
    fun resolveUrl(rawBase: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val origin = rawBase.trim().removeSuffix("/").removeSuffix("/api/v1")
        return origin + path
    }
}

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
            "unauthenticated" -> ApiError.Unauthenticated
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
                401 -> ApiError.Unauthenticated
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
