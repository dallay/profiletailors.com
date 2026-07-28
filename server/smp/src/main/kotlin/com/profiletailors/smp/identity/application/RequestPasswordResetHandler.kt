package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import java.time.Clock
import java.time.Duration

private const val PASSWORD_RESET_EMAIL_WINDOW_MINUTES = 30L
private const val PASSWORD_RESET_EMAIL_MAX_REQUESTS = 3

@Service
internal class RequestPasswordResetHandler(
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val rateLimitPort: RateLimitPort,
    private val clock: Clock,
    private val passwordRecoveryEnabled: () -> Boolean,
    private val timingEqualizer: PasswordRecoveryTimingEqualizer =
        MinimumDurationPasswordRecoveryTimingEqualizer(Duration.ZERO),
    private val passwordResetEmailWindow: Duration = Duration.ofMinutes(PASSWORD_RESET_EMAIL_WINDOW_MINUTES),
    private val passwordResetEmailBucket: String = "password-reset-request-email",
) : CommandWithResultHandler<RequestPasswordResetCommand, RequestPasswordResetResult> {

    override suspend fun handle(command: RequestPasswordResetCommand): RequestPasswordResetResult {
        if (!passwordRecoveryEnabled()) {
            throw PasswordRecoveryDisabledException()
        }

        val requestStartedAt = timingEqualizer.markStart()
        val normalizedEmail = normalizeEmail(command.email)

        val now = clock.instant()
        val admitted = rateLimitPort.tryAcquire(
            key = "$passwordResetEmailBucket:$normalizedEmail",
            window = passwordResetEmailWindow,
            now = now,
            maxRequests = PASSWORD_RESET_EMAIL_MAX_REQUESTS,
        )
        if (!admitted) {
            throw PasswordResetRateLimitExceededException()
        }

        val principalIdentity = principalIdentityLookup.findByEmail(normalizedEmail)
        val credential = localPasswordCredentialGateway.findByEmail(normalizedEmail)

        if (principalIdentity == null || credential == null) {
            timingEqualizer.equalize(requestStartedAt)
            return RequestPasswordResetResult()
        }

        val generated = PasswordResetTokenHasher.generate(now)
        val principalId = principalIdentity.principalId

        transactionRunner.runAtomically {
            passwordResetTokenRepository.invalidateActiveTokens(principalId, now)
            passwordResetTokenRepository.create(
                principalId = principalId,
                tokenHash = generated.tokenHash,
                requestedAt = now,
                expiresAt = generated.expiresAt,
            )
        }

        eventPublisher.publish(
            PasswordResetRequested(
                principalId = principalId,
                email = normalizedEmail,
                rawResetToken = generated.rawToken,
                locale = command.locale,
            ),
        )

        timingEqualizer.equalize(requestStartedAt)
        return RequestPasswordResetResult()
    }
}
