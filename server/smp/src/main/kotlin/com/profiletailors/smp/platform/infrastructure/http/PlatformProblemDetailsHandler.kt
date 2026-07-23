package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PlatformProblemDetailsHandler {

    companion object {
        private const val UNAUTHORIZED_DETAIL = "Authentication is required."
        private const val BAD_REQUEST_DETAIL = "The request is missing required context."
        private const val INVALID_REFRESH_SESSION_DETAIL = "Session is not active."
    }

    @ExceptionHandler(MissingPrincipalContextException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: MissingPrincipalContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL).apply {
            title = "Principal context missing"
        }

    @ExceptionHandler(ApiKeyCredentialNotActiveException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ApiKeyCredentialNotActiveException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL).apply {
            title = "API key credential invalid"
        }

    @ExceptionHandler(MissingResourceContextException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: MissingResourceContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, BAD_REQUEST_DETAIL).apply {
            title = "Resource context missing"
        }

    @ExceptionHandler(RefreshSessionNotActiveException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: RefreshSessionNotActiveException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, INVALID_REFRESH_SESSION_DETAIL).apply {
            title = "Refresh session invalid"
        }
}
