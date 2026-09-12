package com.profiletailors.smp.privacy.application

import com.profiletailors.observability.NoOpOperationalEventSink
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import com.profiletailors.observability.emit
import com.profiletailors.smp.identity.application.CloseAccountOrchestration

/**
 * Implementation of [CloseAccountOrchestration] that coordinates all
 * account closure deletion steps across privacy sub-domains.
 *
 * Lives in [com.profiletailors.smp.privacy.application] because it requires
 * direct access to the privacy-layer ports that perform credential
 * revocation, identity anonymization, media/publishing cleanup, and
 * tenancy data removal.
 *
 * NOTE: [@Service] annotation intentionally omitted because the 5 sub-ports
 * ([IdentityAnonymization], [CredentialsRevocation], [PublishingDeletion],
 * [MediaDeletion], [TenancyData]) do not yet have infrastructure-layer
 * implementations. This class will be registered as a Spring bean once those
 * ports are wired. Until then, [CloseAccountHandler] receives a stub
 * [CloseAccountOrchestration] from [com.profiletailors.smp.identity.infrastructure.RateLimitConfiguration].
 */
class CloseAccountOrchestrator(
    private val identityAnonymization: IdentityAnonymization,
    private val credentialsRevocation: CredentialsRevocation,
    private val publishingDeletion: PublishingDeletion,
    private val mediaDeletion: MediaDeletion,
    private val tenancyData: TenancyData,
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) : CloseAccountOrchestration {

    override suspend fun execute(principalId: String) {
        operationalEvents.emit(Severity.INFO, "privacy.accountClosure.started")

        // Step 1: Revoke credentials (sessions + API keys)
        credentialsRevocation.revokeAllSessions(principalId)
        credentialsRevocation.deleteAllApiKeys(principalId)
        operationalEvents.emit(Severity.DEBUG, "privacy.accountClosure.credentialsRevoked")

        // Step 2: Clean up publishing context
        publishingDeletion.cancelPendingPublications(principalId)
        publishingDeletion.deleteSocialConnections(principalId)
        publishingDeletion.deleteSecureCredentials(principalId)
        operationalEvents.emit(Severity.DEBUG, "privacy.accountClosure.publishingCleaned")

        // Step 3: Capture workspace IDs before removing memberships
        val workspaceIds = tenancyData.getMembershipWorkspaceIds(principalId)

        // Step 4: Mark media for deletion
        if (workspaceIds.isNotEmpty()) {
            mediaDeletion.markAssetsDeleted(principalId, workspaceIds)
            mediaDeletion.markBlobsReadyForGc(principalId, workspaceIds)
            operationalEvents.emit(Severity.DEBUG, "privacy.accountClosure.mediaMarkedForGc")
        }

        // Step 5: Remove workspace memberships
        tenancyData.removeAllMemberships(principalId)
        operationalEvents.emit(Severity.DEBUG, "privacy.accountClosure.membershipsRemoved")

        // Step 6: Anonymize identity
        val now = java.time.Clock.systemUTC().instant()
        identityAnonymization.anonymizeUserIdentity(principalId, now)
        identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        operationalEvents.emit(Severity.DEBUG, "privacy.accountClosure.identityAnonymized")

        operationalEvents.emit(Severity.INFO, "privacy.accountClosure.completed")
        // Note: Audit event emission is deferred until a shared audit facility is available.
        // Currently covered by structured logging.
    }
}
