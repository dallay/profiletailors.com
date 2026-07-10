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
    @ExceptionHandler(ProviderNotConfiguredException::class)
    fun handle(exception: ProviderNotConfiguredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        exception.message ?: "Provider not configured",
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
    fun handle(exception: PublicationStateTransitionException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        exception.message ?: "Publication state transition conflict",
    ).apply {
        title = "Publication state conflict"
    }

    @ExceptionHandler(PublicationNotFoundException::class)
    fun handle(exception: PublicationNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        exception.message ?: "Publication not found",
    ).apply {
        title = "Publication not found"
    }

    @ExceptionHandler(ExpiredOAuthStateException::class)
    fun handle(exception: ExpiredOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "OAuth state expired").apply {
            title = "OAuth state expired"
        }

    @ExceptionHandler(InvalidOAuthStateException::class)
    fun handle(exception: InvalidOAuthStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "OAuth state invalid").apply {
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
    fun handle(exception: MediaServiceUnavailableException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        exception.message ?: "Media service is unavailable",
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
        exception.message ?: "Asset ${exception.assetId} is not ready for publishing",
    ).apply {
        title = "Asset not ready"
        setProperty("errorCode", "ASSET_NOT_READY")
        setProperty("assetId", exception.assetId)
    }
}
