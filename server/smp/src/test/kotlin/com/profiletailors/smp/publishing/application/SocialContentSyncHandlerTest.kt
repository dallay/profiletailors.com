package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialContentSyncSuspension
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SocialContentSyncHandlerTest {
    private val scope = WorkspaceScope("workspace-1")
    private val actor = SocialContentActor(
        id = "page-1",
        scope = scope,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )
    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24))

    @Test
    fun `sync sends posts tombstones and checkpoint through one batch writer`() = runTest {
        val post = page("post-1", null).items.single()
        val writer = RecordingBatchWriter()
        val handler = SocialContentSyncHandler(
            provider = ScriptedProvider(page("post-1", null)),
            postRepository = RecordingPostRepository(),
            checkpointRepository = RecordingCheckpointRepository(),
            batchWriter = writer,
            capabilityResolver = DefaultCapabilityResolver(SocialContentFeatureGates(importEnabled = true)),
            retention = retention,
            gates = SocialContentFeatureGates(importEnabled = true),
            backoff = { _, _ -> },
        )

        handler.importPosts(actor, now)

        writer.posts shouldBe listOf(post.copy(expiresAt = now.plus(retention.activityTtl)))
        writer.tombstoneIds shouldBe setOf(ExternalPostId("post-1"))
        writer.checkpoint.lastSuccessfulAt shouldBe now
    }

    @Test
    fun `full sync with an empty provider result does not tombstone every existing post`() = runTest {
        val existing = SocialPost.imported(
            scope = scope,
            actor = actor,
            externalPostId = ExternalPostId("post-existing"),
            publishedAt = now.minusSeconds(60),
            now = now,
        )
        val posts = RecordingPostRepository(initial = listOf(existing))
        val writer = RecordingBatchWriter(posts)
        val handler = SocialContentSyncHandler(
            provider = ScriptedProvider(
                SocialContentPage<SocialPost>(emptyList(), null, now),
            ),
            postRepository = posts,
            checkpointRepository = RecordingCheckpointRepository(),
            batchWriter = writer,
            capabilityResolver = DefaultCapabilityResolver(SocialContentFeatureGates(importEnabled = true)),
            retention = retention,
            gates = SocialContentFeatureGates(importEnabled = true),
            backoff = { _, _ -> },
        )

        handler.importPosts(actor, now)

        writer.tombstoneIds shouldBe emptySet()
        posts.upserted.single().lifecycle shouldBe PostLifecycle.PUBLISHED
    }

    @Test
    fun `sync preserves an existing local publication reconciliation`() = runTest {
        val providerPost = page("post-1", null).items.single()
        val localPublication = providerPost.copy(
            origin = com.profiletailors.smp.publishing.domain.PostOrigin.PROFILETAILORS,
            localPublicationId = "publication-1",
        )
        val posts = RecordingPostRepository(initial = listOf(localPublication))
        val handler = SocialContentSyncHandler(
            ScriptedProvider(page("post-1", null)),
            posts,
            RecordingCheckpointRepository(),
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(),
            backoff = { _, _ -> },
        )

        val result = handler.importPosts(actor, now)

        result.items.single().origin shouldBe com.profiletailors.smp.publishing.domain.PostOrigin.PROFILETAILORS
        result.items.single().localPublicationId shouldBe "publication-1"
    }

    @Test
    fun `sync retries rate limits and checkpoints only after all pages are persisted`() = runTest {
        val provider = ScriptedProvider(
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429, Duration.ZERO),
            page("post-1", "1"),
            page("post-2", null),
        )
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val waits = mutableListOf<Duration>()
        val handler = SocialContentSyncHandler(
            provider,
            posts,
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(posts, checkpoints),
            backoff = { _, duration -> waits += duration },
        )

        val result = handler.importPosts(actor, now)

        result.items.map { it.externalPostId.value } shouldBe listOf("post-1", "post-2")
        posts.upserted shouldHaveSize 2
        posts.tombstones shouldHaveSize 1
        checkpoints.saved.single().highWaterMark shouldBe Instant.parse("2026-08-02T12:00:00Z")
        waits shouldBe listOf(Duration.ZERO)
    }

    @Test
    fun `sync bounds rate limit retries and uses retry after before configured fallback backoff`() = runTest {
        val terminalFailure = SocialContentProviderException(
            SocialContentProviderFailure.RATE_LIMITED,
            429,
        )
        val provider = ScriptedProvider(
            SocialContentProviderException(
                SocialContentProviderFailure.RATE_LIMITED,
                429,
                Duration.ofSeconds(5),
            ),
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429),
            terminalFailure,
        )
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val writer = RecordingBatchWriter(posts, checkpoints)
        val waits = mutableListOf<Duration>()
        val handler = SocialContentSyncHandler(
            provider,
            posts,
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            maxRetries = 2,
            batchWriter = writer,
            backoff = { _, duration -> waits += duration },
        )

        val error = shouldThrow<SocialContentSyncStateException> { handler.importPosts(actor, now) }

        provider.calls shouldBe 3
        waits shouldBe listOf(
            Duration.ofSeconds(5),
            Duration.ofSeconds(4),
        )
        error.cause shouldBe terminalFailure
        posts.upserted shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
        writer.posts shouldBe emptyList()
    }

    @Test
    fun `sync leaves previous checkpoint unchanged when provider remains rate limited`() = runTest {
        val previous = SyncCheckpoint(
            scope,
            actor.id,
            SyncResource.POSTS,
            com.profiletailors.smp.publishing.domain.PageCursor("4"),
            now.minusSeconds(60),
            now.minusSeconds(60),
        )
        val provider = ScriptedProvider(
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429, Duration.ZERO),
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429, Duration.ZERO),
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429, Duration.ZERO),
            SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED, 429, Duration.ZERO),
        )
        val checkpoints = RecordingCheckpointRepository(previous)
        val handler = SocialContentSyncHandler(
            provider,
            RecordingPostRepository(),
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(),
            backoff = { _, _ -> },
        )

        runCatching { handler.importPosts(actor, now) }.isFailure shouldBe true
        checkpoints.saved shouldBe emptyList()
        checkpoints.current shouldBe previous
    }

    @Test
    fun `sync fails safely when pagination exceeds configured bound`() = runTest {
        val provider = RepeatingPageProvider(page("post-1", "next"))
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val handler = SocialContentSyncHandler(
            provider,
            posts,
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(),
            maxPages = 1,
            backoff = { _, _ -> },
        )

        shouldThrow<SocialContentSyncStateException> { handler.importPosts(actor, now) }
        posts.upserted shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
    }

    @Test
    fun `sync records reauthentication suspension without retrying unauthorized requests`() = runTest {
        val provider = ScriptedProvider(
            SocialContentProviderException(SocialContentProviderFailure.UNAUTHORIZED, 401),
        )
        val suspensions = mutableListOf<SocialContentSyncSuspension>()
        val handler = SocialContentSyncHandler(
            provider,
            RecordingPostRepository(),
            RecordingCheckpointRepository(),
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(),
            backoff = { _, _ -> },
            failureRecorder = { _, _, suspension -> suspensions += suspension },
        )

        shouldThrow<SocialContentSyncStateException> { handler.importPosts(actor, now) }
        provider.calls shouldBe 1
        suspensions shouldBe listOf(SocialContentSyncSuspension.REAUTH_REQUIRED)
    }

    @Test
    fun `sync records role and provider suspensions without retrying non-rate-limit failures`() = runTest {
        val cases = listOf(
            SocialContentProviderFailure.ROLE_FORBIDDEN to SocialContentSyncSuspension.ROLE_REQUIRED,
            SocialContentProviderFailure.PROVIDER_UNAVAILABLE to SocialContentSyncSuspension.PROVIDER_UNAVAILABLE,
        )

        cases.forEach { (failure, expectedSuspension) ->
            val provider = ScriptedProvider(SocialContentProviderException(failure, 403))
            val suspensions = mutableListOf<SocialContentSyncSuspension>()
            val handler = SocialContentSyncHandler(
                provider,
                RecordingPostRepository(),
                RecordingCheckpointRepository(),
                com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                    SocialContentFeatureGates(importEnabled = true),
                ),
                retention,
                SocialContentFeatureGates(importEnabled = true),
                batchWriter = RecordingBatchWriter(),
                backoff = { _, _ -> error("non-rate-limit failures must not back off") },
                failureRecorder = { _, _, suspension -> suspensions += suspension },
            )

            shouldThrow<SocialContentSyncStateException> { handler.importPosts(actor, now) }

            provider.calls shouldBe 1
            suspensions shouldBe listOf(expectedSuspension)
        }
    }

    @Test
    fun `incremental sync resumes cursor and persists the latest provider high water mark`() = runTest {
        val previousHighWaterMark = now.minusSeconds(60)
        val previous = SyncCheckpoint(
            scope,
            actor.id,
            SyncResource.POSTS,
            com.profiletailors.smp.publishing.domain.PageCursor("resume-1"),
            previousHighWaterMark,
            previousHighWaterMark,
        )
        val provider = OverlapRecordingProvider(
            SocialContentPage(
                items = emptyList(),
                nextCursor = null,
                highWaterMark = now,
            ),
        )
        val checkpoints = RecordingCheckpointRepository(previous)
        val handler = SocialContentSyncHandler(
            provider,
            RecordingPostRepository(),
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(checkpointRepository = checkpoints),
            overlap = Duration.ofMinutes(10),
            backoff = { _, _ -> },
        )

        handler.importPosts(actor, now)

        provider.requestedCursor shouldBe com.profiletailors.smp.publishing.domain.PageCursor("resume-1")
        provider.requestedSince shouldBe previousHighWaterMark.minus(Duration.ofMinutes(10))
        checkpoints.saved.single() shouldBe previous.copy(
            cursor = null,
            highWaterMark = now,
            lastSuccessfulAt = now,
        )
    }

    @Test
    fun `sync requests an overlap window and deduplicates overlapping provider pages`() = runTest {
        val previous = SyncCheckpoint(
            scope,
            actor.id,
            SyncResource.POSTS,
            null,
            now.minusSeconds(60),
            now.minusSeconds(60),
        )
        val provider = OverlapRecordingProvider(
            page(
                id = "post-1",
                next = null,
            ).copy(
                items = listOf(
                    page("post-1", null).items.single(),
                    page("post-1", null).items.single().copy(lastModifiedAt = now),
                ),
                highWaterMark = now,
            ),
        )
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository(previous)
        val handler = SocialContentSyncHandler(
            provider,
            posts,
            checkpoints,
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = RecordingBatchWriter(posts, checkpoints),
            overlap = Duration.ofMinutes(10),
            backoff = { _, _ -> },
        )

        val result = handler.importPosts(actor, now)

        provider.requestedSince shouldBe now.minusSeconds(60).minus(Duration.ofMinutes(10))
        result.items shouldHaveSize 1
        posts.upserted shouldHaveSize 1
        posts.upserted.single().lastModifiedAt shouldBe now
        checkpoints.saved.single().highWaterMark shouldBe now
    }

    @Test
    fun `sync sends one newest version when the same post overlaps two provider pages`() = runTest {
        val older = page("post-1", null).items.single().copy(
            body = "older",
            lastModifiedAt = now.minusSeconds(30),
        )
        val newer = older.copy(body = "newer", lastModifiedAt = now)
        val provider = PagedOverlapProvider(
            SocialContentPage(listOf(older), com.profiletailors.smp.publishing.domain.PageCursor("page-2")),
            SocialContentPage(listOf(newer), null, now),
        )
        val writer = RecordingBatchWriter()
        val handler = SocialContentSyncHandler(
            provider,
            RecordingPostRepository(),
            RecordingCheckpointRepository(),
            com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true),
            ),
            retention,
            SocialContentFeatureGates(importEnabled = true),
            batchWriter = writer,
            backoff = { _, _ -> },
        )

        val result = handler.importPosts(actor, now)

        provider.requestedCursors shouldBe listOf(null, com.profiletailors.smp.publishing.domain.PageCursor("page-2"))
        result.items shouldHaveSize 1
        result.items.single().body shouldBe "newer"
        writer.posts shouldHaveSize 1
        writer.posts.single().externalPostId shouldBe ExternalPostId("post-1")
        writer.posts.single().lastModifiedAt shouldBe now
        writer.posts.single().body shouldBe "newer"
    }

    private fun page(id: String, next: String?): SocialContentPage<SocialPost> {
        val post = SocialPost.imported(actor.scope, actor, ExternalPostId(id), now, now)
        return SocialContentPage(
            listOf(post),
            next?.let { com.profiletailors.smp.publishing.domain.PageCursor(it) },
            now,
        )
    }

    private class ScriptedProvider(private vararg val steps: Any) : SocialContentProvider {
        private var index = 0
        val calls: Int get() = index
        override suspend fun discoverActors(scope: WorkspaceScope, connectionId: String) =
            emptyList<com.profiletailors.smp.publishing.domain.SocialContentActorCandidate>()
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = when (val step = steps[index++]) {
            is Throwable -> {
                throw step
            }
            else -> {
                val page = step as? SocialContentPage<*>
                    ?: error("Expected a SocialContentPage or Throwable, got ${step::class.simpleName}")
                SocialContentPage(
                    items = page.items.map { item ->
                        item as? SocialPost
                            ?: error("Expected a SocialPost, got ${item?.let { it::class.simpleName } ?: "null"}")
                    },
                    nextCursor = page.nextCursor,
                    highWaterMark = page.highWaterMark,
                )
            }
        }
        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ) = SocialContentPage<com.profiletailors.smp.publishing.domain.SocialComment>(emptyList(), null)
        override suspend fun reply(
            actor: SocialContentActor,
            parent: com.profiletailors.smp.publishing.domain.SocialComment,
            body: String,
            idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
        ) = parent
    }

    private class RepeatingPageProvider(private val response: SocialContentPage<SocialPost>) : SocialContentProvider {
        var calls = 0

        override suspend fun discoverActors(scope: WorkspaceScope, connectionId: String) =
            emptyList<com.profiletailors.smp.publishing.domain.SocialContentActorCandidate>()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> {
            calls++
            return response
        }

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ) = SocialContentPage<com.profiletailors.smp.publishing.domain.SocialComment>(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: com.profiletailors.smp.publishing.domain.SocialComment,
            body: String,
            idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
        ) = parent
    }

    private class PagedOverlapProvider(
        private val firstPage: SocialContentPage<SocialPost>,
        private val secondPage: SocialContentPage<SocialPost>,
    ) : SocialContentProvider {
        val requestedCursors = mutableListOf<com.profiletailors.smp.publishing.domain.PageCursor?>()

        override suspend fun discoverActors(scope: WorkspaceScope, connectionId: String) =
            emptyList<com.profiletailors.smp.publishing.domain.SocialContentActorCandidate>()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> {
            requestedCursors += cursor
            return if (cursor == null) firstPage else secondPage
        }

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ) = SocialContentPage<com.profiletailors.smp.publishing.domain.SocialComment>(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: com.profiletailors.smp.publishing.domain.SocialComment,
            body: String,
            idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
        ) = parent
    }

    private class OverlapRecordingProvider(private val response: SocialContentPage<SocialPost>) :
        SocialContentProvider {
        var requestedCursor: com.profiletailors.smp.publishing.domain.PageCursor? = null
        var requestedSince: Instant? = null

        override suspend fun discoverActors(scope: WorkspaceScope, connectionId: String) =
            emptyList<com.profiletailors.smp.publishing.domain.SocialContentActorCandidate>()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = response

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            modifiedSince: Instant?,
        ): SocialContentPage<SocialPost> {
            requestedCursor = cursor
            requestedSince = modifiedSince
            return response
        }

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
            pageSize: Int,
        ) = SocialContentPage<com.profiletailors.smp.publishing.domain.SocialComment>(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: com.profiletailors.smp.publishing.domain.SocialComment,
            body: String,
            idempotencyKey: com.profiletailors.smp.publishing.domain.IdempotencyKey,
        ) = parent
    }

    private class RecordingPostRepository(initial: List<SocialPost> = emptyList()) : SocialContentPostRepository {
        val upserted = initial.toMutableList()
        val tombstones = mutableListOf<Set<ExternalPostId>>()
        override suspend fun upsert(post: SocialPost): SocialPost {
            upserted.removeIf { existingPost ->
                existingPost.scope == post.scope &&
                    existingPost.provider == post.provider &&
                    existingPost.actorId == post.actorId &&
                    existingPost.externalPostId == post.externalPostId
            }
            upserted += post
            return post
        }

        override suspend fun findByWorkspaceAndExternalId(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            externalPostId: ExternalPostId,
        ) = upserted.firstOrNull { it.externalPostId == externalPostId }

        override suspend fun tombstoneMissing(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            seenExternalIds: Set<ExternalPostId>,
        ) {
            tombstones += seenExternalIds
        }
    }

    private class RecordingBatchWriter(
        private val postRepository: RecordingPostRepository? = null,
        private val checkpointRepository: RecordingCheckpointRepository? = null,
    ) : SocialContentBatchWriter {
        var posts: List<SocialPost> = emptyList()
        var tombstoneIds: Set<ExternalPostId> = emptySet()
        lateinit var checkpoint: SyncCheckpoint

        override suspend fun persist(
            posts: Collection<SocialPost>,
            tombstoneIds: Set<ExternalPostId>,
            checkpoint: SyncCheckpoint,
        ) {
            this.posts = posts.toList()
            this.tombstoneIds = tombstoneIds
            this.checkpoint = checkpoint
            posts.forEach { postRepository?.upsert(it) }
            if (tombstoneIds.isNotEmpty()) {
                postRepository?.tombstoneMissing(
                    scope = checkpoint.scope,
                    provider = checkpoint.provider,
                    actorId = checkpoint.actorId,
                    seenExternalIds = tombstoneIds,
                )
            }
            checkpointRepository?.save(checkpoint)
        }
    }

    private class RecordingCheckpointRepository(var current: SyncCheckpoint? = null) :
        SocialContentCheckpointRepository {
        val saved = mutableListOf<SyncCheckpoint>()
        override suspend fun find(
            scope: WorkspaceScope,
            actorId: String,
            resource: SyncResource,
            postId: ExternalPostId?,
        ) = current
        override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
            current = checkpoint
            saved += checkpoint
            return checkpoint
        }
    }
}
