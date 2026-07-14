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
    @ExceptionHandler(UnsplashProviderNotConfiguredException::class)
    fun handle(exception: UnsplashProviderNotConfiguredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        exception.message ?: "Unsplash is not configured for this environment.",
    ).apply {
        title = "Unsplash is not configured"
        setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_NOT_CONFIGURED")
    }

    @ExceptionHandler(UnsplashPhotoNotFoundException::class)
    fun handle(exception: UnsplashPhotoNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        exception.message ?: "Unsplash photo not found.",
    ).apply {
        title = "Unsplash photo not found"
        setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_PHOTO_NOT_FOUND")
        setProperty("externalId", exception.externalId)
    }

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

    @ExceptionHandler(UnsplashProviderException::class)
    fun handle(exception: UnsplashProviderException): ProblemDetail {
        logger.warn("Unsplash provider request failed: {}", exception.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            exception.message ?: "Unsplash is temporarily unavailable.",
        ).apply {
            title = "Unsplash provider error"
            setProperty(ERROR_CODE_PROPERTY, "UNSPLASH_PROVIDER_ERROR")
        }
    }

    private companion object {
        const val ERROR_CODE_PROPERTY = "errorCode"
    }
}
