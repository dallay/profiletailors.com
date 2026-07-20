package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.ConsentRecordNotFoundException
import com.profiletailors.smp.governance.domain.InvalidAuditEventCursorException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GovernanceProblemDetailsHandler {

    @ExceptionHandler(ConsentRecordNotFoundException::class)
    fun handleNotFound(exception: ConsentRecordNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Consent record not found").apply {
            title = "Consent record not found"
        }

    @ExceptionHandler(EnumValidationException::class)
    fun handleValidation(exception: EnumValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Bad Request"
        }

    @ExceptionHandler(InvalidAuditEventCursorException::class)
    fun handle(exception: InvalidAuditEventCursorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid audit cursor"
        }
}
