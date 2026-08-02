package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.ThreadState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class FakeSocialContentProviderTest {
    @Test
    fun `fake provider returns deterministic paginated posts and records calls`() = runTest {
        val actor = fixtureActor()
        val first = fixturePost(actor, "post-1")
        val second = fixturePost(actor, "post-2")
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(first, second)),
                pageSize = 1,
            ),
        )

        val page = provider.fetchPosts(actor, null)
        val nextPage = provider.fetchPosts(actor, page.nextCursor)

        page.items shouldBe listOf(first)
        nextPage.items shouldBe listOf(second)
        provider.calls shouldBe listOf(
            FakeProviderCall.FetchPosts(actor.id, null),
            FakeProviderCall.FetchPosts(actor.id, "1"),
        )
    }

    @Test
    fun `fake provider exposes configured failures before succeeding`() = runTest {
        val actor = fixtureActor()
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(fixturePost(actor, "post-1"))),
                failures = ArrayDeque(listOf(FakeProviderFailure.RATE_LIMITED)),
            ),
        )

        shouldThrow<SocialContentProviderException> { provider.fetchPosts(actor, null) }
            .failure shouldBe SocialContentProviderFailure.RATE_LIMITED
        provider.fetchPosts(actor, null).items shouldHaveSize 1
    }

    @Test
    fun `fake provider preserves nested comment parent identity and reply calls`() = runTest {
        val actor = fixtureActor()
        val post = fixturePost(actor, "post-1")
        val parent = SocialComment(
            scope = actor.scope,
            postId = post.externalPostId,
            ownerActorId = actor.id,
            externalCommentId = ExternalCommentId("comment-1"),
            parentExternalCommentId = null,
            actorExternalId = ProviderActorId("urn:li:person:1"),
            body = "Question",
            createdAt = post.publishedAt,
            state = ThreadState.OPEN,
            expiresAt = post.expiresAt,
        )
        val reply = parent.copy(
            externalCommentId = ExternalCommentId("comment-2"),
            parentExternalCommentId = parent.externalCommentId,
        )
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(comments = mapOf(post.externalPostId.value to listOf(parent, reply))),
        )

        provider.fetchComments(actor, post).items.map { it.parentExternalCommentId } shouldBe
            listOf(null, parent.externalCommentId)
        provider.reply(actor, parent, "Thanks", IdempotencyKey("reply-1"))
            .externalCommentId shouldBe ExternalCommentId("fake-reply-reply-1")
        provider.replyCalls shouldHaveSize 1
    }

    private fun fixtureActor() = SocialContentActor(
        id = "actor-1",
        scope = WorkspaceScope("workspace-1"),
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = com.profiletailors.smp.publishing.domain.SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social", "r_organization_social_social_actions", "w_organization_social"),
    )

    private fun fixturePost(actor: SocialContentActor, id: String) = SocialPost.imported(
        scope = actor.scope,
        actor = actor,
        externalPostId = ExternalPostId(id),
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        now = Instant.parse("2026-08-01T10:00:00Z"),
    ).copy(origin = PostOrigin.EXTERNAL_OR_UNKNOWN)
}
