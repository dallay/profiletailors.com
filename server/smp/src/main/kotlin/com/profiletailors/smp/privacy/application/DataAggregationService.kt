package com.profiletailors.smp.privacy.application

import java.time.Instant

/**
 * Application service that aggregates personal data across all bounded contexts
 * for data subject access and export requests.
 *
 * Returns a JSON-serializable map containing the principal's data from every
 * context that may hold it.
 */
class DataAggregationService(
    private val identityAnonymization: IdentityData,
    private val credentials: CredentialsData,
    private val tenancyData: TenancyAggregation,
    private val publishing: PublishingData,
    private val media: MediaData,
    private val governanceData: GovernanceData,
    private val leadCaptureData: LeadCaptureData,
) {

    /**
     * Aggregate all personal data for the given [principalId] and [email].
     *
     * @return A JSON-serializable map with the following top-level keys:
     *   - _metadata: { generatedAt, principalId }
     *   - identity: the identity facts or null
     *   - credentials: { sessions, apiKeys }
     *   - workspaces: list of workspace memberships
     *   - publishing: { socialConnections, socialAccounts, publications }
     *   - media: { assets }
     *   - governance: { consentRecords }
     *   - leadCapture: { waitlistEntries }
     */
    suspend fun aggregate(principalId: String, email: String): Map<String, Any?> {
        val identityFacts = identityAnonymization.getIdentityFacts(principalId)
        val sessions = credentials.getSessions(principalId)
        val apiKeys = credentials.getApiKeys(principalId)
        val workspaceMemberships = tenancyData.getWorkspaceMemberships(principalId)
        val socialConnections = publishing.getSocialConnections(principalId)
        val socialAccounts = publishing.getSocialAccounts(principalId)
        val publications = publishing.getPublications(principalId)
        val mediaAssets = media.getMediaAssets(principalId)
        val consentRecords = governanceData.getConsentRecords(email)
        val waitlistEntries = leadCaptureData.getWaitlistEntries(email)

        return buildMap {
            put(
                "_metadata",
                mapOf(
                    "generatedAt" to Instant.now().toString(),
                    "principalId" to principalId,
                ),
            )
            put("identity", identityFacts)
            put(
                "credentials",
                mapOf(
                    "sessions" to sessions,
                    "apiKeys" to apiKeys,
                ),
            )
            put("workspaces", workspaceMemberships)
            put(
                "publishing",
                mapOf(
                    "socialConnections" to socialConnections,
                    "socialAccounts" to socialAccounts,
                    "publications" to publications,
                ),
            )
            put(
                "media",
                mapOf(
                    "assets" to mediaAssets,
                ),
            )
            put(
                "governance",
                mapOf(
                    "consentRecords" to consentRecords,
                ),
            )
            put(
                "leadCapture",
                mapOf(
                    "waitlistEntries" to waitlistEntries,
                ),
            )
        }
    }
}
