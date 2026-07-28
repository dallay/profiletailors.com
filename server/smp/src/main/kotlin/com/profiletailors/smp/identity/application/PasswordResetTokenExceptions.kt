package com.profiletailors.smp.identity.application

open class InvalidPasswordResetTokenException(message: String = INVALID_RESET_TOKEN_DETAIL) :
    RuntimeException(message)

class ExpiredPasswordResetTokenException : InvalidPasswordResetTokenException()

class UsedPasswordResetTokenException : InvalidPasswordResetTokenException()

class PasswordRecoveryDisabledException : RuntimeException("Password recovery is disabled.")

class PasswordRecoveryPasswordException(@Suppress("UNUSED_PARAMETER") password: String) :
    RuntimeException("Password does not meet policy requirements.")

class PasswordResetRateLimitExceededException :
    RuntimeException(
        "Authentication rate limit exceeded. Try again later.",
    )

const val INVALID_RESET_TOKEN_DETAIL: String =
    "This password reset link is invalid or has expired. Request a new one."
