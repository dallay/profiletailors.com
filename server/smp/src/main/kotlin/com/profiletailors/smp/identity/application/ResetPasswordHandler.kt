package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
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
) : CommandWithResultHandler<ResetPasswordCommand, ResetPasswordResult> {

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
        val consumed = transactionRunner.runAtomically {
            val ok = try {
                passwordResetTokenRepository.consumeAndUpdatePassword(
                    tokenHash = tokenHash,
                    now = now,
                    newPasswordHash = newPasswordHash,
                )
            } catch (_: com.profiletailors.smp.identity.infrastructure.PasswordResetCredentialMissingException) {
                throw InvalidPasswordResetTokenException()
            }
            if (!ok) {
                throw InvalidPasswordResetTokenException()
            }
            refreshSessionLifecycleService.revokeAllForPrincipal(principalId)
            true
        }

        return if (consumed) ResetPasswordResult() else ResetPasswordResult(passwordChanged = false)
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
