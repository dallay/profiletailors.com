package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.UnsplashPhotoNotFoundException
import com.profiletailors.smp.media.application.UnsplashPhotoTooLargeException
import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = LoggerFactory.getLogger(UnsplashProblemDetailsHandler::class.java)

@RestControllerAdvice
class UnsplashProblemDetailsHandler {

    /**
     * Creates a problem detail response for an unconfigured Unsplash provider.
     *
     * @param exception The exception describing the provider configuration issue.
     * @return A service-unavailable problem detail with the configuration error code.
     */
    @ExceptionHandler(UnsplashProviderNotConfiguredException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: UnsplashProviderNotConfiguredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        UNSPLASH_NOT_CONFIGURED_DETAIL,
    ).apply {
        title = "Unsplash is not configured"
        setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_NOT_CONFIGURED")
    }

    /**
     * Creates a not-found problem response for an unavailable Unsplash photo.
     *
     * @param exception The exception containing the photo's external identifier and optional detail message.
     * @return A problem detail with HTTP status 404 and the photo's external identifier.
     */
    @ExceptionHandler(UnsplashPhotoNotFoundException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: UnsplashPhotoNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        UNSPLASH_PHOTO_NOT_FOUND_DETAIL,
    ).apply {
        title = "Unsplash photo not found"
        setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_PHOTO_NOT_FOUND")
    }

    /**
     * Creates a problem detail response for an Unsplash photo that exceeds the allowed size.
     *
     * @param exception The exception containing the photo's actual and maximum allowed sizes.
     * @return A problem detail with HTTP status 413 and the photo size information.
     */
    @ExceptionHandler(UnsplashPhotoTooLargeException::class)
    fun handle(exception: UnsplashPhotoTooLargeException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.PAYLOAD_TOO_LARGE,
        exception.message ?: "Unsplash photo too large.",
    ).apply {
        title = "Unsplash photo too large"
        setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_PHOTO_TOO_LARGE")
        setProperty("actualSize", exception.actualSize)
        setProperty("maxAllowed", exception.maxAllowed)
    }

    /**
     * Converts an Unsplash provider failure into a bad gateway problem detail.
     *
     * @param exception The provider exception containing the failure details.
     * @return A problem detail with the provider error status and error code.
     */
    @ExceptionHandler(UnsplashProviderException::class)
    fun handle(exception: UnsplashProviderException): ProblemDetail {
        logger.warn("Unsplash provider request failed: {}", exception.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            UNSPLASH_PROVIDER_ERROR_DETAIL,
        ).apply {
            title = "Unsplash provider error"
            setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_PROVIDER_ERROR")
        }
    }

    companion object {
        const val ERROR_CODE_PROPERTY = "errorCode"
        private const val UNSPLASH_NOT_CONFIGURED_DETAIL = "Unsplash is not configured for this environment."
        private const val UNSPLASH_PHOTO_NOT_FOUND_DETAIL = "Unsplash photo not found."
        private const val UNSPLASH_PROVIDER_ERROR_DETAIL = "Unsplash is temporarily unavailable."
    }
}
