package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.ChangePasswordValidationException
import com.profiletailors.smp.identity.application.CloseAccountConfirmationException
import com.profiletailors.smp.identity.application.CloseAccountRateLimitException
import com.profiletailors.smp.identity.application.ExpiredPasswordResetTokenException
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.InvalidCurrentPasswordException
import com.profiletailors.smp.identity.application.InvalidEmailPasswordException
import com.profiletailors.smp.identity.application.InvalidPasswordResetTokenException
import com.profiletailors.smp.identity.application.InvalidRegistrationInputException
import com.profiletailors.smp.identity.application.InvalidVerificationTokenException
import com.profiletailors.smp.identity.application.LocalPasswordCredentialNotFoundException
import com.profiletailors.smp.identity.application.PasswordRecoveryDisabledException
import com.profiletailors.smp.identity.application.PasswordRecoveryPasswordException
import com.profiletailors.smp.identity.application.PasswordResetRateLimitExceededException
import com.profiletailors.smp.identity.application.RegistrationDisabledException
import com.profiletailors.smp.identity.application.RegistrationInvitationRequiredException
import com.profiletailors.smp.identity.application.RegistrationValidationException
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import com.profiletailors.smp.identity.application.UsedPasswordResetTokenException
import com.profiletailors.smp.identity.application.UserAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException
import java.net.URI

