package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

@ValueObject
enum class CredentialDeletionReason {
    DISCONNECT,
    EXPIRED,
}

@AggregateRoot
data class CredentialDeletionTombstone(
    val workspaceId: String,
    val connectionId: String,
    val provider: SocialProvider,
    val providerConnectionRef: String,
    val credentialReference: String?,
    val activityId: String,
    val policyVersion: String,
    val reason: CredentialDeletionReason,
    val deletedAt: Instant,
) {
    init {
        require(workspaceId.isNotBlank()) { "Workspace id is required." }
        require(connectionId.isNotBlank()) { "Connection id is required." }
        require(providerConnectionRef.isNotBlank()) { "Provider connection ref is required." }
        require(activityId.isNotBlank()) { "Activity id is required." }
    }
}

interface CredentialDeletionTombstoneRepository {
    suspend fun findByWorkspaceAndConnection(workspaceId: String, connectionId: String): CredentialDeletionTombstone?
    suspend fun record(tombstone: CredentialDeletionTombstone): CredentialDeletionTombstone
}

object NoOpCredentialDeletionTombstoneRepository : CredentialDeletionTombstoneRepository {
    override suspend fun findByWorkspaceAndConnection(
        workspaceId: String,
        connectionId: String,
    ): CredentialDeletionTombstone? = null
    override suspend fun record(tombstone: CredentialDeletionTombstone): CredentialDeletionTombstone = tombstone
}
