package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CredentialPurgeCandidate
import com.profiletailors.smp.publishing.domain.CredentialPurgeRepository
import com.profiletailors.smp.publishing.domain.CredentialRetentionRule
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CredentialRetentionJobTest {
    @Test
    fun `deletes orphan expired candidates when provider is enabled`() = runTest {
        val expiredId = UUID.randomUUID()
        val repository = FakePurgeRepository(
            candidates = mapOf(
                SocialProvider.THREADS to listOf(
                    CredentialPurgeCandidate(expiredId, SocialProvider.THREADS, Instant.now().minusSeconds(3600)),
                ),
            ),
        )
        val job = CredentialRetentionJob(
            purgeRepository = repository,
            retentionRule = CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = true,
            ),
        )

        val result = job.run(dryRun = false, batchSize = 100)

        assertEquals(1, result.scanned)
        assertEquals(1, result.deleted)
        assertEquals(0, result.errors)
        assertEquals(false, result.dryRun)
        assertTrue(repository.deleted.contains(expiredId))
    }

    @Test
    fun `skips disabled providers`() = runTest {
        val repository = FakePurgeRepository(
            candidates = mapOf(
                SocialProvider.THREADS to listOf(
                    CredentialPurgeCandidate(UUID.randomUUID(), SocialProvider.THREADS, Instant.now()),
                ),
            ),
        )
        val job = CredentialRetentionJob(
            purgeRepository = repository,
            retentionRule = CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = false,
            ),
        )

        val result = job.run(dryRun = false, batchSize = 100)

        assertEquals(0, result.scanned)
        assertEquals(0, result.deleted)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `dry run reports without deleting`() = runTest {
        val expiredId = UUID.randomUUID()
        val repository = FakePurgeRepository(
            candidates = mapOf(
                SocialProvider.THREADS to listOf(
                    CredentialPurgeCandidate(expiredId, SocialProvider.THREADS, Instant.now()),
                ),
            ),
        )
        val job = CredentialRetentionJob(
            purgeRepository = repository,
            retentionRule = CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = true,
            ),
        )

        val result = job.run(dryRun = true, batchSize = 100)

        assertEquals(true, result.dryRun)
        assertEquals(0, result.deleted)
        assertTrue(result.scanned >= 1)
        assertTrue(result.sampleCredentialIds.contains(expiredId.toString()))
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `counts partial failure and continues with remaining candidates`() = runTest {
        val failingId = UUID.randomUUID()
        val passingId = UUID.randomUUID()
        val repository = FakePurgeRepository(
            candidates = mapOf(
                SocialProvider.THREADS to listOf(
                    CredentialPurgeCandidate(failingId, SocialProvider.THREADS, Instant.now()),
                    CredentialPurgeCandidate(passingId, SocialProvider.THREADS, Instant.now()),
                ),
            ),
            failingIds = setOf(failingId),
        )
        val job = CredentialRetentionJob(
            purgeRepository = repository,
            retentionRule = CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = true,
            ),
        )

        val result = job.run(dryRun = false, batchSize = 100)

        assertEquals(1, result.errors)
        assertEquals(1, result.deleted)
        assertTrue(repository.deleted.contains(passingId))
    }

    @Test
    fun `rejects batch size outside the inclusive range`() = runTest {
        val job = CredentialRetentionJob(
            purgeRepository = FakePurgeRepository(emptyMap()),
            retentionRule = CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = true,
            ),
        )

        shouldThrow<IllegalArgumentException> {
            job.run(dryRun = false, batchSize = 0)
        }
        shouldThrow<IllegalArgumentException> {
            job.run(dryRun = false, batchSize = 1001)
        }
    }

    private class FakePurgeRepository(
        private val candidates: Map<SocialProvider, List<CredentialPurgeCandidate>>,
        private val failingIds: Set<UUID> = emptySet(),
    ) : CredentialPurgeRepository {
        val deleted = mutableListOf<UUID>()

        override fun findOrphanExpiredCandidates(
            provider: SocialProvider,
            expiredBefore: Instant,
            batchSize: Int,
        ): Flow<CredentialPurgeCandidate> {
            if (provider == SocialProvider.LINKEDIN) {
                return flow { }
            }
            return (candidates[provider] ?: emptyList()).asFlow()
        }

        override suspend fun deleteById(id: UUID): Boolean {
            if (failingIds.contains(id)) {
                throw IllegalStateException("Storage delete failed for $id")
            }
            deleted += id
            return true
        }
    }
}
