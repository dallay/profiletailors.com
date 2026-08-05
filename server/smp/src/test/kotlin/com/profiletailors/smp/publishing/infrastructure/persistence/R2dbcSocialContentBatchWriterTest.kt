package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class R2dbcSocialContentBatchWriterTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val scope = WorkspaceScope("workspace-1")
    private val actor = SocialContentActor(
        id = "account-1",
        scope = scope,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:1"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )

    @Test
    fun `commits posts and tombstones before saving checkpoint`() = runTest {
        val events = mutableListOf<String>()
        val writer = R2dbcSocialContentBatchWriter(
            postRepository = RecordingPostRepository(events),
            checkpointRepository = RecordingCheckpointRepository(events),
            transactionRunner = RecordingTransactionRunner(events),
        )

        writer.persist(
            posts = listOf(post("post-current")),
            tombstoneIds = setOf(ExternalPostId("post-current")),
            checkpoint = checkpoint(SyncResource.POSTS),
        )

        events shouldBe listOf(
            "transaction:start",
            "post:upsert:post-current",
            "post:tombstone-missing",
            "transaction:commit",
            "checkpoint:save",
        )
    }

    @Test
    fun `does not save checkpoint when batch commit fails`() = runTest {
        val events = mutableListOf<String>()
        val previous = checkpoint(SyncResource.POSTS).copy(
            cursor = PageCursor("previous"),
            highWaterMark = now.minusSeconds(60),
            lastSuccessfulAt = now.minusSeconds(60),
        )
        val checkpointRepository = RecordingCheckpointRepository(events, previous)
        val writer = R2dbcSocialContentBatchWriter(
            postRepository = RecordingPostRepository(events),
            checkpointRepository = checkpointRepository,
            transactionRunner = RecordingTransactionRunner(events, failAfterBlock = true),
        )

        shouldThrow<IllegalStateException> {
            writer.persist(
                posts = listOf(post("post-current")),
                tombstoneIds = setOf(ExternalPostId("post-current")),
                checkpoint = checkpoint(SyncResource.POSTS),
            )
        }

        events shouldBe listOf(
            "transaction:start",
            "post:upsert:post-current",
            "post:tombstone-missing",
        )
        checkpointRepository.saved shouldBe emptyList()
        checkpointRepository.current shouldBe previous
    }

    @Test
    fun `rejects a non-post checkpoint before opening a transaction`() = runTest {
        val events = mutableListOf<String>()
        val writer = R2dbcSocialContentBatchWriter(
            postRepository = RecordingPostRepository(events),
            checkpointRepository = RecordingCheckpointRepository(events),
            transactionRunner = RecordingTransactionRunner(events),
        )

        shouldThrow<IllegalArgumentException> {
            writer.persist(
                posts = listOf(post("post-1")),
                tombstoneIds = emptySet(),
                checkpoint = checkpoint(SyncResource.COMMENTS),
            )
        }

        events shouldBe emptyList()
    }

    private fun post(externalId: String): SocialPost = SocialPost.imported(
        scope = scope,
        actor = actor,
        externalPostId = ExternalPostId(externalId),
        publishedAt = now,
        now = now,
    )

    private fun checkpoint(resource: SyncResource): SyncCheckpoint = SyncCheckpoint(
        scope = scope,
        actorId = actor.id,
        resource = resource,
        cursor = null,
        highWaterMark = now,
        lastSuccessfulAt = now,
        provider = SocialProvider.LINKEDIN,
    )

    private class RecordingTransactionRunner(
        private val events: MutableList<String>,
        private val failAfterBlock: Boolean = false,
    ) : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            events += "transaction:start"
            val result = block()
            if (failAfterBlock) {
                error("configured transaction commit failure")
            }
            events += "transaction:commit"
            return result
        }
    }

    private class RecordingPostRepository(
        private val events: MutableList<String>,
        private val failOnTombstone: Boolean = false,
    ) : SocialContentPostRepository {
        override suspend fun upsert(post: SocialPost): SocialPost {
            events += "post:upsert:${post.externalPostId.value}"
            return post
        }

        override suspend fun findByWorkspaceAndExternalId(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            externalPostId: ExternalPostId,
        ): SocialPost? = null

        override suspend fun tombstoneMissing(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            seenExternalIds: Set<ExternalPostId>,
        ) {
            events += "post:tombstone-missing"
            if (failOnTombstone) {
                error("configured tombstone failure")
            }
        }
    }

    private class RecordingCheckpointRepository(
        private val events: MutableList<String>,
        var current: SyncCheckpoint? = null,
    ) : SocialContentCheckpointRepository {
        val saved = mutableListOf<SyncCheckpoint>()

        override suspend fun find(scope: WorkspaceScope, actorId: String, resource: SyncResource): SyncCheckpoint? =
            current

        override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
            events += "checkpoint:save"
            current = checkpoint
            saved += checkpoint
            return checkpoint
        }
    }
}
