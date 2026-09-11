package com.kairos.app.data.remote

import com.kairos.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to a request — but only when the
 * request is going to the configured server. The token comes from an in-memory
 * provider (kept current by the session layer) rather than a blocking read of
 * encrypted storage on the network thread.
 *
 * The host check ([allowedHostProvider]) means a queued write that somehow
 * carried a stale absolute URL, or any request to another origin, can never
 * leak this device's token to a different server. `/auth/enroll` and `/meta`
 * simply run before any token exists, so no path exemption is needed: with no
 * token, no header is added. When [allowedHostProvider] returns null (host
 * unknown) the check is skipped so the app still works.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val allowedHostProvider: () -> String? = { null },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val allowed = allowedHostProvider()
        val host = chain.request().url.host
        val attach = !token.isNullOrBlank() &&
            (allowed.isNullOrBlank() || host.equals(allowed, ignoreCase = true))
        val request = if (attach) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                // Report this phone's app version so the web Family page can list it.
                .header("X-Client-Build", BuildConfig.VERSION_CODE.toString())
                .header("X-Client-Version", BuildConfig.VERSION_NAME)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
