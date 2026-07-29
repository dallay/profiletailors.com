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

    /**
     * Resets a user's password using a valid password-reset token.
     *
     * @param command The password-reset command containing the token and new password.
     * @return The result of the completed password reset.
     * @throws PasswordRecoveryDisabledException If password recovery is disabled.
     * @throws PasswordRecoveryPasswordException If the new password length is invalid.
     * @throws InvalidPasswordResetTokenException If the token is missing or cannot be consumed.
     * @throws UsedPasswordResetTokenException If the token has already been used.
     * @throws ExpiredPasswordResetTokenException If the token has expired.
     */
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
        } catch (failure: org.springframework.dao.DataAccessException) {
            log.error(
                "Audit recording failed for completed password reset of principal '{}'; reset outcome is unaffected",
                principalId,
                failure,
            )
        }
        return ResetPasswordResult()
    }

    /**
     * Validates that the password length is within the permitted range.
     *
     * @param password The password to validate.
     * @throws PasswordRecoveryPasswordException If the password length is outside the permitted range.
     */
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
