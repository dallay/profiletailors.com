package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.InvalidEmailPasswordException
import com.profiletailors.smp.identity.application.InvalidRegistrationInputException
import com.profiletailors.smp.identity.application.InvalidVerificationTokenException
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import com.profiletailors.smp.identity.application.UserAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
        }

    @ExceptionHandler(InvalidRegistrationInputException::class)
    fun handle(exception: InvalidRegistrationInputException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid registration input"
        }

    @ExceptionHandler(UnverifiedEmailException::class)
    fun handle(exception: UnverifiedEmailException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.message ?: "Forbidden").apply {
            title = "Email verification required"
        }

    @ExceptionHandler(InvalidVerificationTokenException::class)
    fun handle(exception: InvalidVerificationTokenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Bad request").apply {
            title = "Invalid verification token"
        }
}
