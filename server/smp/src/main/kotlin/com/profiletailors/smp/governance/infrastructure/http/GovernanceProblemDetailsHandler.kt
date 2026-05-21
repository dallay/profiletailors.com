package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.InvalidAuditEventCursorException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GovernanceProblemDetailsHandler {

    @ExceptionHandler(InvalidAuditEventCursorException::class)
    fun handle(exception: InvalidAuditEventCursorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid audit cursor"
        }
}
