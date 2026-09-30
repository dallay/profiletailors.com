package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

data class CredentialPurgeCandidate(val id: UUID, val provider: SocialProvider, val accessTokenExpiresAt: Instant?)

interface CredentialPurgeRepository {
    fun findOrphanExpiredCandidates(
        provider: SocialProvider,
        expiredBefore: Instant,
        batchSize: Int,
    ): Flow<CredentialPurgeCandidate>

    suspend fun deleteById(id: UUID): Boolean
}