@RestControllerAdvice
@Suppress("TooManyFunctions")
class IdentityProblemDetailsHandler {

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
        HttpStatus.SERVICE_UNAVAILABLE,
        exception.message ?: "Registration is not available.",
    ).apply {
        title = "Registration disabled"
        type = URI("/problems/registration-disabled")
        setProperty("code", "REGISTRATION_DISABLED")
    }

    @ExceptionHandler(RegistrationInvitationRequiredException::class)
    fun handle(exception: RegistrationInvitationRequiredException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        exception.message ?: "A valid invitation is required to register.",
    ).apply {
        title = "Invitation required"
        type = URI("/problems/registration-invitation-required")
        setProperty("code", "REGISTRATION_INVITATION_REQUIRED")
    }

    /**
     * Creates a problem detail response for registration validation failures.
     *
     * @return A problem detail with HTTP 422 status and a registration validation failure message.
     */
    @ExceptionHandler(RegistrationValidationException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: RegistrationValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, REGISTRATION_VALIDATION_DETAIL).apply {
            title = "Registration validation failed"
        }

    @ExceptionHandler(InvalidVerificationTokenException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: InvalidVerificationTokenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_VERIFICATION_TOKEN_DETAIL).apply {
            title = "Invalid verification token"
        }

    @ExceptionHandler(InvalidPasswordResetTokenException::class)
    fun handle(exception: InvalidPasswordResetTokenException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.message ?: INVALID_RESET_TOKEN_DETAIL,
    ).apply {
        title = "Invalid password reset token"
        setProperty("code", "INVALID_PASSWORD_RESET_TOKEN")
    }

    @ExceptionHandler(ExpiredPasswordResetTokenException::class)
    fun handle(exception: ExpiredPasswordResetTokenException): ProblemDetail =
        invalidResetProblem(exception, "EXPIRED_PASSWORD_RESET_TOKEN")

    @ExceptionHandler(UsedPasswordResetTokenException::class)
    fun handle(exception: UsedPasswordResetTokenException): ProblemDetail =
        invalidResetProblem(exception, "USED_PASSWORD_RESET_TOKEN")

    @ExceptionHandler(PasswordRecoveryPasswordException::class)
    fun handle(exception: PasswordRecoveryPasswordException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: INVALID_PASSWORD_DETAIL).apply {
            title = "Invalid password"
            setProperty("code", "INVALID_PASSWORD")
        }

    @ExceptionHandler(PasswordResetRateLimitExceededException::class)
    fun handle(exception: PasswordResetRateLimitExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.message ?: RATE_LIMIT_DETAIL).apply {
            title = "Authentication rate limit exceeded"
            setProperty("code", "AUTH_RATE_LIMIT_EXCEEDED")
        }

    @ExceptionHandler(PasswordRecoveryDisabledException::class)
    fun handle(exception: PasswordRecoveryDisabledException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        exception.message ?: PASSWORD_RECOVERY_DISABLED_DETAIL,
    ).apply {
        title = "Password recovery disabled"
        setProperty("code", "PASSWORD_RECOVERY_DISABLED")
    }

    @ExceptionHandler(WebExchangeBindException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: WebExchangeBindException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL).apply {
            title = "Validation failed"
            setProperty("code", "VALIDATION_ERROR")
        }

    @ExceptionHandler(ServerWebInputException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: ServerWebInputException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL).apply {
            title = "Invalid request"
            setProperty("code", "VALIDATION_ERROR")
        }

    @ExceptionHandler(CloseAccountConfirmationException::class)
    @Suppress("UNUSED_PARAMETER")
    fun handle(exception: CloseAccountConfirmationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, CLOSE_ACCOUNT_CONFIRMATION_DETAIL).apply {
            title = "Invalid account closure confirmation"
        }

    /**
     * Creates a problem response when account-closure requests exceed the rate limit.
     *
     * @return A rate-limit problem detail with HTTP status 429 and the account-closure error code.
     */
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

    /**
     * Creates a bad-request response for an incorrect current password.
     *
     * @return A problem detail identifying the invalid current password error.
     */
    @ExceptionHandler(InvalidCurrentPasswordException::class)
    fun handle(exception: InvalidCurrentPasswordException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.message ?: "Incorrect current password.",
    ).apply {
        title = "Invalid current password"
        setProperty("code", "INVALID_CURRENT_PASSWORD")
    }

    /**
     * Creates a bad-request problem detail for an invalid new password.
     *
     * @param exception The exception describing why the new password is invalid.
     * @return A problem detail with the validation message and `INVALID_NEW_PASSWORD` code.
     */
    @ExceptionHandler(ChangePasswordValidationException::class)
    fun handle(exception: ChangePasswordValidationException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.message ?: "Password does not meet requirements.",
    ).apply {
        title = "Invalid new password"
        setProperty("code", "INVALID_NEW_PASSWORD")
    }

    /**
     * Handles requests for accounts without a local password credential.
     *
     * @return A bad-request problem detail with the `NO_LOCAL_PASSWORD_CREDENTIAL` code.
     */
    @ExceptionHandler(LocalPasswordCredentialNotFoundException::class)
    fun handle(exception: LocalPasswordCredentialNotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        exception.message ?: "Local password credential not found.",
    ).apply {
        title = "No local password credential"
        setProperty("code", "NO_LOCAL_PASSWORD_CREDENTIAL")
    }

    companion object {
        private const val INVALID_CREDENTIALS_DETAIL = "Invalid email or password."
        private const val USER_ALREADY_EXISTS_DETAIL = "Unable to complete registration with the provided credentials."
        private const val INVALID_REGISTRATION_INPUT_DETAIL = "Registration request is invalid."
        private const val REGISTRATION_VALIDATION_DETAIL = "Registration validation failed."
        private const val INVALID_VERIFICATION_TOKEN_DETAIL = "Invalid verification token."
        private const val CLOSE_ACCOUNT_CONFIRMATION_DETAIL = "Account closure confirmation is invalid."
        private const val CLOSE_ACCOUNT_RATE_LIMIT_DETAIL = "Account closure rate limit exceeded."
        private const val INVALID_RESET_TOKEN_DETAIL =
            "This password reset link is invalid or has expired. Request a new one."
        private const val INVALID_PASSWORD_DETAIL = "Password does not meet policy requirements."
        private const val RATE_LIMIT_DETAIL = "Authentication rate limit exceeded. Try again later."
        private const val PASSWORD_RECOVERY_DISABLED_DETAIL = "Password recovery is not available."
        private const val VALIDATION_DETAIL = "Validation failure"

        private fun invalidResetProblem(exception: RuntimeException, code: String): ProblemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.message ?: INVALID_RESET_TOKEN_DETAIL,
            ).apply {
                title = "Invalid password reset token"
                setProperty("code", code)
            }
    }
}
