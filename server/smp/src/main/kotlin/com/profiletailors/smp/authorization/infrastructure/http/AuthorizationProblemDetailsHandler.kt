package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.application.AuthorizationDeniedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthorizationProblemDetailsHandler {

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handle(exception: AuthorizationDeniedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.message ?: "Forbidden").apply {
            title = "Authorization denied"
        }
}
