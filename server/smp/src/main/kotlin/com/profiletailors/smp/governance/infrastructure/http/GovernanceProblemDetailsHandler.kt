package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.ConsentRecordNotFoundException
import com.profiletailors.smp.governance.domain.InvalidAuditEventCursorException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GovernanceProblemDetailsHandler {

    /**
         * Creates an HTTP 404 problem detail for a missing consent record.
         *
         * @param exception The exception describing the missing consent record.
         * @return A problem detail with the exception message or a default not-found message.
         */
        @ExceptionHandler(ConsentRecordNotFoundException::class)
    fun handleNotFound(exception: ConsentRecordNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Consent record not found").apply {
            title = "Consent record not found"
        }

    /**
         * Creates a bad-request problem detail for an enum validation failure.
         *
         * @param exception The enum validation exception being handled.
         * @return A problem detail with HTTP status 400 and the validation error message.
         */
        @ExceptionHandler(EnumValidationException::class)
    fun handleValidation(exception: EnumValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Bad Request"
        }

    /**
         * Handles invalid audit event cursor errors.
         *
         * @param exception The invalid audit event cursor exception.
         * @return A bad-request problem detail describing the invalid cursor.
         */
        @ExceptionHandler(InvalidAuditEventCursorException::class)
    fun handle(exception: InvalidAuditEventCursorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid audit cursor"
        }
}
