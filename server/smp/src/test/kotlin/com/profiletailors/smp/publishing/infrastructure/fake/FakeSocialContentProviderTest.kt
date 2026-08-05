package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.ThreadState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class FakeSocialContentProviderTest {
    @Test
    fun `should return deterministic paginated posts and record calls when fetching posts`() = runTest {
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
    fun `should expose configured failures before succeeding on a subsequent fetch`() = runTest {
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
    fun `should map ROLE_FORBIDDEN failure to the corresponding typed provider failure`() = runTest {
        val actor = fixtureActor()
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(
                posts = mapOf(actor.id to listOf(fixturePost(actor, "post-1"))),
                failures = ArrayDeque(listOf(FakeProviderFailure.ROLE_FORBIDDEN)),
            ),
        )

        shouldThrow<SocialContentProviderException> { provider.fetchPosts(actor, null) }
            .failure shouldBe SocialContentProviderFailure.ROLE_FORBIDDEN
    }

    @Test
    fun `should return an empty page when there are no posts for the requested actor`() = runTest {
        val actor = fixtureActor()
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures(posts = emptyMap()))

        val page = provider.fetchPosts(actor, null)

        page.items.shouldBeEmpty()
        page.nextCursor shouldBe null
        provider.calls shouldBe listOf(FakeProviderCall.FetchPosts(actor.id, null))
    }

    @Test
    fun `should return an empty page when there are no comments for the requested post`() = runTest {
        val actor = fixtureActor()
        val post = fixturePost(actor, "post-1")
        val provider = FakeSocialContentProvider(FakeSocialContentFixtures(comments = emptyMap()))

        val page = provider.fetchComments(actor, post)

        page.items.shouldBeEmpty()
        page.nextCursor shouldBe null
        provider.calls shouldBe listOf(FakeProviderCall.FetchComments(actor.id, post.externalPostId.value))
    }

    @Test
    fun `should return the configured actor candidates when discovering actors`() = runTest {
        val actor = fixtureActor()
        val provider = FakeSocialContentProvider(
            FakeSocialContentFixtures(actorCandidates = listOf(administeredOrganizationCandidate(actor))),
        )

        val candidates = provider.discoverActors(actor.scope, "connection-1")

        candidates shouldHaveSize 1
        candidates.single().id shouldBe actor.id
        provider.calls shouldBe listOf(FakeProviderCall.DiscoverActors(actor.scope.value, "connection-1"))
    }

    @Test
    fun `should preserve nested comment parent identity and record reply calls`() = runTest {
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

    @Test
    fun `should isolate fake post repository by workspace provider and actor id`() = runTest {
        val actorA = fixtureActor()
        val actorB = fixtureActor(id = "actor-2", externalId = "urn:li:organization:456")
        val postA = fixturePost(actorA, "post-A")
        val postB = fixturePost(actorB, "post-B")

        val repository: SocialContentPostRepository = FakeSocialContentPostRepository()
        repository.upsert(postA)
        repository.upsert(postB)

        repository.findByWorkspaceAndExternalId(actorA.scope, SocialProvider.LINKEDIN, actorA.id, postA.externalPostId) shouldBe postA
        repository.findByWorkspaceAndExternalId(actorA.scope, SocialProvider.LINKEDIN, actorA.id, postB.externalPostId) shouldBe null
        repository.findByWorkspaceAndExternalId(actorB.scope, SocialProvider.LINKEDIN, actorB.id, postB.externalPostId) shouldBe postB
    }

    @Test
    fun `should tombstone only matching identity absent from the seen set while leaving others untouched`() = runTest {
        val actorA = fixtureActor()
        val actorB = fixtureActor(id = "actor-2", externalId = "urn:li:organization:456")
        val retained = fixturePost(actorA, "post-retained")
        val stale = fixturePost(actorA, "post-stale")

        val repository: SocialContentPostRepository = FakeSocialContentPostRepository()
        repository.upsert(retained)
        repository.upsert(stale)
        repository.upsert(fixturePost(actorB, "post-other-actor"))

        repository.tombstoneMissing(actorA.scope, SocialProvider.LINKEDIN, actorA.id, seenExternalIds = setOf(retained.externalPostId))

        repository.findByWorkspaceAndExternalId(actorA.scope, SocialProvider.LINKEDIN, actorA.id, retained.externalPostId)?.lifecycle shouldBe PostLifecycle.PUBLISHED
        repository.findByWorkspaceAndExternalId(actorA.scope, SocialProvider.LINKEDIN, actorA.id, stale.externalPostId)?.lifecycle shouldBe PostLifecycle.TOMBSTONED
        repository.findByWorkspaceAndExternalId(actorB.scope, SocialProvider.LINKEDIN, actorB.id, ExternalPostId("post-other-actor"))?.lifecycle shouldBe PostLifecycle.PUBLISHED
    }

    private fun fixtureActor(
        id: String = "actor-1",
        externalId: String = "urn:li:organization:123",
    ) = SocialContentActor(
        id = id,
        scope = WorkspaceScope("workspace-1"),
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId(externalId),
        kind = com.profiletailors.smp.publishing.domain.SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social", "r_organization_social_social_actions", "w_organization_social"),
    )

    private fun administeredOrganizationCandidate(actor: SocialContentActor) = SocialContentActorCandidate(
        id = actor.id,
        externalActorId = actor.externalActorId,
        kind = com.profiletailors.smp.publishing.domain.SocialAccountKind.ORGANIZATION_PAGE,
        displayName = actor.displayName,
        roleState = com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN,
        grantedScopes = actor.grantedScopes,
    )

    private fun fixturePost(actor: SocialContentActor, id: String) = SocialPost.imported(
        scope = actor.scope,
        actor = actor,
        externalPostId = ExternalPostId(id),
        publishedAt = Instant.parse("2026-08-01T10:00:00Z"),
        now = Instant.parse("2026-08-01T10:00:00Z"),
    ).copy(origin = PostOrigin.EXTERNAL_OR_UNKNOWN)
}
