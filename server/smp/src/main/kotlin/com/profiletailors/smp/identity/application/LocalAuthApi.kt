package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.credentials.application.RefreshSessionToken

data class RegisterUserCommand(
    val email: String,
    val password: String,
    val username: String? = null,
) : CommandWithResult<LocalAuthSessionResult>

data class LoginUserCommand(
    val email: String,
    val password: String,
) : CommandWithResult<LocalAuthSessionResult>

data class RefreshUserSessionCommand(
    val rawRefreshToken: String,
) : CommandWithResult<LocalAuthSessionResult>

data class LogoutUserSessionCommand(
    val rawRefreshToken: String?,
) : CommandWithResult<LogoutUserSessionResult>

data class AuthTokens(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val principalId: String,
    val email: String,
    val username: String?,
    val workspaceId: String? = null,
)

data class LocalAuthSessionResult(
    val tokens: AuthTokens,
    val refreshToken: RefreshSessionToken,
)

data class LogoutUserSessionResult(
    val clearedClientSession: Boolean = true,
)
