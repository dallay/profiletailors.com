package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SocialContentModelsTest {
    private val workspace = WorkspaceScope("workspace-1")
    private val otherWorkspace = WorkspaceScope("workspace-2")
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
            "rw_organization_admin",
        ),
    )
    private val publishedAt = Instant.parse("2026-08-01T10:00:00Z")
    private val retention = RetentionRequirements(Duration.ofHours(48), Duration.ofHours(24))

    @Test
    fun `should reject blank identifier values for every domain value class`() {
        shouldThrow<IllegalArgumentException> { WorkspaceScope(" ") }
        shouldThrow<IllegalArgumentException> { ProviderActorId("") }
        shouldThrow<IllegalArgumentException> { ExternalPostId(" ") }
        shouldThrow<IllegalArgumentException> { ExternalCommentId("") }
        shouldThrow<IllegalArgumentException> { IdempotencyKey("") }
        shouldThrow<IllegalArgumentException> { PageCursor("") }
    }

    @Test
    fun `should reject social content actors when their account kind is not an organization page`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentActor(
                id = "personal-actor",
                scope = workspace,
                connectionId = "connection-1",
                provider = SocialProvider.LINKEDIN,
                externalActorId = ProviderActorId("urn:li:person:123"),
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Personal profile",
                roleState = ActorRoleState.ADMIN,
                grantedScopes = setOf("r_organization_social"),
            )
        }
    }

    @Test
    fun `should reject social content actors when any identity field is blank`() {
        shouldThrow<IllegalArgumentException> {
            actor.copy(id = "")
        }
        shouldThrow<IllegalArgumentException> {
            actor.copy(connectionId = "")
        }
        shouldThrow<IllegalArgumentException> {
            actor.copy(displayName = "  ")
        }
    }

    @Test
    fun `should derive admin capabilities from granted scopes`() {
        val capabilities = actor.capabilities(retention)

        capabilities.accountKind shouldBe SocialAccountKind.ORGANIZATION_PAGE
        capabilities.canReadPosts shouldBe true
        capabilities.canReadComments shouldBe true
        capabilities.canReplyAsActor shouldBe true
        capabilities.canReceiveCommentWebhooks shouldBe true
        capabilities.canImportCompanyPage() shouldBe true
    }

    @Test
    fun `should report missing company page import capability when posts are unreadable or account kind differs`() {
        actor.capabilities(retention).copy(canReadPosts = false).canImportCompanyPage() shouldBe false

        val readOnly = actor.capabilities(retention).copy(accountKind = SocialAccountKind.PERSONAL_PROFILE)
        readOnly.canImportCompanyPage() shouldBe false
    }

    @Test
    fun `should reject retention requirements when any TTL is zero or negative`() {
        shouldThrow<IllegalArgumentException> {
            RetentionRequirements(Duration.ZERO, Duration.ofHours(1))
        }
        shouldThrow<IllegalArgumentException> {
            RetentionRequirements(Duration.ofHours(-1), Duration.ofHours(1))
        }
        shouldThrow<IllegalArgumentException> {
            RetentionRequirements(Duration.ofHours(1), Duration.ZERO)
        }
    }

    @Test
    fun `should reject social content actor candidates when identity or display name is blank`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentActorCandidate(
                id = "",
                externalActorId = ProviderActorId("urn:li:organization:1"),
                kind = SocialAccountKind.ORGANIZATION_PAGE,
                displayName = "Profile Tailors",
                roleState = ActorRoleState.ADMIN,
                grantedScopes = setOf("r_organization_social"),
            )
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentActorCandidate(
                id = "candidate-1",
                externalActorId = ProviderActorId("urn:li:organization:1"),
                kind = SocialAccountKind.ORGANIZATION_PAGE,
                displayName = "  ",
                roleState = ActorRoleState.ADMIN,
                grantedScopes = setOf("r_organization_social"),
            )
        }
    }

    @Test
    fun `should advance sync checkpoint while preserving workspace and advancing timestamp`() {
        val cursor = PageCursor("next-1")
        val checkpoint = SyncCheckpoint(
            scope = workspace,
            actorId = actor.id,
            resource = SyncResource.POSTS,
            cursor = cursor,
            lastSuccessfulAt = null,
        )

        val advanced = checkpoint.advance(PageCursor("next-2"), publishedAt, nextHighWaterMark = publishedAt)

        advanced.cursor shouldBe PageCursor("next-2")
        advanced.lastSuccessfulAt shouldBe publishedAt
        advanced.highWaterMark shouldBe publishedAt
        advanced.scope shouldBe workspace
    }

    @Test
    fun `should reject sync checkpoint when actor ID is blank`() {
        shouldThrow<IllegalArgumentException> {
            SyncCheckpoint(
                scope = workspace,
                actorId = "",
                resource = SyncResource.POSTS,
                cursor = null,
                lastSuccessfulAt = null,
            )
        }
    }

    @Test
    fun `should reject webhook events when any identity or cache key is blank`() {
        shouldThrow<IllegalArgumentException> {
            WebhookEvent(
                scope = workspace,
                providerEventId = "",
                actorId = "actor-1",
                receivedAt = publishedAt,
                payloadCacheKey = "cache-key-1",
            )
        }
        shouldThrow<IllegalArgumentException> {
            WebhookEvent(
                scope = workspace,
                providerEventId = "evt-1",
                actorId = "",
                receivedAt = publishedAt,
                payloadCacheKey = "cache-key-1",
            )
        }
        shouldThrow<IllegalArgumentException> {
            WebhookEvent(
                scope = workspace,
                providerEventId = "evt-1",
                actorId = "actor-1",
                receivedAt = publishedAt,
                payloadCacheKey = "  ",
            )
        }
    }

    @Test
    fun `should reject payload cache when key is blank or encrypted payload is empty`() {
        val expiresAt = publishedAt.plusSeconds(60)
        shouldThrow<IllegalArgumentException> {
            PayloadCache(
                scope = workspace,
                key = "",
                kind = CacheKind.ACTIVITY,
                encryptedPayload = byteArrayOf(0x01, 0x02),
                expiresAt = expiresAt,
            )
        }
        shouldThrow<IllegalArgumentException> {
            PayloadCache(
                scope = workspace,
                key = "key-1",
                kind = CacheKind.ACTIVITY,
                encryptedPayload = ByteArray(0),
                expiresAt = expiresAt,
            )
        }
    }

    @Test
    fun `should make payload cache available before expiry and unavailable at or after the boundary`() {
        val cache = PayloadCache(
            scope = workspace,
            key = "key-1",
            kind = CacheKind.ACTIVITY,
            encryptedPayload = byteArrayOf(0x01, 0x02),
            expiresAt = publishedAt.plusSeconds(60),
        )

        cache.isAvailable(publishedAt.plusSeconds(59)) shouldBe true
        cache.isAvailable(publishedAt.plusSeconds(60)) shouldBe false
        cache.isAvailable(publishedAt.plusSeconds(120)) shouldBe false
    }

    @Test
    fun `should make social comments available before expiry and expired at or after`() {
        val comment = SocialComment(
            scope = workspace,
            postId = ExternalPostId("post-1"),
            ownerActorId = actor.id,
            externalCommentId = ExternalCommentId("comment-1"),
            parentExternalCommentId = null,
            actorExternalId = ProviderActorId("urn:li:person:1"),
            body = "Question",
            createdAt = publishedAt,
            state = ThreadState.OPEN,
            expiresAt = publishedAt.plusSeconds(60),
        )

        comment.isExpired(publishedAt.plusSeconds(59)) shouldBe false
        comment.isExpired(publishedAt.plusSeconds(60)) shouldBe true
    }

    @Test
    fun `should reject reply commands when actor ID or body is blank`() {
        shouldThrow<IllegalArgumentException> {
            ReplyCommand(
                scope = workspace,
                actorId = "",
                parentCommentId = ExternalCommentId("comment-1"),
                body = "Answer",
                idempotencyKey = IdempotencyKey("reply-1"),
            )
        }
        shouldThrow<IllegalArgumentException> {
            ReplyCommand(
                scope = workspace,
                actorId = actor.id,
                parentCommentId = ExternalCommentId("comment-1"),
                body = "  ",
                idempotencyKey = IdempotencyKey("reply-1"),
            )
        }
    }

    @Test
    fun `should reconcile a local publication and reject blank publication IDs`() {
        val post = SocialPost.imported(
            scope = workspace,
            actor = actor,
            externalPostId = ExternalPostId("post-1"),
            publishedAt = publishedAt,
            now = publishedAt,
        )

        val reconciled = post.reconcileWithLocalPublication("publication-1")

        reconciled.origin shouldBe PostOrigin.PROFILETAILORS
        reconciled.localPublicationId shouldBe "publication-1"
        reconciled.externalPostId shouldBe ExternalPostId("post-1")
        reconciled.mutationAllowed shouldBe false

        shouldThrow<IllegalArgumentException> { post.reconcileWithLocalPublication("") }
        shouldThrow<IllegalArgumentException> { post.reconcileWithLocalPublication("   ") }
    }

    @Test
    fun `should tombstone imported posts and report expired activity without changing published lifecycle`() {
        val post = SocialPost.imported(
            scope = workspace,
            actor = actor,
            externalPostId = ExternalPostId("post-1"),
            publishedAt = publishedAt,
            now = publishedAt,
            expiresAt = publishedAt.plusSeconds(60),
        )

        post.isExpired(publishedAt.plusSeconds(59)) shouldBe false
        post.isExpired(publishedAt.plusSeconds(60)) shouldBe true
        post.lifecycle shouldBe PostLifecycle.PUBLISHED

        val tombstoned = post.tombstone(publishedAt.plusSeconds(60))
        tombstoned.lifecycle shouldBe PostLifecycle.TOMBSTONED
        tombstoned.isActive shouldBe false
        tombstoned.expiresAt shouldBe publishedAt.plusSeconds(60)
    }

    @Test
    fun `should reject reply commands when workspace, actor owner, parent or thread state mismatches`() {
        val matchingActorId = "actor-1"
        val openThreadComment = SocialComment(
            scope = workspace,
            postId = ExternalPostId("post-1"),
            ownerActorId = matchingActorId,
            externalCommentId = ExternalCommentId("comment-1"),
            parentExternalCommentId = null,
            actorExternalId = ProviderActorId("urn:li:person:1"),
            body = "Question",
            createdAt = publishedAt,
            state = ThreadState.OPEN,
            expiresAt = publishedAt.plusSeconds(3600),
        )

        ReplyCommand(
            scope = workspace,
            actorId = matchingActorId,
            parentCommentId = openThreadComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-ok"),
        ).validateAgainst(openThreadComment, workspace, publishedAt.plusSeconds(1)) shouldBe Unit

        val foreignReply = ReplyCommand(
            scope = otherWorkspace,
            actorId = matchingActorId,
            parentCommentId = openThreadComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-workspace"),
        )
        shouldThrow<ReplyRejectedException> {
            foreignReply.validateAgainst(openThreadComment, workspace, publishedAt.plusSeconds(1))
        }
            .reason shouldBe ReplyRejectionReason.WORKSPACE_MISMATCH

        val foreignActorReply = ReplyCommand(
            scope = workspace,
            actorId = "another-actor",
            parentCommentId = openThreadComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-actor"),
        )
        shouldThrow<ReplyRejectedException> {
            foreignActorReply.validateAgainst(openThreadComment, workspace, publishedAt.plusSeconds(1))
        }
            .reason shouldBe ReplyRejectionReason.ACTOR_MISMATCH

        val wrongParentComment = openThreadComment.copy(externalCommentId = ExternalCommentId("comment-2"))
        val parentMismatch = ReplyCommand(
            scope = workspace,
            actorId = matchingActorId,
            parentCommentId = openThreadComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-parent"),
        )
        shouldThrow<ReplyRejectedException> {
            parentMismatch.validateAgainst(wrongParentComment, workspace, publishedAt.plusSeconds(1))
        }
            .reason shouldBe ReplyRejectionReason.PARENT_NOT_FOUND

        val closedComment = openThreadComment.copy(state = ThreadState.CLOSED)
        val threadMismatch = ReplyCommand(
            scope = workspace,
            actorId = matchingActorId,
            parentCommentId = openThreadComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-thread"),
        )
        shouldThrow<ReplyRejectedException> {
            threadMismatch.validateAgainst(closedComment, workspace, publishedAt.plusSeconds(1))
        }
            .reason shouldBe ReplyRejectionReason.THREAD_NOT_OPEN
    }

    @Test
    fun `should reject reply commands when the parent comment has expired`() {
        val matchingActorId = "actor-1"
        val expiredComment = SocialComment(
            scope = workspace,
            postId = ExternalPostId("post-1"),
            ownerActorId = matchingActorId,
            externalCommentId = ExternalCommentId("comment-1"),
            parentExternalCommentId = null,
            actorExternalId = ProviderActorId("urn:li:person:1"),
            body = "Question",
            createdAt = publishedAt,
            state = ThreadState.OPEN,
            expiresAt = publishedAt.plusSeconds(60),
        )

        val command = ReplyCommand(
            scope = workspace,
            actorId = matchingActorId,
            parentCommentId = expiredComment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-expired"),
        )

        shouldThrow<ReplyRejectedException> {
            command.validateAgainst(expiredComment, workspace, publishedAt.plusSeconds(120))
        }.reason shouldBe ReplyRejectionReason.EXPIRED
    }

    @Test
    fun `should allow capability resolver to gate every operation independently`() {
        val allEnabled = DefaultCapabilityResolver(
            SocialContentFeatureGates(
                discoveryEnabled = true,
                importEnabled = true,
                inboxEnabled = true,
                repliesEnabled = true,
            ),
        )

        allEnabled.resolve(actor, CapabilityOperation.DISCOVER_ACTORS, retention) shouldBe CapabilityDecision.Allowed
        allEnabled.resolve(actor, CapabilityOperation.READ_POSTS, retention) shouldBe CapabilityDecision.Allowed
        allEnabled.resolve(actor, CapabilityOperation.READ_COMMENTS, retention) shouldBe CapabilityDecision.Allowed
        allEnabled.resolve(actor, CapabilityOperation.REPLY, retention) shouldBe CapabilityDecision.Allowed

        val revoked = actor.copy(roleState = ActorRoleState.REVOKED)
        allEnabled.resolve(revoked, CapabilityOperation.READ_POSTS, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.REAUTH_REQUIRED)

        val member = actor.copy(roleState = ActorRoleState.MEMBER)
        allEnabled.resolve(member, CapabilityOperation.REPLY, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.ROLE_REQUIRED)

        val disabled = DefaultCapabilityResolver(SocialContentFeatureGates())
        disabled.resolve(actor, CapabilityOperation.READ_POSTS, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.UNSUPPORTED)

        val minimalAdmin = actor.copy(grantedScopes = setOf("w_organization_social"))
        allEnabled.resolve(minimalAdmin, CapabilityOperation.READ_POSTS, retention) shouldBe
            CapabilityDecision.Denied(CapabilityFailure.MISSING_SCOPE)
    }
}
