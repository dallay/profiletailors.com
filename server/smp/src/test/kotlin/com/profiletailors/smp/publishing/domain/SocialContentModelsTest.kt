package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class SocialContentModelsTest {
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
        grantedScopes = setOf("r_organization_social"),
    )
    private val publishedAt = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun `workspace scope and provider identifiers reject blank values`() {
        shouldThrow<IllegalArgumentException> { WorkspaceScope(" ") }
        shouldThrow<IllegalArgumentException> { ProviderActorId("") }
        shouldThrow<IllegalArgumentException> { ExternalPostId(" ") }
    }

    @Test
    fun `social content actors reject personal profiles`() {
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
    fun `local publication match changes provenance without changing provider identity`() {
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
    }

    @Test
    fun `tombstone is retained but inactive`() {
        val post = SocialPost.imported(
            scope = workspace,
            actor = actor,
            externalPostId = ExternalPostId("post-1"),
            publishedAt = publishedAt,
            now = publishedAt,
        )

        val tombstone = post.tombstone(publishedAt.plusSeconds(60))

        tombstone.lifecycle shouldBe PostLifecycle.TOMBSTONED
        tombstone.isActive shouldBe false
        tombstone.mutationAllowed shouldBe false
    }

    @Test
    fun `expired activity is unavailable without becoming a tombstone`() {
        val post = SocialPost.imported(
            scope = workspace,
            actor = actor,
            externalPostId = ExternalPostId("post-1"),
            publishedAt = publishedAt,
            now = publishedAt,
            expiresAt = publishedAt.plusSeconds(60),
        )

        post.isExpired(publishedAt.plusSeconds(61)) shouldBe true
        post.lifecycle shouldBe PostLifecycle.PUBLISHED
    }

    @Test
    fun `reply command requires same workspace and an open parent`() {
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
            expiresAt = publishedAt.plusSeconds(3600),
        )

        ReplyCommand(
            scope = workspace,
            actorId = actor.id,
            parentCommentId = comment.externalCommentId,
            body = "Answer",
            idempotencyKey = IdempotencyKey("reply-1"),
        ).validateAgainst(comment, workspace, publishedAt.plusSeconds(1)) shouldBe Unit

        shouldThrow<ReplyRejectedException> {
            ReplyCommand(
                scope = WorkspaceScope("workspace-2"),
                actorId = actor.id,
                parentCommentId = comment.externalCommentId,
                body = "Answer",
                idempotencyKey = IdempotencyKey("reply-2"),
            ).validateAgainst(comment, workspace, publishedAt.plusSeconds(1))
        }
    }
}
