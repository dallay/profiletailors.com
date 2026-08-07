package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.application.DefaultSocialContentAccessGate
import com.profiletailors.smp.publishing.application.SocialContentAccessGate
import com.profiletailors.smp.publishing.application.SocialContentAccessPolicy
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentAccessRequest
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentActorCandidate
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.ThreadState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * Credential-free Community Management adapter seam for organization Page reads.
 *
 * The adapter remains separate from [RealLinkedInPublisher] and is not wired into application
 * connections until the Community Management approval and scope gates are satisfied.
 */
@Suppress("StringLiteralDuplication")
class LinkedInCommunityManagementAdapter(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val accessTokenResolver: LinkedInSocialContentAccessTokenResolver,
    private val accessGate: SocialContentAccessGate = DefaultSocialContentAccessGate(
        approvalEvidenceRepository = SocialContentApprovalEvidenceRepository { _, _ -> null },
        policy = SocialContentAccessPolicy(),
    ),
) : SocialContentProvider {
    override suspend fun discoverActors(
        scope: WorkspaceScope,
        connectionId: String,
    ): List<SocialContentActorCandidate> = discoverActors(scope, connectionId, connectionId)

    override suspend fun discoverActors(
        scope: WorkspaceScope,
        connectionId: String,
        socialAccountId: String,
    ): List<SocialContentActorCandidate> {
        authorize(
            scope = scope,
            socialAccountId = socialAccountId,
            operation = CapabilityOperation.DISCOVER_ACTORS,
            actorKind = SocialAccountKind.ORGANIZATION_PAGE,
            roleState = ActorRoleState.ADMIN,
            grantedScopes = ORGANIZATION_SCOPES,
        )
        val candidates = mutableListOf<SocialContentActorCandidate>()
        var start = 0
        var total = 0
        do {
            val response = getJson(
                scope = scope,
                connectionId = connectionId,
                path = organizationAclsPath(start),
            )
            val elements = response.path("elements")
            elements.mapNotNullTo(candidates, ::organizationCandidate)
            total = response.path("paging").path("total").asInt(start + elements.size())
            if (elements.isEmpty) break
            start += elements.size()
        } while (start < total && candidates.size < MAX_DISCOVERED_ACTORS)
        return candidates.take(MAX_DISCOVERED_ACTORS)
    }

    override suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        pageSize: Int,
    ): SocialContentPage<SocialPost> = fetchPosts(actor, cursor, pageSize, null)

    override suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        modifiedSince: Instant?,
    ): SocialContentPage<SocialPost> = fetchPosts(actor, cursor, DEFAULT_PAGE_SIZE, modifiedSince)

    private suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        pageSize: Int,
        modifiedSince: Instant?,
    ): SocialContentPage<SocialPost> {
        requireOrganizationActor(actor)
        authorize(actor, CapabilityOperation.READ_POSTS)
        val start = cursor?.value?.toIntOrNull() ?: 0
        require(start >= 0) { "LinkedIn post cursor must be non-negative." }
        require(pageSize in 1..DEFAULT_PAGE_SIZE) {
            "LinkedIn post page size must be between 1 and $DEFAULT_PAGE_SIZE."
        }
        val since = modifiedSince?.let { "&lastModifiedAt=${encode(it.toEpochMilli().toString())}" }.orEmpty()
        val response = getJson(
            scope = actor.scope,
            connectionId = actor.connectionId,
            path = "/rest/posts?author=${encode(actor.externalActorId.value)}&q=author&" +
                "start=$start&count=$pageSize$since",
        )
        val elements = response.path("elements")
        val posts = elements.map { element -> post(actor, element) }
        return SocialContentPage(
            items = posts,
            nextCursor = nextCursor(start, elements.size(), response.path("paging")),
            highWaterMark = posts.maxOfOrNull { it.lastModifiedAt ?: it.publishedAt },
        )
    }

    override suspend fun fetchComments(
        actor: SocialContentActor,
        post: SocialPost,
        cursor: PageCursor?,
        pageSize: Int,
    ): SocialContentPage<SocialComment> {
        requireOrganizationActor(actor)
        authorize(actor, CapabilityOperation.READ_COMMENTS)
        val start = cursor?.value?.toIntOrNull() ?: 0
        require(start >= 0) { "LinkedIn comment cursor must be non-negative." }
        require(pageSize in 1..DEFAULT_PAGE_SIZE) {
            "LinkedIn comment page size must be between 1 and $DEFAULT_PAGE_SIZE."
        }
        val response = requestJson(
            scope = actor.scope,
            connectionId = actor.connectionId,
            method = "GET",
            path = "/rest/socialActions/${encode(post.externalPostId.value)}/comments?start=$start&count=$pageSize",
        )
        val elements = response.body.path("elements")
        val comments = elements.map { element ->
            val parent = element.path("parentComment").asText("").takeIf(String::isNotBlank)
            SocialComment(
                scope = actor.scope,
                postId = post.externalPostId,
                ownerActorId = actor.id,
                externalCommentId = ExternalCommentId(element.path("id").asText()),
                parentExternalCommentId = parent?.let(::ExternalCommentId),
                actorExternalId = ProviderActorId(element.path("actor").asText()),
                body = element.path("message").path("text").asText(""),
                createdAt = epochMillis(element.path("created").path("time").asLong()),
                state = ThreadState.OPEN,
                expiresAt = post.expiresAt,
            )
        }
        return SocialContentPage(comments, nextCursor(start, elements.size(), response.body.path("paging")))
    }

    override suspend fun reply(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        idempotencyKey: IdempotencyKey,
    ): SocialComment {
        requireOrganizationActor(actor)
        authorize(actor, CapabilityOperation.REPLY)
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "actor" to actor.externalActorId.value,
                "message" to mapOf("text" to body),
                "parentComment" to parent.externalCommentId.value,
            ),
        )
        val response = requestJson(
            scope = actor.scope,
            connectionId = actor.connectionId,
            method = "POST",
            path = "/rest/socialActions/${encode(parent.postId.value)}/comments",
            body = payload,
            idempotencyKey = idempotencyKey,
        )
        val externalId = response.response.headers.firstValue("x-restli-id").orElse(null)
            ?: response.body.path("id").asText("linkedin-reply-${idempotencyKey.value}")
        return parent.copy(
            externalCommentId = ExternalCommentId(externalId),
            parentExternalCommentId = parent.externalCommentId,
            actorExternalId = actor.externalActorId,
            body = body,
        )
    }

    private fun organizationAclsPath(start: Int): String = if (start == 0) {
        "/rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR"
    } else {
        "/rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR&start=$start&count=$DEFAULT_PAGE_SIZE"
    }

    private fun organizationCandidate(element: JsonNode): SocialContentActorCandidate? {
        val organization = element.path("organization").asText("")
        val role = element.path("role").asText("")
        if (!organization.startsWith(ORGANIZATION_URN_PREFIX) || role != ADMINISTRATOR_ROLE) return null
        return SocialContentActorCandidate(
            id = organization,
            externalActorId = ProviderActorId(organization),
            kind = SocialAccountKind.ORGANIZATION_PAGE,
            displayName = element.path("organization~").path("localizedName").asText(organization),
            roleState = ActorRoleState.ADMIN,
            grantedScopes = ORGANIZATION_SCOPES,
        )
    }

    private fun post(actor: SocialContentActor, element: JsonNode): SocialPost {
        val createdAt = epochMillis(element.path("created").path("time").asLong())
        val modifiedAt = epochMillis(
            element.path("lastModified").path("time").asLong(createdAt.toEpochMilli()),
        )
        return SocialPost.imported(
            scope = actor.scope,
            actor = actor,
            externalPostId = ExternalPostId(element.path("id").asText()),
            publishedAt = createdAt,
            now = modifiedAt,
            body = element.path("commentary").textValue()
                ?: element.path("commentary").path("text").textValue(),
        ).copy(lastModifiedAt = modifiedAt)
    }

    private suspend fun getJson(scope: WorkspaceScope, connectionId: String, path: String): JsonNode =
        requestJson(scope, connectionId, "GET", path).body

    private suspend fun authorize(
        scope: WorkspaceScope,
        socialAccountId: String,
        operation: CapabilityOperation,
        actorKind: SocialAccountKind,
        roleState: ActorRoleState?,
        grantedScopes: Set<String>?,
    ) {
        accessGate.authorize(
            SocialContentAccessRequest(
                scope = scope,
                socialAccountId = socialAccountId,
                operation = operation,
                actorKind = actorKind,
                roleState = roleState,
                grantedScopes = grantedScopes,
                apiVersion = properties.apiVersion,
            ),
        )
    }

    private suspend fun authorize(actor: SocialContentActor, operation: CapabilityOperation) = authorize(
        scope = actor.scope,
        socialAccountId = actor.socialAccountId,
        operation = operation,
        actorKind = actor.kind,
        roleState = actor.roleState,
        grantedScopes = actor.grantedScopes,
    )

    private suspend fun requestJson(
        scope: WorkspaceScope,
        connectionId: String,
        method: String,
        path: String,
        body: String? = null,
        idempotencyKey: IdempotencyKey? = null,
    ): LinkedInJsonResponse {
        val accessToken = accessTokenResolver.resolve(scope, connectionId)
        val builder = HttpRequest.newBuilder(URI.create(properties.apiBaseUrl + path))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("LinkedIn-Version", properties.apiVersion)
            .header("X-Restli-Protocol-Version", "2.0.0")
        idempotencyKey?.let { builder.header("X-Idempotency-Key", it.value) }
        val request = when (method) {
            "POST" ->
                builder
                    .header(CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.orEmpty()))
            else -> builder.GET()
        }.build()
        val response = httpTransport.send(request)
        if (response.statusCode !in HTTP_SUCCESS_RANGE) throw providerException(response)
        return LinkedInJsonResponse(response, objectMapper.readTree(response.body))
    }

    private fun providerException(response: LinkedInHttpResponse): SocialContentProviderException {
        val failure = when (response.statusCode) {
            HTTP_UNAUTHORIZED -> SocialContentProviderFailure.UNAUTHORIZED
            HTTP_FORBIDDEN -> SocialContentProviderFailure.ROLE_FORBIDDEN
            HTTP_TOO_MANY_REQUESTS -> SocialContentProviderFailure.RATE_LIMITED
            in HTTP_SERVER_ERROR_RANGE -> SocialContentProviderFailure.PROVIDER_UNAVAILABLE
            else -> SocialContentProviderFailure.PROVIDER_UNAVAILABLE
        }
        val retryAfter = response.headers.firstValue("Retry-After").orElse(null)
            ?.toLongOrNull()
            ?.let(Duration::ofSeconds)
        return SocialContentProviderException(failure, response.statusCode, retryAfter)
    }

    private fun requireOrganizationActor(actor: SocialContentActor) {
        require(actor.kind == SocialAccountKind.ORGANIZATION_PAGE) {
            "LinkedIn community management requires an organization page."
        }
        require(actor.externalActorId.value.startsWith(ORGANIZATION_URN_PREFIX)) {
            "LinkedIn organization URN is required."
        }
    }

    private fun nextCursor(start: Int, size: Int, paging: JsonNode): PageCursor? {
        val total = paging.path("total").asInt(NO_TOTAL)
        val next = start + size
        return if (size > 0 && (total == NO_TOTAL || next < total)) PageCursor(next.toString()) else null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun epochMillis(value: Long): Instant = Instant.ofEpochMilli(value)

    private data class LinkedInJsonResponse(val response: LinkedInHttpResponse, val body: JsonNode)

    private companion object {
        const val CONTENT_TYPE = "Content-Type"
        const val DEFAULT_PAGE_SIZE = 100
        const val MAX_DISCOVERED_ACTORS = 1000
        const val NO_TOTAL = -1
        const val ADMINISTRATOR_ROLE = "ADMINISTRATOR"
        const val ORGANIZATION_URN_PREFIX = "urn:li:organization:"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SUCCESS_RANGE = 200..299
        val HTTP_SERVER_ERROR_RANGE = 500..599
        val ORGANIZATION_SCOPES = setOf(
            "r_organization_social",
            "r_organization_social_feed",
            "r_organization_social_social_actions",
            "w_organization_social",
        )
    }
}

fun interface LinkedInSocialContentAccessTokenResolver {
    suspend fun resolve(scope: WorkspaceScope, connectionId: String): String
}
