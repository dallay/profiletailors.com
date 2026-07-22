package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Service that orchestrates the permanent closure of a user account.
 *
 * Account closure is irreversible. The following operations are performed:
 * 1. Validate the confirmation string equals "DELETE"
 * 2. Enforce rate limit (1 attempt per 5 minutes per principal)
 * 3. Revoke all sessions and API keys
 * 4. Cancel pending publications and delete social connections/credentials
 * 5. Mark media assets as deleted and blobs ready for garbage collection
 * 6. Remove all workspace memberships
 * 7. Anonymize the principal's identity (email, username, display identity)
 * 8. Log audit event
 *
 * Unlike DSAR deletion requests, account closure proceeds even if the user is
 * the sole owner of any workspace — at the cost of orphaning those workspaces.
 * This is intentional for self-service account closure on a personal instance.
 */
@Service
class CloseAccountHandler(
    private val orchestrationPort: CloseAccountOrchestrationPort,
    private val rateLimitPort: RateLimitPort,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Executes the full account closure flow.
     *
     * @throws IllegalArgumentException if [command.confirmation] is not "DELETE".
     * @throws CloseAccountRateLimitException if the principal has already attempted
     *         closure within the last [RATE_LIMIT_DURATION].
     */
    suspend fun handle(command: CloseAccountCommand) {
        validateConfirmation(command)
        enforceRateLimit(command.principalId)

        logger.info("Initiating account closure for principal {}", command.principalId)
        orchestrationPort.execute(command.principalId)
        logger.info("Account closure completed for principal {}", command.principalId)
    }

    private fun validateConfirmation(command: CloseAccountCommand) {
        if (command.confirmation != CONFIRMATION_REQUIRED) {
            throw CloseAccountConfirmationException(
                "Account closure requires confirmation text \"$CONFIRMATION_REQUIRED\"",
            )
        }
    }

    private fun enforceRateLimit(principalId: String) {
        if (!rateLimitPort.tryAcquire(principalId, RATE_LIMIT_DURATION, clock.instant())) {
            val minutes = RATE_LIMIT_DURATION.toMinutes()
            throw CloseAccountRateLimitException(
                "Account closure rate limit exceeded. " +
                    "Please wait $minutes minutes before trying again.",
            )
        }
    }

    companion object {
        private val RATE_LIMIT_DURATION: Duration = Duration.ofMinutes(5)
        private const val CONFIRMATION_REQUIRED: String = "DELETE"
    }
}

/**
 * Exception thrown when the account closure rate limit is exceeded.
 */
class CloseAccountRateLimitException(message: String) : RuntimeException(message)
