package com.profiletailors.smp.mediaprovider.unsplash

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * Maps provider-level exceptions into safe HTTP-layer outcomes.
 *
 * The mapper is the single seam that converts Unsplash adapter failures into
 * caller-facing HTTP signals without leaking the upstream `UNSPLASH_ACCESS_KEY`,
 * raw upstream body, or the underlying cause's stack trace.
 *
 * Mapping rules (see `media-provider-unsplash` spec):
 * - [ProviderErrorException] (4xx from Unsplash)        → 502 PROVIDER_ERROR
 * - [ProviderUnavailableException] (timeout / network)  → 504 PROVIDER_UNREACHABLE
 * - [ProviderImportRejectedException] (MIME / >500 MB)  → 422 IMPORT_REJECTED
 * - 429 with Retry-After                              → 429 + Retry-After (caller passes it through)
 */
@Component
class UnsplashErrorMapper {

    /**
     * Translate an internal adapter exception into a safe, caller-facing [Outcome].
     *
     * @param exception any error raised by the Unsplash adapter.
     * @return an [Outcome] carrying the HTTP status, the public-facing message,
     *   a stable `errorCode`, and an optional `retryAfterSeconds` value (used
     *   for rate-limit propagation).
     */
    fun map(exception: UnsplashProviderException): Outcome = when (exception) {
        is ProviderImportRejectedException -> Outcome(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            publicMessage = sanitize(exception.message),
            errorCode = exception.errorCode,
            retryAfterSeconds = null,
        )

        is ProviderUnavailableException -> Outcome(
            status = HttpStatus.GATEWAY_TIMEOUT,
            publicMessage = "Unsplash is currently unreachable. Please retry shortly.",
            errorCode = exception.errorCode,
            retryAfterSeconds = null,
        )

        is ProviderErrorException -> Outcome(
            status = HttpStatus.BAD_GATEWAY,
            publicMessage = "Unsplash rejected the request. Please retry shortly.",
            errorCode = exception.errorCode,
            retryAfterSeconds = null,
        )

        is UnsplashRateLimitedException -> rateLimited(exception.retryAfterSeconds)

        else -> Outcome(
            status = HttpStatus.BAD_GATEWAY,
            publicMessage = "Unsplash adapter failed.",
            errorCode = "PROVIDER_ERROR",
            retryAfterSeconds = null,
        )
    }

    /**
     * Build a 429 + Retry-After outcome directly (used when Unsplash returns 429
     * and the adapter surfaces the upstream `Retry-After` header value in seconds).
     *
     * @param retryAfterSeconds non-negative retry-after value (>= 0).
     */
    fun rateLimited(retryAfterSeconds: Int): Outcome = Outcome(
        status = HttpStatus.TOO_MANY_REQUESTS,
        publicMessage = "Unsplash rate limit reached. Retry after $retryAfterSeconds seconds.",
        errorCode = "PROVIDER_RATE_LIMITED",
        retryAfterSeconds = retryAfterSeconds,
    )

    /**
     * Sanitize a free-form message before showing it to the client.
     *
     * Strips tokens that smell like an `UNSPLASH_ACCESS_KEY` or HTTP `Authorization`
     * header value so that future code changes cannot accidentally leak the key.
     */
    private fun sanitize(rawMessage: String?): String {
        val safe = rawMessage.orEmpty()
        // Remove anything that resembles a bearer token, auth header, or access-key token.
        val scrubbed = safe
            .replace(BEARER_TOKEN_REGEX, "Bearer ***")
            .replace(AUTHORIZATION_HEADER_REGEX, "Authorization: ***")
        return "Unsplash import rejected: $scrubbed".take(MAX_PUBLIC_MESSAGE_LENGTH)
    }

    companion object {
        private const val MAX_PUBLIC_MESSAGE_LENGTH = 512
        private val BEARER_TOKEN_REGEX = Regex("""(?i)Bearer\s+[A-Za-z0-9._\-]+""")
        private val AUTHORIZATION_HEADER_REGEX = Regex("""(?i)Authorization\s*:\s*[^\s,;]+""")
    }

    /**
     * Public-facing outcome of an adapter error.
     *
     * @property status HTTP status to return to the client.
     * @property publicMessage safe message safe to render in the HTTP body.
     * @property errorCode stable machine-readable error code from [UnsplashProviderException.errorCode].
     * @property retryAfterSeconds non-null only for rate-limit outcomes; honored by the controller
     *   as the value of the `Retry-After` response header.
     */
    data class Outcome(
        val status: HttpStatus,
        val publicMessage: String,
        val errorCode: String,
        val retryAfterSeconds: Int?,
    )
}
