package com.profiletailors.smp.privacy.application

import java.time.Instant

/**
 * Application service for anonymization and deletion of personal data
 * across bounded contexts.
 *
 * Each method orchestrates the sequence of operations needed to
 * fulfill a data subject request.
 */
class AnonymizationService(
    private val identityAnonymization: IdentityAnonymization,
    private val waitlistAnonymization: WaitlistAnonymization,
    private val credentials: CredentialsRevocation,
    private val tenancyData: TenancyData,
    private val publishing: PublishingDeletion,
    private val media: MediaDeletion,
) {

    /**
     * Anonymize all PII associated with a principal.
     *
     * Operations:
     * 1. Anonymize user_identity (email → [REDACTED on ts], username → [REDACTED])
     * 2. Anonymize principal display_identity → [REDACTED]
     * 3. Anonymize waitlist entries matching email
     * 4. Revoke all sessions
     * 5. Delete all API keys
     */
    suspend fun anonymizePII(principalId: String, email: String, timestamp: Instant) {
        identityAnonymization.anonymizeUserIdentity(principalId, timestamp)
        identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        waitlistAnonymization.anonymizeByEmail(email, timestamp)
        credentials.revokeAllSessions(principalId)
        credentials.deleteAllApiKeys(principalId)
    }

    /**
     * Partial anonymization for deletion Phase 1 — only identity + waitlist,
     * without credential revocation (which is handled in Phase 2).
     */
    suspend fun anonymizeIdentityAndWaitlist(principalId: String, email: String, timestamp: Instant) {
        identityAnonymization.anonymizeUserIdentity(principalId, timestamp)
        identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        waitlistAnonymization.anonymizeByEmail(email, timestamp)
    }

    /**
     * Revoke all credentials (sessions + API keys).
     * Used in deletion Phase 2.
     */
    suspend fun revokeCredentials(principalId: String) {
        credentials.revokeAllSessions(principalId)
        credentials.deleteAllApiKeys(principalId)
    }

    /**
     * Anonymize waitlist entries matching the given email.
     * Used by the correction handler to propagate email changes.
     */
    suspend fun anonymizeWaitlistByEmail(email: String, timestamp: Instant) {
        waitlistAnonymization.anonymizeByEmail(email, timestamp)
    }

    /**
     * Mark media assets and blobs for garbage collection.
     * Used in deletion Phase 3.
     */
    suspend fun markMediaForGc(principalId: String, workspaceIds: List<String>) {
        media.markAssetsDeleted(principalId, workspaceIds)
        media.markBlobsReadyForGc(principalId, workspaceIds)
    }

    /**
     * Apply a correction to a principal's identity field.
     *
     * @return [CorrectionResult.Success] when the correction was applied,
     *         [CorrectionResult.NotFound] if no identity record was found.
     */
    suspend fun verifyCorrection(principalId: String, field: CorrectionField, newValue: String): CorrectionResult {
        val changed = when (field) {
            CorrectionField.EMAIL -> identityAnonymization.correctUserIdentityEmail(principalId, newValue)
            CorrectionField.USERNAME -> identityAnonymization.correctUserIdentityUsername(principalId, newValue)
        }
        return if (changed != null) {
            CorrectionResult.Success
        } else {
            CorrectionResult.NotFound
        }
    }

    /**
     * Delete all data associated with a principal across all contexts.
     */
    suspend fun deleteData(principalId: String) {
        val workspaceIds = tenancyData.removeAllMemberships(principalId)

        if (workspaceIds.isNotEmpty()) {
            media.markAssetsDeleted(principalId, workspaceIds)
            media.markBlobsReadyForGc(principalId, workspaceIds)
        }

        publishing.deleteSocialConnections(principalId)
        publishing.deleteSecureCredentials(principalId)
        publishing.cancelPendingPublications(principalId)
        credentials.revokeAllSessions(principalId)
        credentials.deleteAllApiKeys(principalId)
    }

    /**
     * Result of applying a correction.
     */
    sealed interface CorrectionResult {
        /** The correction was applied successfully. */
        data object Success : CorrectionResult

        /** No identity record was found for the given principal. */
        data object NotFound : CorrectionResult
    }
}
