package com.kairos.app.data.remote

/**
 * The single error path for the whole client. Every failed call — HTTP error,
 * the { error } envelope, a timeout, or no network — collapses to one of these,
 * so the UI never has to reason about Retrofit/OkHttp internals.
 */
sealed class ApiError(open val message: String) {
    /** 401 — token missing/invalid/expired. Triggers a forced sign-out. */
    data object Unauthenticated : ApiError("Signed out on the server.")

    /** 401 reauth_required — device still enrolled, but the account password
     *  changed; re-enter the password (keep the device). */
    data object ReauthRequired : ApiError("Please sign in again.")

    /** 403 — e.g. enrollment code invalid or expired (never says which). */
    data class Forbidden(override val message: String) : ApiError(message)

    /** 404 */
    data class NotFound(override val message: String) : ApiError(message)

    /** 429 — includes the server's Retry-After, in seconds, when present. */
    data class RateLimited(
        override val message: String,
        val retryAfterSec: Int? = null,
    ) : ApiError(message)

    /** 422 — field-level validation problems. */
    data class Validation(
        override val message: String,
        val fields: Map<String, String> = emptyMap(),
    ) : ApiError(message)

    /** 409 */
    data class Conflict(override val message: String) : ApiError(message)

    /** 5xx or an error the server didn't classify. */
    data class Server(override val message: String) : ApiError(message)

    /** No network, DNS failure, TLS problem, timeout — never reached the API. */
    data class Network(override val message: String) : ApiError(message)

    /** Anything else, including a malformed body. */
    data class Unknown(override val message: String) : ApiError(message)
}

/** Thrown by the API layer; carried through coroutines to the ViewModel. */
class ApiException(val error: ApiError) : Exception(error.message)
