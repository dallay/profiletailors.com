package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PublishingProblemDetailsHandler {
    @ExceptionHandler(ProviderNotConfiguredException::class)
    fun handle(exception: ProviderNotConfiguredException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            exception.message ?: "Provider not configured",
        ).apply {
            title = "Provider not configured"
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
}
