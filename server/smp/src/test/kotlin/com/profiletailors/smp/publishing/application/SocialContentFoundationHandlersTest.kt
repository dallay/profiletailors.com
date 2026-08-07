package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.CapabilityDecision
import com.profiletailors.smp.publishing.domain.CapabilityFailure
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.ReplyRejectedException
import com.profiletailors.smp.publishing.domain.ReplyRejectionReason
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentCommentRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialContentSyncLimits
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
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

@Suppress("LargeClass")
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

        shouldThrow<SocialContentCapabilityDeniedException> { handler.discoverActors(actor) }
            .failure shouldBe CapabilityFailure.UNSUPPORTED
        provider.calls shouldBe emptyList()
    }

    @Test
    fun `should expose dedicated import handlers with explicit commands`() = runTest {
        val post = fixturePost("post-command")
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(post)),
                comments = mapOf(post.externalPostId.value to emptyList()),
            ),
        )
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val resolver = allCapabilitiesEnabled(
            SocialContentFeatureGates(
                discoveryEnabled = true,
                importEnabled = true,
                inboxEnabled = true,
                repliesEnabled = true,
            ),
        )

        ImportSocialPostsHandler(
            provider,
            posts,
            checkpoints,
            resolver,
            retention,
            SocialContentSyncLimits(pageSize = 1, maxPages = 1),
        ).handle(SyncSocialPostsCommand(actor, now))
        ImportSocialCommentsHandler(
            provider,
            RecordingCommentRepository(),
            checkpoints,
            resolver,
            retention,
            SocialContentSyncLimits(1, 1),
        ).handle(SyncSocialCommentsCommand(actor, post, now))

        posts.upserted.single().externalPostId shouldBe post.externalPostId
    }

    @Test
    fun `should expose reply handling through an explicit command boundary`() = runTest {
        val post = fixturePost("post-command-reply")
        val parent = fixtureComment(post, "comment-command-reply")
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val handler = ReplyToSocialCommentCommandHandler(
            provider,
            FakeReplyCommandRepository(),
            allRepliesAllowed(),
            retention,
        )
        val command = ReplyToSocialCommentCommand(
            actor,
            parent,
            ReplyCommand(workspace, actor.id, parent.externalCommentId, "Answer", IdempotencyKey("reply-command")),
            now,
        )

        handler.handle(command).state shouldBe ReplyCommandState.SUCCEEDED
        provider.replyCalls shouldHaveSize 1
    }

    @Test
    fun `should accept the capability resolver port without requiring its default implementation`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val resolver = object : SocialContentCapabilityResolver {
            override fun resolve(
                actor: SocialContentActor,
                operation: CapabilityOperation,
                retention: RetentionRequirements,
            ): CapabilityDecision = CapabilityDecision.Denied(CapabilityFailure.MISSING_SCOPE)
        }
        val handler = DiscoverSocialContentActorsHandler(provider, resolver, retention)

        shouldThrow<SocialContentCapabilityDeniedException> {
            handler.handle(DiscoverSocialContentActorsQuery(actor))
        }.failure shouldBe CapabilityFailure.MISSING_SCOPE
        provider.calls shouldBe emptyList()
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

        shouldThrow<SocialContentCapabilityDeniedException> { handler.importPosts(actor, now) }
            .failure shouldBe CapabilityFailure.UNSUPPORTED
        provider.calls shouldBe emptyList()
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
    fun `should delay rate limited retries with the default exponential backoff`() = runTest {
        var attempts = 0
        val startedAt = currentTime

        SocialContentRetryPolicy(maxAttempts = 3).execute {
            attempts += 1
            if (attempts < 3) {
                throw SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED)
            }
            Unit
        }

        attempts shouldBe 3
        currentTime - startedAt shouldBe 300L
    }

    @Test
    fun `should fail repeated comment cursors without writing or replacing the checkpoint`() = runTest {
        val firstPost = fixturePost("post-comment-repeat")
        val comment = fixtureComment(firstPost, "comment-repeat")
        val checkpoints = RecordingCheckpointRepository(
            SyncCheckpoint(
                workspace,
                actor.id,
                SyncResource.COMMENTS,
                PageCursor("old"),
                null,
                now.minusSeconds(1),
                firstPost.externalPostId,
            ),
        )
        val comments = RecordingCommentRepository()
        val handler = foundationHandler(
            provider = RepeatingCommentCursorProvider(comment),
            posts = RecordingPostRepository(),
            checkpoints = checkpoints,
            comments = comments,
            limits = SocialContentSyncLimits(pageSize = 1, maxPages = 3),
        )

        shouldThrow<SocialContentPaginationException> { handler.importComments(actor, firstPost, now) }
            .reason shouldBe PaginationGuardReason.REPEATED_CURSOR

        comments.upserted shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
        checkpoints.checkpoint?.cursor shouldBe PageCursor("old")
    }

    @Test
    fun `should fail max comment pages without writing or replacing the checkpoint`() = runTest {
        val post = fixturePost("post-comment-max")
        val comment = fixtureComment(post, "comment-max")
        val checkpoints = RecordingCheckpointRepository()
        val comments = RecordingCommentRepository()
        val handler = foundationHandler(
            provider = EndlessCommentCursorProvider(comment),
            posts = RecordingPostRepository(),
            checkpoints = checkpoints,
            comments = comments,
            limits = SocialContentSyncLimits(pageSize = 1, maxPages = 2),
        )

        shouldThrow<SocialContentPaginationException> { handler.importComments(actor, post, now) }
            .reason shouldBe PaginationGuardReason.MAX_PAGES_EXCEEDED

        comments.upserted shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
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
    fun `should fail repeated cursors without writing or replacing the checkpoint`() = runTest {
        val post = fixturePost("post-1")
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository(
            SyncCheckpoint(
                workspace,
                actor.id,
                SyncResource.POSTS,
                PageCursor("old"),
                now.minusSeconds(1),
                now.minusSeconds(10),
            ),
        )
        val provider = RepeatingCursorProvider(post)
        val handler = foundationHandler(provider, posts, checkpoints, limits = SocialContentSyncLimits(1, 3))

        shouldThrow<SocialContentPaginationException> { handler.importPosts(actor, now) }
            .reason shouldBe PaginationGuardReason.REPEATED_CURSOR
        posts.upserted shouldBe emptyList()
        posts.tombstoneCalls shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
        checkpoints.checkpoint?.cursor shouldBe PageCursor("old")
    }

    @Test
    fun `should fail max pages without writing or replacing the checkpoint`() = runTest {
        val post = fixturePost("post-1")
        val posts = RecordingPostRepository()
        val checkpoints = RecordingCheckpointRepository()
        val provider = EndlessCursorProvider(post)
        val handler = foundationHandler(provider, posts, checkpoints, limits = SocialContentSyncLimits(1, 2))

        shouldThrow<SocialContentPaginationException> { handler.importPosts(actor, now) }
            .reason shouldBe PaginationGuardReason.MAX_PAGES_EXCEEDED
        posts.upserted shouldBe emptyList()
        posts.tombstoneCalls shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
    }

    @Test
    fun `should resume from checkpoint and preserve a newer high water mark`() = runTest {
        val post = fixturePost("post-1")
        val checkpoint = SyncCheckpoint(
            workspace,
            actor.id,
            SyncResource.POSTS,
            PageCursor("resume"),
            now.plusSeconds(60),
            now.minusSeconds(60),
        )
        val checkpoints = RecordingCheckpointRepository(checkpoint)
        val provider = ResumeProvider(post)
        val handler = foundationHandler(provider, RecordingPostRepository(), checkpoints)

        handler.importPosts(actor, now)

        provider.requestedCursors shouldBe listOf(PageCursor("resume"))
        checkpoints.saved.single().highWaterMark shouldBe checkpoint.highWaterMark
        checkpoints.saved.single().lastSuccessfulAt shouldBe now
    }

    @Test
    fun `should include newer post timestamps when provider high water mark is older`() = runTest {
        val post = fixturePost("post-newer")
        val checkpoints = RecordingCheckpointRepository(
            SyncCheckpoint(
                workspace,
                actor.id,
                SyncResource.POSTS,
                null,
                now.minusSeconds(300),
                now.minusSeconds(300),
            ),
        )
        val provider = HighWaterMarkProvider(post, now.minusSeconds(120))
        val handler = foundationHandler(provider, RecordingPostRepository(), checkpoints)

        handler.importPosts(actor, now)

        checkpoints.saved.single().highWaterMark shouldBe now.minusSeconds(60)
    }

    @Test
    fun `should include newer comment timestamps when provider high water mark is older`() = runTest {
        val post = fixturePost("post-comments")
        val comment = fixtureComment(post, "comment-newer").copy(createdAt = now.minusSeconds(60))
        val checkpoints = RecordingCheckpointRepository(
            SyncCheckpoint(
                workspace,
                actor.id,
                SyncResource.COMMENTS,
                null,
                now.minusSeconds(300),
                now.minusSeconds(300),
                post.externalPostId,
            ),
        )
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(comments = mapOf(post.externalPostId.value to listOf(comment))),
        )
        val handler = foundationHandler(provider, RecordingPostRepository(), checkpoints)

        handler.importComments(actor, post, now)

        checkpoints.saved.single().highWaterMark shouldBe now.minusSeconds(60)
        checkpoints.saved.single().postId shouldBe post.externalPostId
    }

    @Test
    fun `should isolate comment checkpoints by post`() = runTest {
        val firstPost = fixturePost("post-comments-1")
        val secondPost = fixturePost("post-comments-2")
        val firstComment = fixtureComment(firstPost, "comment-1")
        val secondComment = fixtureComment(secondPost, "comment-2")
        val checkpoints = RecordingCheckpointRepository()
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                comments = mapOf(
                    firstPost.externalPostId.value to listOf(firstComment),
                    secondPost.externalPostId.value to listOf(secondComment),
                ),
            ),
        )
        val handler = foundationHandler(provider, RecordingPostRepository(), checkpoints)

        handler.importComments(actor, firstPost, now)
        handler.importComments(actor, secondPost, now)

        checkpoints.saved.map { it.postId } shouldBe listOf(firstPost.externalPostId, secondPost.externalPostId)
        checkpoints.find(workspace, actor.id, SyncResource.COMMENTS, firstPost.externalPostId)?.postId shouldBe
            firstPost.externalPostId
        checkpoints.find(workspace, actor.id, SyncResource.COMMENTS, secondPost.externalPostId)?.postId shouldBe
            secondPost.externalPostId
    }

    @Test
    fun `should leave comment state and checkpoint unchanged when the provider fails`() = runTest {
        val provider = ThrowingSocialContentProvider(
            SocialContentProviderException(SocialContentProviderFailure.UNAUTHORIZED),
        )
        val comments = RecordingCommentRepository()
        val checkpoints = RecordingCheckpointRepository(
            SyncCheckpoint(
                workspace,
                actor.id,
                SyncResource.COMMENTS,
                PageCursor("old"),
                null,
                now.minusSeconds(1),
                ExternalPostId("post-1"),
            ),
        )
        val handler = foundationHandler(provider, RecordingPostRepository(), checkpoints, comments)

        shouldThrow<SocialContentProviderException> { handler.importComments(actor, fixturePost("post-1"), now) }
        comments.upserted shouldBe emptyList()
        checkpoints.saved shouldBe emptyList()
        checkpoints.checkpoint?.cursor shouldBe PageCursor("old")
    }

    @Test
    fun `should return every persisted reply state without calling the provider`() = runTest {
        listOf(ReplyCommandState.PROCESSING, ReplyCommandState.SUCCEEDED, ReplyCommandState.FAILED).forEach { state ->
            val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
            val repository = FakeReplyCommandRepository()
            val parent = fixtureComment(fixturePost("post-$state"), "comment-$state")
            val key = IdempotencyKey("reply-$state")
            val command = com.profiletailors.smp.publishing.domain.ReplyCommand(
                workspace,
                actor.id,
                parent.externalCommentId,
                "Answer",
                key,
            )
            val existing = ReplyCommandResult(
                command = command,
                state = state,
                externalCommentId = if (state == ReplyCommandState.SUCCEEDED) ExternalCommentId("reply-id") else null,
            )
            repository.claim(command)
            repository.save(existing)
            val handler = replyHandler(provider, repository)

            handler.handle(actor, parent, "Answer", key, now) shouldBe existing
            provider.replyCalls shouldHaveSize 0
        }
    }

    @Test
    fun `should reject a reply idempotency key reused by a different command`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val repository = FakeReplyCommandRepository()
        val parent = fixtureComment(fixturePost("post-conflict"), "comment-conflict")
        val key = IdempotencyKey("reply-conflict")
        val handler = replyHandler(provider, repository)

        handler.handle(actor, parent, "Answer", key, now)

        shouldThrow<ReplyIdempotencyConflictException> {
            handler.handle(actor, parent, "Different answer", key, now)
        }
        provider.replyCalls shouldHaveSize 1
    }

    @Test
    fun `should persist a typed provider failure for a failed reply`() = runTest {
        val provider = ThrowingSocialContentProvider(
            SocialContentProviderException(SocialContentProviderFailure.ROLE_FORBIDDEN),
        )
        val repository = RecordingReplyCommandRepository()
        val handler = IdempotentReplyHandler(
            provider = provider,
            commandRepository = repository,
            capabilityResolver = allRepliesAllowed(),
            retention = retention,
        )
        val parent = fixtureComment(fixturePost("post-failed"), "comment-failed")

        shouldThrow<SocialContentProviderException> {
            handler.handle(actor, parent, "Answer", IdempotencyKey("reply-failed"), now)
        }

        repository.saved.last().state shouldBe ReplyCommandState.FAILED
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
    fun `imports all comment pages using the configured provider page size`() = runTest {
        val post = fixturePost("post-1")
        val first = fixtureComment(post, "comment-1")
        val second = fixtureComment(post, "comment-2", first.externalCommentId)
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                comments = mapOf(post.externalPostId.value to listOf(first, second)),
                pageSize = 1,
            ),
        )
        val comments = RecordingCommentRepository()
        val handler = foundationHandler(provider, RecordingPostRepository(), RecordingCheckpointRepository(), comments)

        handler.importComments(actor, post, now)

        comments.upserted.map { it.externalCommentId } shouldBe
            listOf(first.externalCommentId, second.externalCommentId)
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

    @Test
    fun `should reject a reply command whose actor differs from the executing actor`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val repository = FakeReplyCommandRepository()
        val handler = ReplyToSocialCommentCommandHandler(provider, repository, allRepliesAllowed(), retention)
        val parent = fixtureComment(fixturePost("post-executor"), "comment-executor").copy(ownerActorId = "actor-2")
        val command = ReplyCommand(
            workspace,
            "actor-2",
            parent.externalCommentId,
            "Answer",
            IdempotencyKey("reply-executor"),
        )

        shouldThrow<ReplyRejectedException> { handler.handle(actor, parent, command, now) }
            .reason shouldBe ReplyRejectionReason.EXECUTOR_MISMATCH

        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should return a stored SUCCEEDED reply before validating an expired parent`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val repository = FakeReplyCommandRepository()
        val parent = fixtureComment(fixturePost("post-replay-succeeded"), "comment-replay-succeeded")
        val command = ReplyCommand(
            workspace,
            actor.id,
            parent.externalCommentId,
            "Answer",
            IdempotencyKey("replay-succeeded"),
        )
        repository.claim(command)
        val existing = ReplyCommandResult(
            command,
            ReplyCommandState.SUCCEEDED,
            externalCommentId = ExternalCommentId("reply-id"),
        )
        repository.save(existing)
        val handler = ReplyToSocialCommentCommandHandler(provider, repository, allRepliesAllowed(), retention)

        handler.handle(actor, parent.copy(expiresAt = now), command, now) shouldBe existing
        provider.replyCalls shouldHaveSize 0
    }

    @Test
    fun `should return stored FAILED and PROCESSING replies before validating an expired parent`() = runTest {
        listOf(ReplyCommandState.FAILED, ReplyCommandState.PROCESSING).forEach { state ->
            val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
            val repository = FakeReplyCommandRepository()
            val parent = fixtureComment(fixturePost("post-replay-$state"), "comment-replay-$state")
            val command = ReplyCommand(
                workspace,
                actor.id,
                parent.externalCommentId,
                "Answer",
                IdempotencyKey("replay-$state"),
            )
            repository.claim(command)
            val existing = ReplyCommandResult(command, state)
            repository.save(existing)
            val handler = ReplyToSocialCommentCommandHandler(provider, repository, allRepliesAllowed(), retention)

            handler.handle(actor, parent.copy(expiresAt = now), command, now) shouldBe existing
            provider.replyCalls shouldHaveSize 0
        }
    }

    @Test
    fun `should not persist a PROCESSING record for an invalid reply command`() = runTest {
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures())
        val repository = FakeReplyCommandRepository()
        val parent = fixtureComment(fixturePost("post-invalid"), "comment-invalid").copy(expiresAt = now)
        val command = ReplyCommand(
            workspace,
            actor.id,
            parent.externalCommentId,
            "Answer",
            IdempotencyKey("reply-invalid"),
        )
        val handler = ReplyToSocialCommentCommandHandler(provider, repository, allRepliesAllowed(), retention)

        shouldThrow<ReplyRejectedException> { handler.handle(actor, parent, command, now) }
            .reason shouldBe ReplyRejectionReason.EXPIRED

        repository.find(command) shouldBe null
        provider.replyCalls shouldHaveSize 0
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `should cap exponential backoff delays to a positive maximum`() = runTest {
        var attempts = 0
        val observedDelays = mutableListOf<Long>()
        var lastObservation = currentTime

        val exception = shouldThrow<SocialContentProviderException> {
            SocialContentRetryPolicy(maxAttempts = 65).execute {
                attempts += 1
                observedDelays += currentTime - lastObservation
                lastObservation = currentTime
                throw SocialContentProviderException(SocialContentProviderFailure.RATE_LIMITED)
            }
        }

        exception.failure shouldBe SocialContentProviderFailure.RATE_LIMITED
        attempts shouldBe 65
        observedDelays shouldHaveSize 65
        val retryDelays = observedDelays.drop(1)
        retryDelays.all { it > 0 } shouldBe true
        retryDelays.max() shouldBe 60_000L
    }

    private fun foundationHandler(
        provider: SocialContentProvider,
        posts: SocialContentPostRepository,
        checkpoints: RecordingCheckpointRepository,
        comments: RecordingCommentRepository = RecordingCommentRepository(),
        retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
        limits: SocialContentSyncLimits = SocialContentSyncLimits(100, 10),
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
        syncLimits = limits,
    )

    private fun replyHandler(
        provider: SocialContentProvider,
        commandRepository: ReplyCommandRepository = FakeReplyCommandRepository(),
    ) = IdempotentReplyHandler(
        provider = provider,
        commandRepository = commandRepository,
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
            upserted.removeIf { existing ->
                existing.scope == post.scope &&
                    existing.provider == post.provider &&
                    existing.actorId == post.actorId &&
                    existing.externalPostId == post.externalPostId
            }
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

    private abstract class BaseFakeSocialContentProvider : SocialContentProvider {
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(emptyList(), null)

        override suspend fun discoverActors(
            scope: WorkspaceScope,
            connectionId: String,
        ): List<SocialContentActorCandidate> = emptyList()

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialComment> = SocialContentPage(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: SocialComment,
            body: String,
            idempotencyKey: IdempotencyKey,
        ): SocialComment = parent
    }

    private class RepeatingCursorProvider(private val post: SocialPost) : BaseFakeSocialContentProvider() {
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(listOf(post), PageCursor("repeat"))
    }

    private class RepeatingCommentCursorProvider(private val comment: SocialComment) : BaseFakeSocialContentProvider() {
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(emptyList(), null)

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialComment> = SocialContentPage(listOf(comment), PageCursor("repeat"))
    }

    private class EndlessCommentCursorProvider(private val comment: SocialComment) : BaseFakeSocialContentProvider() {
        private var callCount = 0

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(emptyList(), null)

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialComment> = SocialContentPage(
            listOf(comment),
            PageCursor("${++callCount}"),
        )
    }

    private class EndlessCursorProvider(private val post: SocialPost) : BaseFakeSocialContentProvider() {
        private var callCount = 0

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(listOf(post), PageCursor("${++callCount}"))
    }

    private class HighWaterMarkProvider(private val post: SocialPost, private val highWaterMark: Instant) :
        BaseFakeSocialContentProvider() {
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = SocialContentPage(listOf(post), null, highWaterMark)
    }

    private class ResumeProvider(private val post: SocialPost) : BaseFakeSocialContentProvider() {
        val requestedCursors = mutableListOf<PageCursor?>()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> {
            requestedCursors += cursor
            return SocialContentPage(listOf(post), null, post.publishedAt)
        }
    }

    private class PagedSocialContentProvider(private val posts: List<SocialPost>) : BaseFakeSocialContentProvider() {
        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
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
    }

    private class ThrowingSocialContentProvider(private val throwable: Throwable) : BaseFakeSocialContentProvider() {
        override suspend fun discoverActors(
            scope: WorkspaceScope,
            connectionId: String,
        ): List<SocialContentActorCandidate> = emptyList()

        override suspend fun fetchPosts(
            actor: SocialContentActor,
            cursor: PageCursor?,
            pageSize: Int,
        ): SocialContentPage<SocialPost> = throw throwable

        override suspend fun fetchComments(
            actor: SocialContentActor,
            post: SocialPost,
            cursor: PageCursor?,
            pageSize: Int,
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

    private class RecordingCheckpointRepository(initial: SyncCheckpoint? = null) : SocialContentCheckpointRepository {
        private val checkpoints = mutableMapOf<CheckpointIdentity, SyncCheckpoint>()
        var checkpoint: SyncCheckpoint? = initial
        val saved = mutableListOf<SyncCheckpoint>()

        init {
            initial?.let { checkpoints[it.identity()] = it }
        }

        override suspend fun find(
            scope: WorkspaceScope,
            actorId: String,
            resource: SyncResource,
            postId: ExternalPostId?,
        ): SyncCheckpoint? = checkpoints[CheckpointIdentity(scope, actorId, resource, postId)]

        override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
            this.checkpoint = checkpoint
            checkpoints[checkpoint.identity()] = checkpoint
            saved += checkpoint
            return checkpoint
        }

        private fun SyncCheckpoint.identity() = CheckpointIdentity(scope, actorId, resource, postId)

        private data class CheckpointIdentity(
            val scope: WorkspaceScope,
            val actorId: String,
            val resource: SyncResource,
            val postId: ExternalPostId?,
        )
    }

    private class RecordingReplyCommandRepository(
        private val delegate: com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepository =
            com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepository(),
    ) : com.profiletailors.smp.publishing.domain.ReplyCommandRepository {
        val saved = mutableListOf<com.profiletailors.smp.publishing.domain.ReplyCommandResult>()

        override suspend fun find(command: com.profiletailors.smp.publishing.domain.ReplyCommand) =
            delegate.find(command)

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
