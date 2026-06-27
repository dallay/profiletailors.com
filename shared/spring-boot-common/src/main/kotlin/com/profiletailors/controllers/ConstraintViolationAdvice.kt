package com.profiletailors.controllers

import jakarta.validation.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.net.URI
import java.time.Instant

/**
 * Handles constraint violation errors.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - DEFAULT_PRECEDENCE)
internal class ConstraintViolationAdvice {
    /**
     * Handles ConstraintViolationException, typically caused by request parameter validation errors.
     *
     * @return ProblemDetail containing HTTP 400 status, error message, and field-specific validation errors.
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {
        val pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        pd.title = "validation failed"
        pd.detail = "Request parameter validation failed. Please check the provided values."
        pd.type = URI.create("$ERROR_PAGE/validation/constraint-violation")
        pd.setProperty(ERROR_CATEGORY, "VALIDATION")
        pd.setProperty(MESSAGE_KEY, "error.validation.failed")
        val errors = ex.constraintViolations.map { violation ->
            mapOf(
                "field" to violation.propertyPath.toString(),
                "message" to (violation.message ?: "Invalid value"),
                "rejectedValue" to violation.invalidValue,
            )
        }
        pd.setProperty("errors", errors)
        pd.setProperty(TIMESTAMP, Instant.now().toString())
        return pd
    }
}
