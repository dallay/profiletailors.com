package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.application.CredentialRetentionJob
import com.profiletailors.smp.publishing.domain.CredentialPurgeCandidate
import com.profiletailors.smp.publishing.domain.CredentialPurgeRepository
import com.profiletailors.smp.publishing.domain.CredentialRetentionRule
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CredentialRetentionSchedulerTest {
    @Test
    fun `skips the run while the purge stays disabled`() = runTest {
        val repository = TrackingPurgeRepository()
        val scheduler = CredentialRetentionScheduler(
            retentionJob = CredentialRetentionJob(
                purgeRepository = repository,
                retentionRule = enabledRule(),
            ),
            retentionProperties = CredentialRetentionProperties(enabled = false),
        )

        scheduler.runRetentionJob()

        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `delegates dry run and batch size from configuration`() = runTest {
        val candidate = CredentialPurgeCandidate(UUID.randomUUID(), SocialProvider.THREADS, Instant.now())
        val repository = TrackingPurgeRepository(listOf(candidate))
        val scheduler = CredentialRetentionScheduler(
            retentionJob = CredentialRetentionJob(
                purgeRepository = repository,
                retentionRule = enabledRule(),
            ),
            retentionProperties = CredentialRetentionProperties(enabled = true, dryRun = true, batchSize = 10),
        )

        scheduler.runRetentionJob()

        assertTrue(repository.deleted.isEmpty())
        assertEquals(10, repository.lastBatchSize)
    }

    private fun enabledRule() = CredentialRetentionRule(
        activityId = "pa-006",
        policyVersion = "",
        expiredMetadataRetention = Duration.ofDays(30),
        disconnectGrace = Duration.ZERO,
        enabled = true,
    )

    private class TrackingPurgeRepository(private val candidates: List<CredentialPurgeCandidate> = emptyList()) :
        CredentialPurgeRepository {
        val deleted = mutableListOf<UUID>()
        var lastBatchSize = 0

        override fun findOrphanExpiredCandidates(
            provider: SocialProvider,
            expiredBefore: Instant,
            batchSize: Int,
        ): Flow<CredentialPurgeCandidate> {
            lastBatchSize = batchSize
            return candidates.asFlow()
        }

        override suspend fun deleteById(id: UUID): Boolean {
            deleted += id
            return true
        }
    }
}
