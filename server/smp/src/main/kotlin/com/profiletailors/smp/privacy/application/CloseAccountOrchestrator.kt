package com.profiletailors.smp.privacy.application

import com.profiletailors.smp.identity.application.CloseAccountOrchestrationPort
import org.slf4j.LoggerFactory

/**
 * Implementation of [CloseAccountOrchestrationPort] that coordinates all
 * account closure deletion steps across privacy sub-domains.
 *
 * Lives in [com.profiletailors.smp.privacy.application] because it requires
 * direct access to the privacy-layer ports that perform credential
 * revocation, identity anonymization, media/publishing cleanup, and
 * tenancy data removal.
 *
 * NOTE: [@Service] annotation intentionally omitted because the 5 sub-ports
 * ([IdentityAnonymizationPort], [CredentialsRevocationPort], [PublishingDeletionPort],
 * [MediaDeletionPort], [TenancyDataPort]) do not yet have infrastructure-layer
 * implementations. This class will be registered as a Spring bean once those
 * ports are wired. Until then, [CloseAccountHandler] receives a stub
 * [CloseAccountOrchestrationPort] from [com.profiletailors.smp.identity.infrastructure.RateLimitConfiguration].
 */
class CloseAccountOrchestrator(
    private val identityAnonymizationPort: IdentityAnonymizationPort,
    private val credentialsRevocationPort: CredentialsRevocationPort,
    private val publishingDeletionPort: PublishingDeletionPort,
    private val mediaDeletionPort: MediaDeletionPort,
    private val tenancyDataPort: TenancyDataPort,
) : CloseAccountOrchestrationPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(principalId: String) {
        logger.info("Executing account closure for principal {}", principalId)

        // Step 1: Revoke credentials (sessions + API keys)
        credentialsRevocationPort.revokeAllSessions(principalId)
        credentialsRevocationPort.deleteAllApiKeys(principalId)
        logger.debug("Revoked credentials for principal {}", principalId)

        // Step 2: Clean up publishing context
        publishingDeletionPort.cancelPendingPublications(principalId)
        publishingDeletionPort.deleteSocialConnections(principalId)
        publishingDeletionPort.deleteSecureCredentials(principalId)
        logger.debug("Cleaned up publishing data for principal {}", principalId)

        // Step 3: Capture workspace IDs before removing memberships
        val workspaceIds = tenancyDataPort.getMembershipWorkspaceIds(principalId)

        // Step 4: Mark media for deletion
        if (workspaceIds.isNotEmpty()) {
            mediaDeletionPort.markAssetsDeleted(principalId, workspaceIds)
            mediaDeletionPort.markBlobsReadyForGc(principalId, workspaceIds)
            logger.debug("Marked media assets for GC for principal {}", principalId)
        }

        // Step 5: Remove workspace memberships
        tenancyDataPort.removeAllMemberships(principalId)
        logger.debug("Removed workspace memberships for principal {}", principalId)

        // Step 6: Anonymize identity
        val now = java.time.Clock.systemUTC().instant()
        identityAnonymizationPort.anonymizeUserIdentity(principalId, now)
        identityAnonymizationPort.anonymizePrincipalDisplayIdentity(principalId)
        logger.debug("Anonymized identity for principal {}", principalId)

        logger.info("Account closure completed for principal {}", principalId)
        // Note: Audit event emission is deferred until a shared audit facility is available.
        // Currently covered by structured logging.
    }
}
