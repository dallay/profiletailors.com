package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.platform.application.MissingPrincipalContextException
import com.profiletailors.smp.platform.application.MissingResourceContextException
import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PlatformProblemDetailsHandler {

    @ExceptionHandler(MissingPrincipalContextException::class)
    fun handle(exception: MissingPrincipalContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized").apply {
            title = "Principal context missing"
        }

    @ExceptionHandler(ApiKeyCredentialNotActiveException::class)
    fun handle(exception: ApiKeyCredentialNotActiveException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized").apply {
            title = "API key credential invalid"
        }

    @ExceptionHandler(MissingResourceContextException::class)
    fun handle(exception: MissingResourceContextException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Resource context missing"
        }

    @ExceptionHandler(MissingActiveWorkspaceException::class)
    fun handle(exception: MissingActiveWorkspaceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Active workspace missing"
        }
}
