package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.application.PublicationNotFoundException
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAlreadyTerminalException
import com.profiletailors.smp.publishing.domain.PublicationCancellationNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationEditNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationRetryNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationStateTransitionException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PublishingProblemDetailsHandler {

    companion object {
        private const val PROVIDER_NOT_CONFIGURED_DETAIL = "The requested provider is not available."
        private const val PUBLICATION_STATE_CONFLICT_DETAIL =
            "The publication cannot transition from its current state."
        private const val PUBLICATION_NOT_FOUND_DETAIL = "Publication not found."
        private const val OAUTH_STATE_EXPIRED_DETAIL = "OAuth state has expired."
        private const val OAUTH_STATE_INVALID_DETAIL = "OAuth state is invalid."
        private const val MEDIA_SERVICE_UNAVAILABLE_DETAIL = "Media service is unavailable."
        private const val ASSET_NOT_READY_DETAIL = "One or more assets are not ready for publishing."
    }

    @ExceptionHandler(ProviderNotConfiguredException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ProviderNotConfiguredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        PROVIDER_NOT_CONFIGURED_DETAIL,
    ).apply {
        title = "Provider not configured"
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

    @ExceptionHandler(ExpiredOAuthStateException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ExpiredOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, OAUTH_STATE_EXPIRED_DETAIL).apply {
            title = "OAuth state expired"
        }

    @ExceptionHandler(InvalidOAuthStateException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, OAUTH_STATE_INVALID_DETAIL).apply {
            title = "OAuth state invalid"
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
    fun handle(exception: AssetNotReadyException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        ASSET_NOT_READY_DETAIL,
    ).apply {
        title = "Asset not ready"
        setProperty("errorCode", "ASSET_NOT_READY")
    }
}
