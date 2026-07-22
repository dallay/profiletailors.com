package com.profiletailors.smp.identity.application

import com.profiletailors.smp.privacy.application.CredentialsRevocationPort
import com.profiletailors.smp.privacy.application.IdentityAnonymizationPort
import com.profiletailors.smp.privacy.application.MediaDeletionPort
import com.profiletailors.smp.privacy.application.PublishingDeletionPort
import com.profiletailors.common.domain.Service
import com.profiletailors.smp.privacy.application.TenancyDataPort
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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
    private val identityAnonymizationPort: IdentityAnonymizationPort,
    private val credentialsRevocationPort: CredentialsRevocationPort,
    private val publishingDeletionPort: PublishingDeletionPort,
    private val mediaDeletionPort: MediaDeletionPort,
    private val tenancyDataPort: TenancyDataPort,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val lastClosureAttempt: MutableMap<String, Instant> = ConcurrentHashMap()

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

        // Step 1: Revoke credentials (sessions + API keys)
        credentialsRevocationPort.revokeAllSessions(command.principalId)
        credentialsRevocationPort.deleteAllApiKeys(command.principalId)
        logger.debug("Revoked credentials for principal {}", command.principalId)

        // Step 2: Clean up publishing context
        publishingDeletionPort.cancelPendingPublications(command.principalId)
        publishingDeletionPort.deleteSocialConnections(command.principalId)
        publishingDeletionPort.deleteSecureCredentials(command.principalId)
        logger.debug("Cleaned up publishing data for principal {}", command.principalId)

        // Step 3: Capture workspace IDs before removing memberships
        val workspaceIds = tenancyDataPort.getMembershipWorkspaceIds(command.principalId)

        // Step 4: Mark media for deletion
        if (workspaceIds.isNotEmpty()) {
            mediaDeletionPort.markAssetsDeleted(command.principalId, workspaceIds)
            mediaDeletionPort.markBlobsReadyForGc(command.principalId, workspaceIds)
            logger.debug("Marked media assets for GC for principal {}", command.principalId)
        }

        // Step 5: Remove workspace memberships
        tenancyDataPort.removeAllMemberships(command.principalId)
        logger.debug("Removed workspace memberships for principal {}", command.principalId)

        // Step 6: Anonymize identity
        val now = clock.instant()
        identityAnonymizationPort.anonymizeUserIdentity(command.principalId, now)
        identityAnonymizationPort.anonymizePrincipalDisplayIdentity(command.principalId)
        logger.debug("Anonymized identity for principal {}", command.principalId)

        logger.info("Account closure completed for principal {}", command.principalId)
        // Note: Audit event emission is deferred until a shared audit facility is available.
        // Currently covered by structured logging.
    }

    private fun validateConfirmation(command: CloseAccountCommand) {
        require(command.confirmation == CONFIRMATION_REQUIRED) {
            "Account closure requires confirmation text \"$CONFIRMATION_REQUIRED\""
        }
    }

    private fun enforceRateLimit(principalId: String) {
        val now = clock.instant()
        val lastAttempt = lastClosureAttempt[principalId]
        if (lastAttempt != null && Duration.between(lastAttempt, now) < RATE_LIMIT_DURATION) {
            val minutes = RATE_LIMIT_DURATION.toMinutes()
            throw CloseAccountRateLimitException(
                "Account closure rate limit exceeded. " +
                    "Please wait $minutes minutes before trying again.",
            )
        }
        lastClosureAttempt[principalId] = now
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
