package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import java.time.Clock
import java.util.UUID

internal suspend fun issueAuthSession(
    principalId: String,
    subject: String,
    email: String,
    username: String?,
    clock: Clock,
    localJwtIssuer: LocalJwtIssuer,
    refreshSessionLifecycleService: RefreshSessionLifecycleService,
): LocalAuthSessionResult {
    val token = localJwtIssuer.issue(
        principalId = principalId,
        subject = subject,
        email = email,
        username = username,
        issuedAt = clock.instant(),
    )
    val refreshSession = refreshSessionLifecycleService.issue(principalId)
    return LocalAuthSessionResult(
        tokens = AuthTokens(
            accessToken = token.value,
            expiresIn = token.expiresInSeconds,
            principalId = principalId,
            email = email,
            username = username,
        ),
        refreshToken = refreshSession.refreshToken,
    )
}

@Service
internal class RegisterUserHandler(
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordHasher: PasswordHasher,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
) : CommandWithResultHandler<RegisterUserCommand, LocalAuthSessionResult> {

    override suspend fun handle(command: RegisterUserCommand): LocalAuthSessionResult {
        val normalizedEmail = command.email.trim().lowercase()
        val normalizedUsername =
            command.username?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedEmail.substringBefore('@')

        validateRegistration(normalizedEmail, command.password, normalizedUsername)

        if (
            localPasswordCredentialGateway.findByEmail(normalizedEmail) != null ||
            principalIdentityLookup.findByEmail(normalizedEmail) != null
        ) {
            throw UserAlreadyExistsException(normalizedEmail)
        }

        val principalId = "user-${UUID.randomUUID()}"
        val subject = "local:$normalizedEmail"

        identityRegistrationGateway.createUserIdentity(
            principalId = principalId,
            subject = subject,
            email = normalizedEmail,
            username = normalizedUsername,
            provider = null,
            displayIdentity = normalizedUsername,
        )
        localPasswordCredentialGateway.create(
            principalId = principalId,
            passwordHash = passwordHasher.hash(command.password),
        )

        return issueAuthSession(
            principalId = principalId,
            subject = subject,
            email = normalizedEmail,
            username = normalizedUsername,
            clock = clock,
            localJwtIssuer = localJwtIssuer,
            refreshSessionLifecycleService = refreshSessionLifecycleService,
        )
    }

    private fun validateRegistration(email: String, password: String, username: String) {
        val error = when {
            email.isBlank() || !EMAIL_REGEX.matches(email) -> "A valid email is required."
            password.length < MIN_PASSWORD_LENGTH -> "Password must contain at least $MIN_PASSWORD_LENGTH characters."
            username.isBlank() -> "Username is required."
            else -> null
        }
        if (error != null) throw InvalidRegistrationInputException(error)
    }

    private companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        private const val MIN_PASSWORD_LENGTH = 8
    }
}

@Service
internal class LoginUserHandler(
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordHasher: PasswordHasher,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
) : CommandWithResultHandler<LoginUserCommand, LocalAuthSessionResult> {

    override suspend fun handle(command: LoginUserCommand): LocalAuthSessionResult {
        val normalizedEmail = command.email.trim().lowercase()
        val credential = localPasswordCredentialGateway.findByEmail(normalizedEmail)
            ?: throw InvalidEmailPasswordException()

        if (!passwordHasher.matches(command.password, credential.passwordHash)) {
            throw InvalidEmailPasswordException()
        }

        return issueAuthSession(
            principalId = credential.principalId,
            subject = "local:${credential.email}",
            email = credential.email,
            username = credential.username,
            clock = clock,
            localJwtIssuer = localJwtIssuer,
            refreshSessionLifecycleService = refreshSessionLifecycleService,
        )
    }
}

@Service
internal class RefreshUserSessionHandler(
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
) : CommandWithResultHandler<RefreshUserSessionCommand, LocalAuthSessionResult> {

    override suspend fun handle(command: RefreshUserSessionCommand): LocalAuthSessionResult {
        val rotatedSession = refreshSessionLifecycleService.rotate(command.rawRefreshToken)
        val identityFacts = principalIdentityLookup.findByPrincipalId(rotatedSession.current.principalId)

        val email = identityFacts?.email
            ?: error("Email could not be resolved for principal '${rotatedSession.current.principalId}'.")
        val username = identityFacts.username
        val token = localJwtIssuer.issue(
            principalId = rotatedSession.current.principalId,
            subject = "local:$email",
            email = email,
            username = username,
            issuedAt = clock.instant(),
        )

        return LocalAuthSessionResult(
            tokens = AuthTokens(
                accessToken = token.value,
                expiresIn = token.expiresInSeconds,
                principalId = rotatedSession.current.principalId,
                email = email,
                username = username,
            ),
            refreshToken = rotatedSession.current.refreshToken,
        )
    }
}

@Service
internal class LogoutUserSessionHandler(
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
) : CommandWithResultHandler<LogoutUserSessionCommand, LogoutUserSessionResult> {

    override suspend fun handle(command: LogoutUserSessionCommand): LogoutUserSessionResult {
        val rawRefreshToken = command.rawRefreshToken ?: return LogoutUserSessionResult()
        try {
            refreshSessionLifecycleService.revoke(rawRefreshToken)
        } catch (_: RefreshSessionNotActiveException) {
            // Idempotent logout: local cleanup still proceeds.
        }
        return LogoutUserSessionResult()
    }
}
