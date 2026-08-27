package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.application.SocialContentActorNotFoundException
import com.profiletailors.smp.publishing.application.SocialContentPostIsolationException
import com.profiletailors.smp.publishing.application.SocialContentPostNotFoundException
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidSocialContentCursorException
import com.profiletailors.smp.publishing.domain.ProviderConnectionNotAvailableException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAlreadyTerminalException
import com.profiletailors.smp.publishing.domain.PublicationCancellationNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationEditNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationRetryNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationStateTransitionException
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Suppress("TooManyFunctions")
class PublishingProblemDetailsHandler {

    @ExceptionHandler(ProviderNotConfiguredException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ProviderNotConfiguredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        PROVIDER_NOT_CONFIGURED_DETAIL,
    ).apply {
        title = "Provider not configured"
    }

    @ExceptionHandler(ProviderConnectionNotAvailableException::class)
    fun handle(exception: ProviderConnectionNotAvailableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, PROVIDER_CONNECTION_NOT_AVAILABLE_DETAIL).apply {
            title = "Provider connection unavailable"
            setProperty("reason", exception.reason?.name)
        }

    @ExceptionHandler(
        PublicationEditNotAllowedException::class,
        PublicationDeletionNotAllowedException::class,
        PublicationCancellationNotAllowedException::class,
        PublicationRetryNotAllowedException::class,
        PublicationAlreadyTerminalException::class,
        PublicationStateTransitionException::class,
    )
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: PublicationStateTransitionException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        PUBLICATION_STATE_CONFLICT_DETAIL,
    ).apply {
        title = "Publication state conflict"
    }

    @ExceptionHandler(PublicationNotFoundException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: PublicationNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        PUBLICATION_NOT_FOUND_DETAIL,
    ).apply {
        title = "Publication not found"
    }

    @ExceptionHandler(SocialContentPostNotFoundException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: SocialContentPostNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        SOCIAL_CONTENT_POST_NOT_FOUND_DETAIL,
    ).apply {
        title = "Social content post not found"
    }

    @ExceptionHandler(SocialContentActorNotFoundException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: SocialContentActorNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        SOCIAL_CONTENT_ACTOR_NOT_FOUND_DETAIL,
    ).apply {
        title = "Social content actor not found"
    }

    @ExceptionHandler(SocialContentAccessDeniedException::class)
    fun handle(exception: SocialContentAccessDeniedException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        SOCIAL_CONTENT_ACCESS_DENIED_DETAIL,
    ).apply {
        title = "Social content access denied"
        setProperty("code", exception.denial.name)
    }

    /**
     * Creates a conflict response for social content workspace isolation violations.
     *
     * @param exception The social content post isolation exception.
     * @return A problem detail with HTTP status 409 and a workspace conflict title.
     */
    @ExceptionHandler(SocialContentPostIsolationException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: SocialContentPostIsolationException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        SOCIAL_CONTENT_POST_ISOLATION_DETAIL,
    ).apply {
        title = "Social content workspace conflict"
    }

    /**
     * Creates a bad-request problem detail for an invalid request argument.
     *
     * @param exception The invalid argument exception.
     * @return A problem detail with HTTP status 400 and the exception message, or a fallback
     * message when no message is available.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handle(exception: IllegalArgumentException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.message ?: "Invalid request argument",
    ).apply {
        title = "Bad Request"
    }

    /**
     * Creates a bad-request problem detail for an expired OAuth state.
     *
     * @param exception The expired OAuth state exception.
     * @return A problem detail with an HTTP 400 status and an OAuth state expiration message.
     */
    @ExceptionHandler(ExpiredOAuthStateException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ExpiredOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, OAUTH_STATE_EXPIRED_DETAIL).apply {
            title = "OAuth state expired"
        }

    /**
     * Creates a problem detail response for an invalid OAuth state.
     *
     * @param exception The invalid OAuth state exception being handled.
     * @return A bad-request problem detail describing the invalid OAuth state.
     */
    @ExceptionHandler(InvalidOAuthStateException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, OAUTH_STATE_INVALID_DETAIL).apply {
            title = "OAuth state invalid"
        }

    /**
     * Creates a bad-request problem detail for an invalid social content cursor.
     *
     * @param exception The invalid social content cursor exception.
     * @return A problem detail with the invalid-cursor message and error code.
     */
    @ExceptionHandler(InvalidSocialContentCursorException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidSocialContentCursorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_SOCIAL_CONTENT_CURSOR_DETAIL).apply {
            title = "Invalid social content cursor"
            setProperty("errorCode", INVALID_SOCIAL_CONTENT_CURSOR_ERROR_CODE)
        }

    /**
     * Returns HTTP 503 Service Unavailable when the media context is unavailable.
     *
     * This can occur when:
     * - The media service times out responding to asset resolution requests (5-second limit)
     * - The media database or storage layer is unreachable
     *
     * The error code `MEDIA_SERVICE_UNAVAILABLE` signals the client that publication
     * creation was blocked due to infrastructure unavailability — it should NOT be
     * treated as a permanent client error.
     */
    @ExceptionHandler(MediaServiceUnavailableException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: MediaServiceUnavailableException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        MEDIA_SERVICE_UNAVAILABLE_DETAIL,
    ).apply {
        title = "Media service unavailable"
        setProperty("errorCode", "MEDIA_SERVICE_UNAVAILABLE")
    }

    /**
     * Returns HTTP 400 Bad Request when an asset is not ready for publishing use.
     *
     * This covers:
     * - Asset does not exist in the workspace
     * - Asset belongs to a different workspace
     * - Asset is not in READY status (still PROCESSING or FAILED)
     */
    @ExceptionHandler(AssetNotReadyException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: AssetNotReadyException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        ASSET_NOT_READY_DETAIL,
    ).apply {
        title = "Asset not ready"
        setProperty("errorCode", "ASSET_NOT_READY")
    }

    companion object {
        private const val PROVIDER_NOT_CONFIGURED_DETAIL = "The requested provider is not available."
        private const val PROVIDER_CONNECTION_NOT_AVAILABLE_DETAIL =
            "The requested provider cannot accept a new connection."
        private const val PUBLICATION_STATE_CONFLICT_DETAIL =
            "The publication cannot transition from its current state."
        private const val PUBLICATION_NOT_FOUND_DETAIL = "Publication not found."
        private const val SOCIAL_CONTENT_POST_NOT_FOUND_DETAIL = "Social content post not found."
        private const val SOCIAL_CONTENT_ACTOR_NOT_FOUND_DETAIL = "Social content actor not found."
        private const val SOCIAL_CONTENT_ACCESS_DENIED_DETAIL = "Social content access denied."
        private const val SOCIAL_CONTENT_POST_ISOLATION_DETAIL =
            "The social content post crossed a workspace boundary."
        private const val OAUTH_STATE_EXPIRED_DETAIL = "OAuth state has expired."
        private const val OAUTH_STATE_INVALID_DETAIL = "OAuth state is invalid."
        private const val INVALID_SOCIAL_CONTENT_CURSOR_DETAIL = "The social content calendar cursor is invalid."
        private const val INVALID_SOCIAL_CONTENT_CURSOR_ERROR_CODE = "INVALID_SOCIAL_CONTENT_CURSOR"
        private const val MEDIA_SERVICE_UNAVAILABLE_DETAIL = "Media service is unavailable."
        private const val ASSET_NOT_READY_DETAIL = "One or more assets are not ready for publishing."
    }
}
