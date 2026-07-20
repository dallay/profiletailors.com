package com.profiletailors.smp.identity.infrastructure.http

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

    @ExceptionHandler(InvalidEmailPasswordException::class)
    fun handle(exception: InvalidEmailPasswordException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized").apply {
            title = "Invalid credentials"
        }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handle(exception: UserAlreadyExistsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message ?: "Conflict").apply {
            title = "User already exists"
            setProperty("code", "USER_ALREADY_EXISTS")
        }

    @ExceptionHandler(InvalidRegistrationInputException::class)
    fun handle(exception: InvalidRegistrationInputException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
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
    fun handle(exception: RegistrationValidationException): ProblemDetail {
        val detail = exception.message ?: "Unprocessable entity"
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail).apply {
            title = "Registration validation failed"
        }
    }

    @ExceptionHandler(InvalidVerificationTokenException::class)
    fun handle(exception: InvalidVerificationTokenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid verification token"
        }
}
