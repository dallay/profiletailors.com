package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentReader
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class SocialContentCalendarQueryHandlerTest {
    private val workspace = WorkspaceScope("workspace-1")
    private val actor = SocialContentActor(
        id = "page-1",
        scope = workspace,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social"),
    )
    private val publishedAt = Instant.parse("2026-08-02T10:00:00Z")

    @Test
    fun `maps imported posts to provider publication dates and read only calendar items`() = runTest {
        val post = SocialPost.imported(
            scope = workspace,
            actor = actor,
            externalPostId = ExternalPostId("urn:li:share:1"),
            publishedAt = publishedAt,
            now = publishedAt,
        )
        val handler = SocialContentCalendarQueryHandler(RecordingReader(SocialContentPage(listOf(post), null)))

        val response = handler.handle(
            SocialContentCalendarQuery(
                scope = workspace,
                from = publishedAt.minusSeconds(1),
                to = publishedAt.plusSeconds(1),
            ),
        )

        response.items.single() shouldBe SocialContentCalendarItem(
            externalPostId = post.externalPostId,
            scope = workspace,
            provider = SocialProvider.LINKEDIN,
            actorId = actor.id,
            scheduledAt = null,
            publishedAt = publishedAt,
            origin = PostOrigin.EXTERNAL_OR_UNKNOWN,
            lifecycle = PostLifecycle.PUBLISHED,
            mutationAllowed = false,
        )
    }

    @Test
    fun `passes workspace filters and opaque cursor to the reader`() = runTest {
        val reader = RecordingReader(SocialContentPage(emptyList(), PageCursor("opaque.next")))
        val handler = SocialContentCalendarQueryHandler(reader)
        val cursor = PageCursor("opaque.in")
        val from = publishedAt.minusSeconds(60)
        val to = publishedAt.plusSeconds(60)

        val response = handler.handle(
            SocialContentCalendarQuery(
                scope = workspace,
                from = from,
                to = to,
                actorId = actor.id,
                lifecycle = PostLifecycle.PUBLISHED,
                cursor = cursor,
                limit = 25,
            ),
        )

        reader.queries.single() shouldBe SocialContentCalendarQuery(
            scope = workspace,
            from = from,
            to = to,
            actorId = actor.id,
            lifecycle = PostLifecycle.PUBLISHED,
            cursor = cursor,
            limit = 25,
        )
        response.nextCursor shouldBe PageCursor("opaque.next")
    }

    @Test
    fun `rejects a reader result from another workspace`() = runTest {
        val foreignActor = actor.copy(scope = WorkspaceScope("workspace-2"))
        val foreignPost = SocialPost.imported(
            scope = foreignActor.scope,
            actor = foreignActor,
            externalPostId = ExternalPostId("foreign-post"),
            publishedAt = publishedAt,
            now = publishedAt,
        )
        val handler = SocialContentCalendarQueryHandler(
            RecordingReader(SocialContentPage(listOf(foreignPost), null)),
        )

        shouldThrow<SocialContentCalendarIsolationException> {
            handler.handle(
                SocialContentCalendarQuery(
                    scope = workspace,
                    from = publishedAt.minusSeconds(1),
                    to = publishedAt.plusSeconds(1),
                ),
            )
        }
    }

    @Test
    fun `requires a bounded valid date range and page size`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentCalendarQuery(workspace, publishedAt, publishedAt)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentCalendarQuery(workspace, publishedAt.minusSeconds(1), publishedAt, limit = 0)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentCalendarQuery(workspace, publishedAt.minusSeconds(1), publishedAt, limit = 101)
        }
    }

    private class RecordingReader(private val response: SocialContentPage<SocialPost>) : SocialContentReader {
        val queries = mutableListOf<SocialContentCalendarQuery>()

        override suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost> {
            queries += query
            return response
        }
    }
}
