package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.identity.domain.EmailStatus

data class RegisterUserCommand(
    val email: String,
    val password: String,
    val username: String? = null,
    val confirmedAgeEligibility: Boolean,
    val acceptedTermsVersion: String,
) : CommandWithResult<LocalAuthSessionResult>

data class LoginUserCommand(val email: String, val password: String) : CommandWithResult<LocalAuthSessionResult>

data class RefreshUserSessionCommand(val rawRefreshToken: String) : CommandWithResult<LocalAuthSessionResult>

data class LogoutUserSessionCommand(val rawRefreshToken: String?) : CommandWithResult<LogoutUserSessionResult>

data class VerifyEmailCommand(val token: String) : CommandWithResult<LocalAuthSessionResult>

data class ResendVerificationCommand(val email: String) : CommandWithResult<ResendVerificationResult>

data class AuthTokens(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val principalId: String,
    val email: String,
    val username: String?,
    val emailStatus: String = EmailStatus.PENDING.name,
    val workspaceId: String? = null,
)

data class LocalAuthSessionResult(val tokens: AuthTokens, val refreshToken: RefreshSessionToken)

data class LogoutUserSessionResult(val clearedClientSession: Boolean = true)

data class RegistrationResult(
    val principalId: String,
    val email: String,
    val username: String?,
    val emailStatus: EmailStatus,
)

data class ResendVerificationResult(val accepted: Boolean = true)
