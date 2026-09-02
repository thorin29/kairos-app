package com.kairos.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to every request when a token is
 * held. The token comes from an in-memory provider (kept current by the session
 * layer) rather than a blocking read of encrypted storage on the network thread.
 *
 * `/auth/enroll` and `/meta` simply run before any token exists, so no explicit
 * path exemption is needed: when there is no token, no header is added.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
