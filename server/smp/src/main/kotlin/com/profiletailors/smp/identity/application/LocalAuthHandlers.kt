package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserRegistered
import java.time.Clock
import java.util.UUID

internal suspend fun issueAuthSession(
    principalId: String,
    subject: String,
    email: String,
    username: String?,
    emailStatus: EmailStatus,
    workspaceId: String? = null,
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
            emailStatus = emailStatus.name,
            workspaceId = workspaceId,
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
    private val workspaceProvisioningService: com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
) : CommandWithResultHandler<RegisterUserCommand, RegistrationResult> {

    override suspend fun handle(command: RegisterUserCommand): RegistrationResult {
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
            emailStatus = EmailStatus.UNVERIFIED,
        )
        localPasswordCredentialGateway.create(
            principalId = principalId,
            passwordHash = passwordHasher.hash(command.password),
        )

        // Provision a default workspace for the new user
        val provisioned = workspaceProvisioningService.provisionDefaultWorkspace(
            principalId = principalId,
            displayName = normalizedUsername,
        )

        // Generate verification token and store hashed
        val generated = EmailVerificationTokenHasher.generate(clock.instant())
        identityRegistrationGateway.createEmailVerificationToken(
            email = normalizedEmail,
            tokenHash = generated.tokenHash,
            expiresAt = generated.expiresAt,
        )

        // Publish domain event for async email dispatch
        eventPublisher.publish(
            UserRegistered(
                principalId = principalId,
                email = normalizedEmail,
                username = normalizedUsername,
                rawVerificationToken = generated.rawToken,
            ),
        )

        return RegistrationResult(
            principalId = principalId,
            email = normalizedEmail,
            username = normalizedUsername,
            emailStatus = EmailStatus.UNVERIFIED,
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
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
) : CommandWithResultHandler<LoginUserCommand, LocalAuthSessionResult> {

    override suspend fun handle(command: LoginUserCommand): LocalAuthSessionResult {
        val normalizedEmail = command.email.trim().lowercase()
        val credential = localPasswordCredentialGateway.findByEmail(normalizedEmail)

        if (credential == null || !passwordHasher.matches(command.password, credential.passwordHash)) {
            throw InvalidEmailPasswordException()
        }

        val identityFacts = principalIdentityLookup.findByEmail(normalizedEmail)
        val emailStatus = identityFacts?.emailStatus ?: EmailStatus.VERIFIED

        if (emailStatus != EmailStatus.VERIFIED) {
            throw UnverifiedEmailException(normalizedEmail)
        }

        return issueAuthSession(
            principalId = credential.principalId,
            subject = "local:${credential.email}",
            email = credential.email,
            username = credential.username,
            emailStatus = emailStatus,
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
        val emailStatus = identityFacts.emailStatus ?: EmailStatus.VERIFIED

        if (emailStatus != EmailStatus.VERIFIED) {
            throw UnverifiedEmailException(email)
        }

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
                emailStatus = emailStatus.name,
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

@Service
internal class VerifyEmailHandler(
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localJwtIssuer: LocalJwtIssuer,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val clock: Clock,
) : CommandWithResultHandler<VerifyEmailCommand, LocalAuthSessionResult> {

    override suspend fun handle(command: VerifyEmailCommand): LocalAuthSessionResult {
        val now = clock.instant()
        val tokenHash = EmailVerificationTokenHasher.hash(command.token)
        val storedToken = identityRegistrationGateway.verifyEmailToken(tokenHash)
            ?: throw InvalidVerificationTokenException()

        if (!storedToken.isValid(now)) {
            throw InvalidVerificationTokenException()
        }

        identityRegistrationGateway.markTokenUsed(tokenHash, now)
        identityRegistrationGateway.updateEmailStatus(storedToken.email, EmailStatus.VERIFIED)

        val identityFacts = principalIdentityLookup.findByEmail(storedToken.email)
            ?: error("Identity not found for email '${storedToken.email}' after verification.")

        return issueAuthSession(
            principalId = identityFacts.principalId,
            subject = identityFacts.subject,
            email = identityFacts.email ?: storedToken.email,
            username = identityFacts.username,
            emailStatus = EmailStatus.VERIFIED,
            workspaceId = null,
            clock = clock,
            localJwtIssuer = localJwtIssuer,
            refreshSessionLifecycleService = refreshSessionLifecycleService,
        )
    }

    private fun EmailVerificationTokenData.isValid(now: java.time.Instant): Boolean =
        usedAt == null && now.isBefore(expiresAt)
}

@Service
internal class ResendVerificationHandler(
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) : CommandWithResultHandler<ResendVerificationCommand, ResendVerificationResult> {

    override suspend fun handle(command: ResendVerificationCommand): ResendVerificationResult {
        val normalizedEmail = command.email.trim().lowercase()
        val identityFacts = principalIdentityLookup.findByEmail(normalizedEmail)

        // Always return accepted to prevent email enumeration
        if (identityFacts == null || identityFacts.emailStatus != EmailStatus.UNVERIFIED) {
            return ResendVerificationResult()
        }

        // Invalidate old tokens
        identityRegistrationGateway.invalidateEmailTokens(normalizedEmail)

        // Generate new token
        val generated = EmailVerificationTokenHasher.generate()
        identityRegistrationGateway.createEmailVerificationToken(
            email = normalizedEmail,
            tokenHash = generated.tokenHash,
            expiresAt = generated.expiresAt,
        )

        // Publish event for email dispatch (same handler as registration)
        eventPublisher.publish(
            UserRegistered(
                principalId = identityFacts.principalId,
                email = normalizedEmail,
                username = identityFacts.username,
                rawVerificationToken = generated.rawToken,
            ),
        )

        return ResendVerificationResult()
    }
}
