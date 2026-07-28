package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CloseAccountConfirmationException
import com.profiletailors.smp.identity.application.CloseAccountRateLimitException
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.InvalidEmailPasswordException
import com.profiletailors.smp.identity.application.InvalidRegistrationInputException
import com.profiletailors.smp.identity.application.InvalidVerificationTokenException
import com.profiletailors.smp.identity.application.RegistrationDisabledException
import com.profiletailors.smp.identity.application.RegistrationValidationException
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import com.profiletailors.smp.identity.application.UserAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class IdentityProblemDetailsHandler {

    /**
         * Creates a problem detail for invalid email or password credentials.
         *
         * @param exception The invalid credentials exception being handled.
         * @return An unauthorized problem detail describing the invalid credentials.
         */
        @ExceptionHandler(InvalidEmailPasswordException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidEmailPasswordException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_DETAIL).apply {
            title = "Invalid credentials"
        }

    @ExceptionHandler(UserAlreadyExistsException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: UserAlreadyExistsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, USER_ALREADY_EXISTS_DETAIL).apply {
            title = "User already exists"
            setProperty("code", "USER_ALREADY_EXISTS")
        }

    @ExceptionHandler(InvalidRegistrationInputException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidRegistrationInputException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REGISTRATION_INPUT_DETAIL).apply {
            title = "Invalid registration input"
        }

    @ExceptionHandler(UnverifiedEmailException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: UnverifiedEmailException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        "Please verify your email before using this feature.",
    ).apply {
        title = "Email verification required"
        type = URI("https://api.profiletailors.com/errors/email-verification-required")
        setProperty("code", "EMAIL_VERIFICATION_REQUIRED")
    }

    @ExceptionHandler(FeatureEmailVerificationRequired::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: FeatureEmailVerificationRequired): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        "Please verify your email before using this feature.",
    ).apply {
        title = "Email verification required"
        type = URI("https://api.profiletailors.com/errors/email-verification-required")
        setProperty("code", "EMAIL_VERIFICATION_REQUIRED")
    }

    @ExceptionHandler(RegistrationDisabledException::class)
    fun handle(exception: RegistrationDisabledException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        exception.message ?: "Registration is not available.",
    ).apply {
        title = "Registration disabled"
        type = URI("/problems/registration-disabled")
        setProperty("code", "registration_disabled")
    }

    @ExceptionHandler(RegistrationValidationException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: RegistrationValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, REGISTRATION_VALIDATION_DETAIL).apply {
            title = "Registration validation failed"
        }

    /**
         * Creates a problem detail response for an invalid verification token.
         *
         * @param exception The invalid verification token exception being handled.
         * @return A bad-request problem detail describing the invalid verification token.
         */
        @ExceptionHandler(InvalidVerificationTokenException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidVerificationTokenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_VERIFICATION_TOKEN_DETAIL).apply {
            title = "Invalid verification token"
        }

    /**
         * Maps an invalid account closure confirmation exception to a bad-request problem detail.
         *
         * @param exception The exception indicating that account closure confirmation is invalid.
         * @return A problem detail describing the invalid account closure confirmation.
         */
        @ExceptionHandler(CloseAccountConfirmationException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: CloseAccountConfirmationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, CLOSE_ACCOUNT_CONFIRMATION_DETAIL).apply {
            title = "Invalid account closure confirmation"
        }

    @ExceptionHandler(CloseAccountRateLimitException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: CloseAccountRateLimitException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.TOO_MANY_REQUESTS,
        CLOSE_ACCOUNT_RATE_LIMIT_DETAIL,
    ).apply {
        title = "Account closure rate limit exceeded"
        type = URI("https://api.profiletailors.com/errors/account-closure-rate-limit")
        setProperty("code", "ACCOUNT_CLOSURE_RATE_LIMIT")
    }

    companion object {
        private const val INVALID_CREDENTIALS_DETAIL = "Invalid email or password."
        private const val USER_ALREADY_EXISTS_DETAIL = "Unable to complete registration with the provided credentials."
        private const val INVALID_REGISTRATION_INPUT_DETAIL = "Registration request is invalid."
        private const val REGISTRATION_VALIDATION_DETAIL = "Registration validation failed."
        private const val INVALID_VERIFICATION_TOKEN_DETAIL = "Invalid verification token."
        private const val CLOSE_ACCOUNT_CONFIRMATION_DETAIL = "Account closure confirmation is invalid."
        private const val CLOSE_ACCOUNT_RATE_LIMIT_DETAIL = "Account closure rate limit exceeded."
    }
}
