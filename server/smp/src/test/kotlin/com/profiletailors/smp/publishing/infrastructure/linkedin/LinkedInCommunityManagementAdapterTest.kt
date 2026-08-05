package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.application.SocialContentAccessGate
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentAccessDenial
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import com.profiletailors.smp.publishing.domain.SocialContentAccessRequest
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.ThreadState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpHeaders
import java.net.http.HttpRequest

class LinkedInCommunityManagementAdapterTest {
    private val properties = LinkedInPublishingProperties(
        apiBaseUrl = "https://api.linkedin.test",
        apiVersion = "202601",
    )
    private val actor = SocialContentActor(
        id = "page-1",
        scope = WorkspaceScope("workspace-1"),
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = setOf("r_organization_social", "r_organization_social_social_actions", "w_organization_social"),
    )

    @Test
    fun `disabled discovery fails before resolving a token or sending HTTP`() = runTest {
        val transport = RecordingTransport(LinkedInHttpResponse(200, emptyHeaders(), "{}"))
        var tokenCalls = 0
        val adapter = adapter(
            transport = transport,
            accessGate = object : SocialContentAccessGate {
                override suspend fun authorize(request: SocialContentAccessRequest): Unit =
                    throw SocialContentAccessDeniedException(SocialContentAccessDenial.OPERATION_DISABLED)
            },
            onTokenResolve = { tokenCalls++ },
        )

        val exception = assertThrows<SocialContentAccessDeniedException> {
            kotlinx.coroutines.runBlocking { adapter.discoverActors(actor.scope, actor.connectionId) }
        }

        assertEquals(SocialContentAccessDenial.OPERATION_DISABLED, exception.denial)
        assertEquals(0, tokenCalls)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `mismatched gate fails before token and HTTP for posts comments and replies`() = runTest {
        val transport = RecordingTransport(LinkedInHttpResponse(200, emptyHeaders(), "{}"))
        var tokenCalls = 0
        val adapter = adapter(
            transport = transport,
            accessGate = object : SocialContentAccessGate {
                override suspend fun authorize(request: SocialContentAccessRequest): Unit =
                    throw SocialContentAccessDeniedException(SocialContentAccessDenial.ACCOUNT_MISMATCH)
            },
            onTokenResolve = { tokenCalls++ },
        )
        val post = SocialPost.imported(
            scope = actor.scope,
            actor = actor,
            externalPostId = ExternalPostId("urn:li:share:1"),
            publishedAt = java.time.Instant.parse("2026-08-01T12:00:00Z"),
            now = java.time.Instant.parse("2026-08-01T12:00:00Z"),
        )
        val comment = SocialComment(
            scope = actor.scope,
            postId = post.externalPostId,
            ownerActorId = actor.id,
            externalCommentId = ExternalCommentId("comment-1"),
            parentExternalCommentId = null,
            actorExternalId = ProviderActorId("urn:li:person:7"),
            body = "Question",
            createdAt = post.publishedAt,
            state = ThreadState.OPEN,
            expiresAt = post.expiresAt,
        )

        assertThrows<SocialContentAccessDeniedException> {
            kotlinx.coroutines.runBlocking { adapter.fetchPosts(actor, null) }
        }
        assertThrows<SocialContentAccessDeniedException> {
            kotlinx.coroutines.runBlocking { adapter.fetchComments(actor, post) }
        }
        assertThrows<SocialContentAccessDeniedException> {
            kotlinx.coroutines.runBlocking { adapter.reply(actor, comment, "Answer", IdempotencyKey("reply-1")) }
        }

        assertEquals(0, tokenCalls)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `discovers administered organization pages through versioned rest endpoint`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(
                200,
                emptyHeaders(),
                """
                    {"elements":[{"organization":"urn:li:organization:123","organization~":{"localizedName":"Profile Tailors"},"role":"ADMINISTRATOR"}],"paging":{"start":0,"count":100,"total":1}}
                """.trimIndent(),
            ),
        )
        val adapter = adapter(transport)

        val candidates = adapter.discoverActors(actor.scope, actor.connectionId)

        assertEquals("urn:li:organization:123", candidates.single().externalActorId.value)
        assertEquals(SocialAccountKind.ORGANIZATION_PAGE, candidates.single().kind)
        assertEquals(ActorRoleState.ADMIN, candidates.single().roleState)
        val request = transport.requests.single()
        assertEquals(
            "https://api.linkedin.test/rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR",
            request.uri().toString(),
        )
        assertEquals("Bearer access-token", request.headers().firstValue("Authorization").orElse(null))
        assertEquals("202601", request.headers().firstValue("LinkedIn-Version").orElse(null))
        assertEquals("2.0.0", request.headers().firstValue("X-Restli-Protocol-Version").orElse(null))
    }

