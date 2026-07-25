package com.profiletailors.smp.privacy.application

import java.time.Instant

data class PrivacyPrincipalIdentityFacts(
    val principalId: String,
    val subject: String,
    val email: String?,
    val username: String?,
    val provider: String?,
    val displayIdentity: String,
    val emailStatus: String?,
)

/**
 * Port for anonymizing identity data (user_identities, principals).
 *
 * Implemented by an infrastructure adapter that performs the actual
 * database operations against the identity context.
 */
interface IdentityAnonymizationPort {
    /**
     * Anonymize a user_identity's email and username fields.
     * Must be idempotent — calling twice on the same record must not fail.
     */
    suspend fun anonymizeUserIdentity(principalId: String, timestamp: Instant)

    /**
     * Set the principals.display_identity to [REDACTED].
     * Must be idempotent.
     */
    @Suppress("FunctionNameMaxLength")
    suspend fun anonymizePrincipalDisplayIdentity(principalId: String)

    /**
     * Correct the email on a user_identity.
     * @return the old email value, or null if the principal was not found
     */
    suspend fun correctUserIdentityEmail(principalId: String, newEmail: String): String?

    /**
     * Correct the username on a user_identity.
     * @return the old username value, or null if the principal was not found
     */
    suspend fun correctUserIdentityUsername(principalId: String, newUsername: String): String?
}

/**
 * Port for anonymizing waitlist entries by email.
 *
 * Implemented by an infrastructure adapter that performs the actual
 * database operations against the lead-capture context.
 */
fun interface WaitlistAnonymizationPort {
    /**
     * Anonymize all waitlist entries matching the given email.
     * Sets email to [REDACTED on {timestamp}] and clears metadata to {}.
     * Must be idempotent.
     */
    suspend fun anonymizeByEmail(email: String, timestamp: Instant)
}

/**
 * Port for revoking credentials (sessions, API keys).
 */
interface CredentialsRevocationPort {
    /** Delete all refresh sessions for the given principal. */
    suspend fun revokeAllSessions(principalId: String)

    /** Delete all API keys for the given principal. */
    suspend fun deleteAllApiKeys(principalId: String)
}

/**
 * Port for tenancy operations (memberships, ownership checks).
 */
interface TenancyDataPort {
    /**
     * Remove all workspace memberships for the given principal by
     * setting their status to REMOVED. Returns the list of workspace IDs
     * that were affected.
     */
    suspend fun removeAllMemberships(principalId: String): List<String>

    /**
     * Get all workspace IDs the principal is a member of.
     */
    suspend fun getMembershipWorkspaceIds(principalId: String): List<String>

    /**
     * Check if the principal is the sole owner of any workspace.
     */
    suspend fun isSoleOwnerInAnyWorkspace(principalId: String): Boolean
}

/**
 * Port for publishing context operations during deletion.
 */
interface PublishingDeletionPort {
    /** Delete all social connections for the given principal. */
    suspend fun deleteSocialConnections(principalId: String)

    /** Delete all secure credentials for the given principal. */
    suspend fun deleteSecureCredentials(principalId: String)

    /** Cancel all pending (DRAFT, SCHEDULED, QUEUED) publications for the principal. */
    suspend fun cancelPendingPublications(principalId: String)
}

/**
 * Port for media operations during deletion.
 */
interface MediaDeletionPort {
    /**
     * Mark all media assets authored by the given principal as DELETED.
     */
    suspend fun markAssetsDeleted(principalId: String, workspaceIds: List<String>)

    /**
     * Mark all blobs associated with the given principal's assets as READY_FOR_GC.
     */
    suspend fun markBlobsReadyForGc(principalId: String, workspaceIds: List<String>)
}

/**
 * Port for data aggregation from the identity context.
 */
fun interface IdentityDataPort {
    suspend fun getIdentityFacts(principalId: String): PrivacyPrincipalIdentityFacts?
}

/**
 * Port for data aggregation from the credentials context.
 */
interface CredentialsDataPort {
    suspend fun getSessions(principalId: String): List<Map<String, Any?>>
    suspend fun getApiKeys(principalId: String): List<Map<String, Any?>>
}

/**
 * Port for data aggregation from the tenancy context.
 */
interface TenancyAggregationPort {
    suspend fun getWorkspaceMemberships(principalId: String): List<Map<String, Any?>>
    suspend fun getWorkspaceInfo(workspaceId: String): Map<String, Any?>?
}

/**
 * Port for data aggregation from the publishing context.
 */
interface PublishingDataPort {
    suspend fun getSocialConnections(principalId: String): List<Map<String, Any?>>
    suspend fun getSocialAccounts(principalId: String): List<Map<String, Any?>>
    suspend fun getPublications(principalId: String): List<Map<String, Any?>>
}

/**
 * Port for data aggregation from the media context.
 */
fun interface MediaDataPort {
    suspend fun getMediaAssets(principalId: String): List<Map<String, Any?>>
}

/**
 * Port for data aggregation from the governance context.
 */
fun interface GovernanceDataPort {
    suspend fun getConsentRecords(email: String): List<Map<String, Any?>>
}

/**
 * Port for data aggregation from the lead-capture context.
 */
fun interface LeadCaptureDataPort {
    suspend fun getWaitlistEntries(email: String): List<Map<String, Any?>>
}

/**
 * Port for storage operations (upload + presigned URLs).
 */
fun interface StoragePort {
    /**
     * Upload a JSON string and return a downloadable URL.
     *
     * For small content (≤10MB), the storage adapter may inline the data.
     * For larger content, it uploads to blob storage and returns a presigned URL.
     */
    suspend fun uploadJson(key: String, content: String): String
}
