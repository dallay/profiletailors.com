package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import org.slf4j.LoggerFactory
import java.time.Clock

@Service
@Suppress("ThrowsCount")
internal class ResetPasswordHandler(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val passwordRecoveryEnabled: () -> Boolean,
    private val passwordResetAuditPort: PasswordResetAuditPort,
) : CommandWithResultHandler<ResetPasswordCommand, ResetPasswordResult> {

    private val log = LoggerFactory.getLogger(ResetPasswordHandler::class.java)

    override suspend fun handle(command: ResetPasswordCommand): ResetPasswordResult {
        if (!passwordRecoveryEnabled()) {
            throw PasswordRecoveryDisabledException()
        }
        validatePassword(command.newPassword)

        val tokenHash = PasswordResetTokenHasher.hash(command.token)
        val now = clock.instant()
        val newPasswordHash = passwordHasher.hash(command.newPassword)

        val stored = passwordResetTokenRepository.findByTokenHash(tokenHash)
            ?: throw InvalidPasswordResetTokenException()

        when {
            stored.isUsed() -> throw UsedPasswordResetTokenException()
            stored.isExpired(now) -> throw ExpiredPasswordResetTokenException()
        }

        val principalId = stored.principalId
        transactionRunner.runAtomically {
            try {
                passwordResetTokenRepository.consumeAndUpdatePassword(
                    tokenHash = tokenHash,
                    now = now,
                    newPasswordHash = newPasswordHash,
                )
            } catch (_: PasswordResetCredentialMissingException) {
                throw InvalidPasswordResetTokenException()
            }
            refreshSessionLifecycleService.revokeAllForPrincipal(principalId)
            true
        }

        try {
            passwordResetAuditPort.recordCompleted(
                PasswordResetAuditEvent(
                    principalId = principalId,
                    occurredAt = now,
                ),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // The reset is already committed; audit is additive and cannot change its outcome.
            // We MUST NOT swallow without a trace — emit an error log so the audit gap is
            // observable to operators even when the persistence/transport layer is unavailable.
            log.error(
                "Audit recording failed for completed password reset of principal '{}'; reset outcome is unaffected",
                principalId,
                failure,
            )
        }
        return ResetPasswordResult()
    }

    private fun validatePassword(password: String) {
        if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw PasswordRecoveryPasswordException(password)
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
    }
}
