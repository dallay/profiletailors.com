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
    private val identityPort: IdentityDataPort,
    private val credentialsPort: CredentialsDataPort,
    private val tenancyPort: TenancyAggregationPort,
    private val publishingPort: PublishingDataPort,
    private val mediaPort: MediaDataPort,
    private val governancePort: GovernanceDataPort,
    private val leadCapturePort: LeadCaptureDataPort,
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
        val identityFacts = identityPort.getIdentityFacts(principalId)
        val sessions = credentialsPort.getSessions(principalId)
        val apiKeys = credentialsPort.getApiKeys(principalId)
        val workspaceMemberships = tenancyPort.getWorkspaceMemberships(principalId)
        val socialConnections = publishingPort.getSocialConnections(principalId)
        val socialAccounts = publishingPort.getSocialAccounts(principalId)
        val publications = publishingPort.getPublications(principalId)
        val mediaAssets = mediaPort.getMediaAssets(principalId)
        val consentRecords = governancePort.getConsentRecords(email)
        val waitlistEntries = leadCapturePort.getWaitlistEntries(email)

        return buildMap {
            put("_metadata", mapOf(
                "generatedAt" to Instant.now().toString(),
                "principalId" to principalId,
            ))
            put("identity", identityFacts)
            put("credentials", mapOf(
                "sessions" to sessions,
                "apiKeys" to apiKeys,
            ))
            put("workspaces", workspaceMemberships)
            put("publishing", mapOf(
                "socialConnections" to socialConnections,
                "socialAccounts" to socialAccounts,
                "publications" to publications,
            ))
            put("media", mapOf(
                "assets" to mediaAssets,
            ))
            put("governance", mapOf(
                "consentRecords" to consentRecords,
            ))
            put("leadCapture", mapOf(
                "waitlistEntries" to waitlistEntries,
            ))
        }
    }
}
