package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.ReplyRejectedException
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentCommentRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.ThreadState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.publishing.infrastructure.fake.FakeProviderFailure
import com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepository
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentFixtures
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentPostRepository
import com.profiletailors.smp.publishing.infrastructure.fake.FakeSocialContentProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SocialContentFoundationHandlersTest {
    private val now = Instant.parse("2026-08-01T12:00:00Z")
    private val workspace = WorkspaceScope("workspace-1")
    private val otherWorkspace = WorkspaceScope("workspace-2")
    private val retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24))
    private val actor = SocialContentActor(
        id = "actor-1",
        scope = workspace,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf(
            "r_organization_social",
            "r_organization_social_social_actions",
            "w_organization_social",
        ),
    )

    @Test
    fun `should keep only administered organization pages in discovery output`() = runTest {
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                actorCandidates = listOf(
                    administeredOrganizationCandidate(),
                    nonAdminOrganizationCandidate(),
                    personalProfileCandidate(),
                ),
            ),
        )
        val handler = foundationHandler(provider, RecordingPostRepository(), RecordingCheckpointRepository())

        handler.discoverActors(actor) shouldBe listOf(
            SocialContentActor(
                id = "actor-1",
                scope = workspace,
                connectionId = actor.connectionId,
                provider = SocialProvider.LINKEDIN,
                externalActorId = ProviderActorId("urn:li:organization:123"),
                kind = SocialAccountKind.ORGANIZATION_PAGE,
                displayName = "Profile Tailors",
                roleState = ActorRoleState.ADMIN,
                grantedScopes = actor.grantedScopes,
            ),
        )
    }

    @Test
    fun `should reject discovery when the active feature gate is disabled`() = runTest {
        val provider =
            FakeSocialContentProvider(
                FakeSocialContentFixtures(actorCandidates = listOf(administeredOrganizationCandidate())),
            )
        val handler = foundationHandler(
            provider,
            RecordingPostRepository(),
            RecordingCheckpointRepository(),
            gates = SocialContentFeatureGates(discoveryEnabled = false),
        )

        shouldThrow<IllegalStateException> { handler.discoverActors(actor) }
    }

    @Test
    fun `should import posts and advance checkpoint only after successful reconciliation`() = runTest {
        val post = fixturePost("post-1")
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures(posts = mapOf(actor.id to listOf(post))))
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val handler = foundationHandler(provider, posts, checkpoints)

        val result = handler.importPosts(actor, now)

        result.items.single().externalPostId shouldBe post.externalPostId
        posts.upserted.single().externalPostId shouldBe post.externalPostId
        posts.upserted.single().expiresAt shouldBe now.plus(retention.activityTtl)
        posts.tombstoneCalls shouldBe listOf(setOf(post.externalPostId))
        checkpoints.saved.shouldHaveSize(1)
        checkpoints.saved.single().cursor shouldBe null
        checkpoints.saved.single().lastSuccessfulAt shouldBe now
    }

    @Test
    fun `should reject post import when the import feature gate is disabled`() = runTest {
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(
                    actor.id to listOf(fixturePost("post-1")),
                ),
            ),
        )
        val handler = foundationHandler(
            provider,
            RecordingPostRepository(),
            RecordingCheckpointRepository(),
            gates = SocialContentFeatureGates(importEnabled = false),
        )

        shouldThrow<IllegalStateException> { handler.importPosts(actor, now) }
    }

    @Test
    fun `should retry rate limited post reads and advance the checkpoint after the eventual success`() = runTest {
        val post = fixturePost("post-1")
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(post)),
                failures = ArrayDeque(listOf(FakeProviderFailure.RATE_LIMITED)),
            ),
        )
        val checkpoints = RecordingCheckpointRepository()
        val backoffAttempts = mutableListOf<Int>()
        val handler = foundationHandler(
            provider = provider,
            posts = RecordingPostRepository(),
            checkpoints = checkpoints,
            retryPolicy = SocialContentRetryPolicy(backoff = { backoffAttempts += it }),
        )

        handler.importPosts(actor, now)

        provider.calls shouldHaveSize 2
        backoffAttempts shouldBe listOf(1)
        checkpoints.saved.single().lastSuccessfulAt shouldBe now
    }

    @Test
    fun `should not mutate posts or checkpoint when provider fails`() = runTest {
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(failures = ArrayDeque(listOf(FakeProviderFailure.UNAUTHORIZED))),
        )
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val handler = foundationHandler(provider, posts, checkpoints)

        shouldThrow<SocialContentProviderException> { handler.importPosts(actor, now) }
            .failure shouldBe SocialContentProviderFailure.UNAUTHORIZED
        posts.upserted shouldBe emptyList()
        posts.tombstoneCalls shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
    }

    @Test
    fun `should rethrow non rate limited provider failures without retrying`() = runTest {
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(fixturePost("post-1"))),
                failures = ArrayDeque(listOf(FakeProviderFailure.UNAUTHORIZED)),
            ),
        )
        val backoffCalls = mutableListOf<Int>()
        val handler = foundationHandler(
            provider = provider,
            posts = RecordingPostRepository(),
            checkpoints = RecordingCheckpointRepository(),
            retryPolicy = SocialContentRetryPolicy(backoff = { backoffCalls += it }),
        )

        shouldThrow<SocialContentProviderException> { handler.importPosts(actor, now) }
            .failure shouldBe SocialContentProviderFailure.UNAUTHORIZED

        provider.calls shouldHaveSize 1
        backoffCalls shouldBe emptyList()
    }

    @Test
    fun `should rethrow rate limited provider failures once maxAttempts is exhausted`() = runTest {
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(fixturePost("post-1"))),
                failures = ArrayDeque(listOf(FakeProviderFailure.RATE_LIMITED, FakeProviderFailure.RATE_LIMITED)),
            ),
        )
        val backoffCalls = mutableListOf<Int>()
        val handler = foundationHandler(
            provider = provider,
            posts = RecordingPostRepository(),
            checkpoints = RecordingCheckpointRepository(),
            retryPolicy = SocialContentRetryPolicy(maxAttempts = 2, backoff = { backoffCalls += it }),
        )

        shouldThrow<SocialContentProviderException> { handler.importPosts(actor, now) }
            .failure shouldBe SocialContentProviderFailure.RATE_LIMITED

        provider.calls shouldHaveSize 2
        backoffCalls shouldBe listOf(1)
    }

    @Test
    fun `should reject retry policy configured with fewer than one attempt`() {
        shouldThrow<IllegalArgumentException> { SocialContentRetryPolicy(maxAttempts = 0) }
    }

    @Test
    fun `should reconcile every page before tombstoning missing posts`() = runTest {
        val first = fixturePost("post-1")
        val second = fixturePost("post-2")
        val missing = fixturePost("post-missing")
        val provider = PagedSocialContentProvider(listOf(first, second))
        val posts = RecordingPostRepository(initial = listOf(missing))
        val checkpoints = RecordingCheckpointRepository()
        val handler = foundationHandler(provider, posts, checkpoints)

        handler.importPosts(actor, now)

        posts.upserted.map { it.externalPostId } shouldBe listOf(first.externalPostId, second.externalPostId)
        posts.tombstoneCalls shouldBe listOf(setOf(first.externalPostId, second.externalPostId))
        posts.tombstoned.map { it.externalPostId } shouldBe listOf(missing.externalPostId)
        checkpoints.saved.single().cursor shouldBe null
    }

    @Test
    fun `should deduplicate repeated provider posts by workspace actor and external identity`() = runTest {
        val post = fixturePost("post-1")
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures(posts = mapOf(actor.id to listOf(post))))
        val posts = FakeSocialContentPostRepository()
        val handler = foundationHandler(provider, posts, RecordingCheckpointRepository())

        handler.importPosts(actor, now)
        handler.importPosts(actor, now)

        posts.all() shouldHaveSize 1
        posts.all().single().externalPostId shouldBe post.externalPostId
    }

    @Test
    fun `should preserve provider parent identity when importing comments`() = runTest {
        val post = fixturePost("post-1")
        val comment = fixtureComment(post, "comment-1")
        val reply = fixtureComment(post, "comment-2", comment.externalCommentId)
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(comments = mapOf(post.externalPostId.value to listOf(comment, reply))),
        )
        val comments = RecordingCommentRepository()
        val handler = foundationHandler(provider, RecordingPostRepository(), RecordingCheckpointRepository(), comments)

        handler.importComments(actor, post, now)

        comments.upserted.map { it.parentExternalCommentId } shouldBe listOf(null, comment.externalCommentId)
    }

    @Test
    fun `should record a FAILED reply result and rethrow when the provider fails`() = runTest {
        val provider = ThrowingSocialContentProvider(IllegalStateException("upstream down"))
        val commandRepository = RecordingReplyCommandRepository()
        val handler = IdempotentReplyHandler(
            provider = provider,
            commandRepository = commandRepository,
            capabilityResolver = allRepliesAllowed(),
            retention = retention,
        )
        val parent = fixtureComment(fixturePost("post-1"), "comment-1")

        shouldThrow<IllegalStateException> { handler.handle(actor, parent, "Answer", IdempotencyKey("reply-1"), now) }

        val failed = commandRepository.saved.last()
        failed.state shouldBe ReplyCommandState.FAILED
        failed.command.idempotencyKey shouldBe IdempotencyKey("reply-1")
    }

    @Test
    fun `should throw ReplyRejectedException with WORKSPACE_MISMATCH for foreign reply parent`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val parent = fixtureComment(fixturePost("post-1"), "comment-1").copy(scope = otherWorkspace)
        val handler = replyHandler(provider)

        shouldThrow<ReplyRejectedException> {
            handler.handle(actor, parent, "Answer", IdempotencyKey("reply-1"), now)
        }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.WORKSPACE_MISMATCH

        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should throw ReplyRejectedException with THREAD_NOT_OPEN when the reply parent is closed`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val handler = replyHandler(provider)
        val closed = fixtureComment(fixturePost("post-1"), "comment-1").copy(state = ThreadState.CLOSED)

        shouldThrow<ReplyRejectedException> {
            handler.handle(actor, closed, "Answer", IdempotencyKey("reply-closed"), now)
        }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.THREAD_NOT_OPEN

        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should throw ReplyRejectedException with THREAD_NOT_OPEN when the reply parent is deleted`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val handler = replyHandler(provider)
        val deleted = fixtureComment(fixturePost("post-2"), "comment-2").copy(state = ThreadState.DELETED)

        shouldThrow<ReplyRejectedException> {
            handler.handle(actor, deleted, "Answer", IdempotencyKey("reply-deleted"), now)
        }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.THREAD_NOT_OPEN

        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should throw ReplyRejectedException with EXPIRED when the reply parent has expired`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val handler = replyHandler(provider)
        val expired = fixtureComment(fixturePost("post-3"), "comment-3").copy(expiresAt = now)

        shouldThrow<ReplyRejectedException> {
            handler.handle(actor, expired, "Answer", IdempotencyKey("reply-expired"), now)
        }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.EXPIRED

        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should throw ReplyRejectedException with ACTOR_MISMATCH when the actor does not own the reply parent`() =
        runTest {
            val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
            val handler = replyHandler(provider)
            val parent = fixtureComment(fixturePost("post-1"), "comment-1").copy(ownerActorId = "actor-2")

            shouldThrow<ReplyRejectedException> {
                handler.handle(actor, parent, "Answer", IdempotencyKey("reply-owner"), now)
            }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.ACTOR_MISMATCH

            provider.replyCalls shouldHaveSize 0
        }

    @Test
    fun `should throw ReplyRejectedException with CAPABILITY_DENIED when the active feature gate forbids replies`() =
        runTest {
            val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
            val handler = IdempotentReplyHandler(
                provider = provider,
                commandRepository = FakeReplyCommandRepository(),
                capabilityResolver = DefaultCapabilityResolver(SocialContentFeatureGates()),
                retention = retention,
            )
            val parent = fixtureComment(fixturePost("post-1"), "comment-1")

            shouldThrow<ReplyRejectedException> {
                handler.handle(actor, parent, "Answer", IdempotencyKey("reply-capability"), now)
            }.reason shouldBe com.profiletailors.smp.publishing.domain.ReplyRejectionReason.CAPABILITY_DENIED

            provider.replyCalls shouldHaveSize 0
        }

    @Test
    fun `should return existing reply result without a second provider call`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val handler = replyHandler(provider)
        val parent = fixtureComment(fixturePost("post-1"), "comment-1")
        val key = IdempotencyKey("reply-1")

        val first = handler.handle(actor, parent, "Answer", key, now)
        val second = handler.handle(actor, parent, "Answer", key, now)

        first.state shouldBe ReplyCommandState.SUCCEEDED
        second shouldBe first
        provider.replyCalls shouldHaveSize 1
    }

    private fun foundationHandler(
        provider: SocialContentProvider,
        posts: SocialContentPostRepository,
        checkpoints: RecordingCheckpointRepository,
        comments: RecordingCommentRepository = RecordingCommentRepository(),
        retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
        gates: SocialContentFeatureGates = SocialContentFeatureGates(
            discoveryEnabled = true,
            importEnabled = true,
            inboxEnabled = true,
            repliesEnabled = true,
        ),
    ) = SocialContentFoundationHandlers(
        provider = provider,
        postRepository = posts,
        commentRepository = comments,
        checkpointRepository = checkpoints,
        capabilityResolver = allCapabilitiesEnabled(gates),
        retention = retention,
        retryPolicy = retryPolicy,
    )

    private fun replyHandler(provider: SocialContentProvider) = IdempotentReplyHandler(
        provider = provider,
        commandRepository = FakeReplyCommandRepository(),
        capabilityResolver = allRepliesAllowed(),
        retention = retention,
    )

    private fun allCapabilitiesEnabled(gates: SocialContentFeatureGates) = DefaultCapabilityResolver(gates)

    private fun allRepliesAllowed() = DefaultCapabilityResolver(SocialContentFeatureGates(repliesEnabled = true))

    private fun administeredOrganizationCandidate() = SocialContentActorCandidate(
        id = actor.id,
        externalActorId = actor.externalActorId,
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = actor.displayName,
        roleState = ActorRoleState.ADMIN,
        grantedScopes = actor.grantedScopes,
    )

    private fun nonAdminOrganizationCandidate() = SocialContentActorCandidate(
        id = "actor-2",
        externalActorId = ProviderActorId("urn:li:organization:456"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Non-admin Page",
        roleState = ActorRoleState.MEMBER,
        grantedScopes = actor.grantedScopes,
    )

    private fun personalProfileCandidate() = SocialContentActorCandidate(
        id = "personal-1",
        externalActorId = ProviderActorId("urn:li:person:789"),
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Personal profile",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = actor.grantedScopes,
    )

    private fun fixturePost(id: String) = SocialPost.imported(
        scope = workspace,
        actor = actor,
        externalPostId = ExternalPostId(id),
        publishedAt = now.minusSeconds(60),
        now = now,
    )

    private fun fixtureComment(post: SocialPost, id: String, parentId: ExternalCommentId? = null) = SocialComment(
        scope = workspace,
        postId = post.externalPostId,
        externalCommentId = ExternalCommentId(id),
        parentExternalCommentId = parentId,
        ownerActorId = actor.id,
        actorExternalId = ProviderActorId("urn:li:person:1"),
        body = "Question",
        createdAt = post.publishedAt,
        state = ThreadState.OPEN,
        expiresAt = now.plusSeconds(3600),
    )

    private class RecordingPostRepository(initial: List<SocialPost> = emptyList()) : SocialContentPostRepository {
        val upserted = mutableListOf<SocialPost>()
        val tombstoned = initial.toMutableList()
        val tombstoneCalls = mutableListOf<Set<ExternalPostId>>()

        override suspend fun upsert(post: SocialPost): SocialPost {
            upserted += post
            return post
        }

        override suspend fun findByWorkspaceAndExternalId(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            externalPostId: ExternalPostId,
        ): SocialPost? = upserted.firstOrNull {
            it.scope == scope && it.provider == provider && it.actorId == actorId && it.externalPostId == externalPostId
        }

        override suspend fun tombstoneMissing(
            scope: WorkspaceScope,
            provider: SocialProvider,
            actorId: String,
            seenExternalIds: Set<ExternalPostId>,
        ) {
            tombstoneCalls += seenExternalIds
            tombstoned.replaceAll { post ->
                if (
                    post.scope == scope &&
                    post.provider == provider &&
                    post.actorId == actorId &&
                    post.externalPostId !in seenExternalIds
                ) {
                    post.tombstone(Instant.parse("2026-08-01T12:00:00Z"))
                } else {
                    post
                }
            }
        }
    }

    private class PagedSocialContentProvider(private val posts: List<SocialPost>) : SocialContentProvider {
        override suspend fun discoverActors(
            scope: WorkspaceScope,
            connectionId: String,
        ): List<SocialContentActorCandidate> = emptyList()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
        ): SocialContentPage<SocialPost> = when (cursor?.value) {
            null -> SocialContentPage(
                items = listOf(posts[0]),
                nextCursor = PageCursor("page-2"),
            )

            "page-2" -> SocialContentPage(
                items = listOf(posts[1]),
                nextCursor = null,
            )

            else -> error("Unexpected cursor: ${cursor.value}")
        }

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
        ): SocialContentPage<SocialComment> = SocialContentPage(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: SocialComment,
            body: String,
            idempotencyKey: IdempotencyKey,
        ): SocialComment = parent
    }

    private class ThrowingSocialContentProvider(private val throwable: Throwable) : SocialContentProvider {
        override suspend fun discoverActors(
            scope: WorkspaceScope,
            connectionId: String,
        ): List<SocialContentActorCandidate> = emptyList()

        override suspend fun fetchPosts(actor: SocialContentActor, cursor: PageCursor?): SocialContentPage<SocialPost> =
            throw throwable

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
        ): SocialContentPage<SocialComment> = throw throwable

        override suspend fun reply(
            actor: SocialContentActor,
            parent: SocialComment,
            body: String,
            idempotencyKey: IdempotencyKey,
        ): SocialComment = throw throwable
    }

    private class RecordingCommentRepository : SocialContentCommentRepository {
        val upserted = mutableListOf<SocialComment>()

        override suspend fun findByWorkspaceAndExternalId(
            scope: WorkspaceScope,
            externalCommentId: ExternalCommentId,
        ): SocialComment? = upserted.firstOrNull { it.scope == scope && it.externalCommentId == externalCommentId }

        override suspend fun upsert(comment: SocialComment): SocialComment {
            upserted += comment
            return comment
        }
    }

    private class RecordingCheckpointRepository : SocialContentCheckpointRepository {
        var checkpoint: SyncCheckpoint? = null
        val saved = mutableListOf<SyncCheckpoint>()

        override suspend fun find(scope: WorkspaceScope, actorId: String, resource: SyncResource): SyncCheckpoint? =
            checkpoint

        override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
            this.checkpoint = checkpoint
            saved += checkpoint
            return checkpoint
        }
    }

    private class RecordingReplyCommandRepository(
        private val delegate: com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepository =
            com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepository(),
    ) : com.profiletailors.smp.publishing.domain.ReplyCommandRepository {
        val saved = mutableListOf<com.profiletailors.smp.publishing.domain.ReplyCommandResult>()

        override suspend fun claim(command: com.profiletailors.smp.publishing.domain.ReplyCommand) =
            delegate.claim(command)

        override suspend fun save(
            result: com.profiletailors.smp.publishing.domain.ReplyCommandResult,
        ): com.profiletailors.smp.publishing.domain.ReplyCommandResult {
            saved += result
            return delegate.save(result)
        }
    }
}
