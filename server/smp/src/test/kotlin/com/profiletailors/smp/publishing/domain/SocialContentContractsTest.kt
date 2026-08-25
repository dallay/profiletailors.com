package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SocialContentContractsTest {
    private val workspace = WorkspaceScope("workspace-1")
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
    private val retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24))

    private fun fixturePost(actor: SocialContentActor, externalId: String) = SocialPost(
        scope = actor.scope,
        provider = actor.provider,
        actorId = actor.id,
        externalPostId = ExternalPostId(externalId),
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        expiresAt = Instant.parse("2026-08-03T10:00:00Z"),
    )

    private fun fixtureComment(actor: SocialContentActor, post: SocialPost) = SocialComment(
        scope = actor.scope,
        postId = post.externalPostId,
        ownerActorId = actor.id,
        externalCommentId = ExternalCommentId("comment-1"),
        parentExternalCommentId = null,
        actorExternalId = actor.externalActorId,
        body = "Question",
        createdAt = post.publishedAt,
        state = ThreadState.OPEN,
        expiresAt = post.expiresAt,
    )

    /** Stub that implements the abstract provider methods so the incremental overload is exercised. */
    private fun defaultProvider() = object : SocialContentProvider {
        override suspend fun discoverActors(
            scope: WorkspaceScope,
            connectionId: String,
            socialAccountId: String,
        ): List<SocialContentActorCandidate> = emptyList()

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
        ): SocialContentPage<SocialComment> = SocialContentPage(emptyList(), null)

        override suspend fun reply(
            actor: SocialContentActor,
            parent: SocialComment,
            body: String,
            idempotencyKey: IdempotencyKey,
        ): SocialComment = parent
    }

    @Test
    fun `capability policy reports missing scope before allowing replies`() {
        val withoutReplyScope = actor.copy(grantedScopes = setOf("r_organization_social"))
        val policy = DefaultCapabilityResolver(
            SocialContentFeatureGates(importEnabled = true, inboxEnabled = true, repliesEnabled = true),
        )

        policy.resolve(withoutReplyScope, CapabilityOperation.REPLY, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.MISSING_SCOPE)
        policy.resolve(actor, CapabilityOperation.REPLY, retention) shouldBe CapabilityDecision.Allowed
    }

    @Test
    fun `capability policy keeps every operation disabled when feature gates are off`() {
        val policy = DefaultCapabilityResolver(SocialContentFeatureGates())

        policy.resolve(actor, CapabilityOperation.READ_POSTS, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.UNSUPPORTED)
    }

    @Test
    fun `capability policy distinguishes reauthentication and role failures`() {
        val policy = DefaultCapabilityResolver(
            SocialContentFeatureGates(discoveryEnabled = true, importEnabled = true, repliesEnabled = true),
        )

        policy.resolve(
            actor.copy(roleState = ActorRoleState.REVOKED),
            CapabilityOperation.READ_POSTS,
            retention,
        ) shouldBe CapabilityDecision.Denied(CapabilityFailure.REAUTH_REQUIRED)
        policy.resolve(
            actor.copy(roleState = ActorRoleState.MEMBER),
            CapabilityOperation.REPLY,
            retention,
        ) shouldBe CapabilityDecision.Denied(CapabilityFailure.ROLE_REQUIRED)
    }

    @Test
    fun `pagination limits reject invalid values`() {
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 0, maxPages = 1) }
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 101, maxPages = 1) }
        shouldThrow<IllegalArgumentException> { SocialContentSyncLimits(pageSize = 1, maxPages = 0) }
    }

    @Test
    fun `checkpoint advance preserves the source and records success time`() {
        val cursor = PageCursor("next-1")
        val checkpoint = SyncCheckpoint(
            scope = workspace,
            actorId = actor.id,
            resource = SyncResource.POSTS,
            cursor = cursor,
            lastSuccessfulAt = null,
        )
        val successfulAt = Instant.parse("2026-08-01T12:00:00Z")

        val advanced = checkpoint.advance(PageCursor("next-2"), successfulAt)

        advanced.cursor shouldBe PageCursor("next-2")
        advanced.lastSuccessfulAt shouldBe successfulAt
        advanced.scope shouldBe workspace
        checkpoint.cursor shouldBe cursor
        checkpoint.lastSuccessfulAt shouldBe null
    }

    @Test
    fun `provider modified since overload delegates to the primary page method`() = runTest {
        val provider = defaultProvider()
        val post = fixturePost(actor, "post-1")
        val comment = fixtureComment(actor, post)
        val now = Instant.parse("2026-08-01T11:00:00Z")

        provider.discoverActors(workspace, "connection-1").shouldBeEmpty()
        provider.fetchPosts(actor, null, pageSize = 10).items.shouldBeEmpty()
        provider.fetchPosts(actor, null, modifiedSince = now).items.shouldBeEmpty()
        provider.fetchComments(actor, post, null, pageSize = 10).items.shouldBeEmpty()
        provider.reply(actor, comment, "Thanks", IdempotencyKey("reply-1")) shouldBe comment
    }

    @Test
    fun `provider three-argument discover default delegates to the two-argument implementation`() = runTest {
        val provider = object : SocialContentProvider {
            override suspend fun discoverActors(
                scope: WorkspaceScope,
                connectionId: String,
            ): List<SocialContentActorCandidate> = emptyList()

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
            ): SocialContentPage<SocialComment> = SocialContentPage(emptyList(), null)

            override suspend fun reply(
                actor: SocialContentActor,
                parent: SocialComment,
                body: String,
                idempotencyKey: IdempotencyKey,
            ): SocialComment = parent
        }

        provider.discoverActors(workspace, "connection-1", "account-1").shouldBeEmpty()
    }

    @Test
    fun `default capability resolver wrapper delegates to the domain resolver`() {
        val policy = DefaultSocialContentCapabilityResolver(
            DefaultCapabilityResolver(
                SocialContentFeatureGates(importEnabled = true, inboxEnabled = true, repliesEnabled = true),
            ),
        )

        policy.resolve(actor, CapabilityOperation.READ_POSTS, retention) shouldBe CapabilityDecision.Allowed
        policy.resolve(actor, CapabilityOperation.REPLY, retention) shouldBe CapabilityDecision.Allowed
    }

    @Test
    fun `reader default findPost returns null when no override is provided`() = runTest {
        val reader = object : SocialContentReader {
            override suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost> =
                SocialContentPage(emptyList(), null)
        }

        reader.findPost(workspace, ExternalPostId("post-1")) shouldBe null
    }
}