    @Test
    fun `discovery excludes ACL entries that are not administrator roles`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(
                200,
                emptyHeaders(),
                """{"elements":[
                    {"organization":"urn:li:organization:123","organization~":{"localizedName":"Profile Tailors"},"role":"ADMINISTRATOR"},
                    {"organization":"urn:li:organization:456","organization~":{"localizedName":"Member Page"},"role":"VIEWER"}
                ],"paging":{"start":0,"count":100,"total":1}}""",
            ),
        )

        val candidates = adapter(transport).discoverActors(actor.scope, actor.connectionId)

        assertEquals(listOf("urn:li:organization:123"), candidates.map { it.externalActorId.value })
    }

    @Test
    fun `fetches posts with organization urn and opaque pagination`() = runTest {
        val transport = RecordingTransport(
            LinkedInHttpResponse(
                200,
                emptyHeaders(),
                """
                    {"elements":[{"id":"urn:li:share:1","author":"urn:li:organization:123","created":{"time":1754049600000},"lastModified":{"time":1754049660000},"commentary":"Hello"}],"paging":{"start":0,"count":1,"total":2}}
                """.trimIndent(),
            ),
        )
        val adapter = adapter(transport)

        val page = adapter.fetchPosts(actor, null)

        assertEquals(ExternalPostId("urn:li:share:1"), page.items.single().externalPostId)
        assertEquals("Hello", page.items.single().body)
        assertTrue(page.nextCursor != null)
        val request = transport.requests.single()
        assertTrue(
            request.uri().toString().startsWith(
                "https://api.linkedin.test/rest/posts?author=urn%3Ali%3Aorganization%3A123&q=author",
            ),
        )
        assertTrue(
            request.uri().query.contains("count=100"),
        )
    }

    @Test
    fun `maps unauthorized forbidden rate limit and server failures to typed provider errors`() = runTest {
        listOf(
            401 to SocialContentProviderFailure.UNAUTHORIZED,
            403 to SocialContentProviderFailure.ROLE_FORBIDDEN,
            429 to SocialContentProviderFailure.RATE_LIMITED,
            503 to SocialContentProviderFailure.PROVIDER_UNAVAILABLE,
        ).forEach { (status, expected) ->
            val adapter = adapter(RecordingTransport(LinkedInHttpResponse(status, emptyHeaders(), "error")))

            val error = assertThrows<SocialContentProviderException> {
                adapter.fetchPosts(actor, null)
            }

            assertEquals(expected, error.failure)
            assertEquals(status, error.statusCode)
        }
    }

    @Test
    fun `reads social actions and posts a page reply with page urn`() = runTest {
        val post = SocialPost.imported(
            scope = actor.scope,
            actor = actor,
            externalPostId = ExternalPostId("urn:li:share:1"),
            publishedAt = java.time.Instant.parse("2026-08-01T12:00:00Z"),
            now = java.time.Instant.parse("2026-08-01T12:00:00Z"),
        )
        val transport = RecordingTransport(
            LinkedInHttpResponse(
                200,
                emptyHeaders(),
                """
                    {"elements":[{"id":"comment-1","actor":"urn:li:person:7","message":{"text":"Question"},"created":{"time":1754049600000},"parentComment":""}],"paging":{"start":0,"count":100,"total":1}}
                """.trimIndent(),
            ),
            LinkedInHttpResponse(201, emptyHeaders(), """{"id":"comment-2"}"""),
        )
        val adapter = adapter(transport)

        val comments = adapter.fetchComments(actor, post)
        val reply = adapter.reply(
            actor,
            comments.items.single(),
            "Answer",
            IdempotencyKey("reply-1"),
        )

        assertEquals(ExternalCommentId("comment-1"), comments.items.single().externalCommentId)
        assertEquals(ThreadState.OPEN, comments.items.single().state)
        assertEquals(ExternalCommentId("comment-2"), reply.externalCommentId)
        val replyRequest = transport.requests[1]
        assertEquals("POST", replyRequest.method())
        assertEquals("202601", replyRequest.headers().firstValue("LinkedIn-Version").orElse(null))
        assertTrue(replyRequest.bodyPublisher().isPresent)
    }

    private fun adapter(
        transport: RecordingTransport,
        accessGate: SocialContentAccessGate = object : SocialContentAccessGate {
            override suspend fun authorize(request: SocialContentAccessRequest) = Unit
        },
        onTokenResolve: () -> Unit = {},
    ) = LinkedInCommunityManagementAdapter(
        properties = properties,
        objectMapper = ObjectMapper(),
        httpTransport = transport,
        accessTokenResolver = LinkedInSocialContentAccessTokenResolver { _, _ ->
            onTokenResolve()
            "access-token"
        },
        accessGate = accessGate,
    )

    private class RecordingTransport(vararg responses: LinkedInHttpResponse) : LinkedInHttpTransport {
        private val responses = responses.toList()
        private var index = 0
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
            requests += request
            return responses[index++]
        }
    }

    private fun emptyHeaders(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }
}
