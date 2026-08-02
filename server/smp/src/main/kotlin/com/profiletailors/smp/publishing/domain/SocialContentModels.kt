package com.profiletailors.smp.publishing.domain

import java.time.Duration
import java.time.Instant

@JvmInline
value class WorkspaceScope(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace scope is required." }
    }
}

@JvmInline
value class ProviderActorId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider actor ID is required." }
    }
}

@JvmInline
value class ExternalPostId(val value: String) {
    init {
        require(value.isNotBlank()) { "External post ID is required." }
    }
}

@JvmInline
value class ExternalCommentId(val value: String) {
    init {
        require(value.isNotBlank()) { "External comment ID is required." }
    }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Idempotency key is required." }
    }
}

enum class ActorRoleState { ADMIN, MEMBER, UNKNOWN, REVOKED }
enum class PostOrigin { EXTERNAL_OR_UNKNOWN, PROFILETAILORS }
enum class PostLifecycle { PUBLISHED, TOMBSTONED }
enum class ThreadState { OPEN, CLOSED, PROCESSING, DELETED }
enum class SyncResource { POSTS, COMMENTS }
enum class CacheKind { ACTIVITY, COMMENTER_PROFILE }
enum class CapabilityOperation { DISCOVER_ACTORS, READ_POSTS, READ_COMMENTS, REPLY }
enum class CapabilityFailure { REAUTH_REQUIRED, ROLE_REQUIRED, MISSING_SCOPE, UNSUPPORTED }

data class SocialContentFeatureGates(
    val discoveryEnabled: Boolean = false,
    val importEnabled: Boolean = false,
    val inboxEnabled: Boolean = false,
    val repliesEnabled: Boolean = false,
)

sealed interface CapabilityDecision {
    data object Allowed : CapabilityDecision
    data class Denied(val failure: CapabilityFailure) : CapabilityDecision
}

@JvmInline
value class PageCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "Page cursor is required." }
    }
}

data class ProviderCapabilities(
    val accountKind: SocialAccountKind,
    val grantedScopes: Set<String>,
    val roleState: ActorRoleState,
    val canReadPosts: Boolean,
    val canReadComments: Boolean,
    val canReplyAsActor: Boolean,
    val canReceiveCommentWebhooks: Boolean,
    val supportsNestedReplies: Boolean,
    val retention: RetentionRequirements,
) {
    fun canImportCompanyPage(): Boolean = accountKind == SocialAccountKind.ORGANIZATION_PAGE && canReadPosts
}

data class RetentionRequirements(val activityTtl: java.time.Duration, val commenterProfileTtl: java.time.Duration) {
    init {
        require(!activityTtl.isNegative && !activityTtl.isZero) { "Activity TTL must be positive." }
        require(!commenterProfileTtl.isNegative && !commenterProfileTtl.isZero) {
            "Commenter profile TTL must be positive."
        }
    }
}

data class SocialContentActorCandidate(
    val id: String,
    val externalActorId: ProviderActorId,
    val kind: SocialAccountKind,
    val displayName: String,
    val roleState: ActorRoleState,
    val grantedScopes: Set<String>,
) {
    init {
        require(id.isNotBlank()) { "Actor candidate ID is required." }
        require(displayName.isNotBlank()) { "Actor candidate display name is required." }
    }
}

data class SocialContentActor(
    val id: String,
    val scope: WorkspaceScope,
    val connectionId: String,
    val provider: SocialProvider,
    val externalActorId: ProviderActorId,
    val kind: SocialAccountKind,
    val displayName: String,
    val roleState: ActorRoleState,
    val grantedScopes: Set<String>,
) {
    init {
        require(id.isNotBlank()) { "Actor ID is required." }
        require(connectionId.isNotBlank()) { "Connection ID is required." }
        require(displayName.isNotBlank()) { "Actor display name is required." }
        require(kind == SocialAccountKind.ORGANIZATION_PAGE) {
            "Social content actors must be organization pages."
        }
    }

    fun capabilities(retention: RetentionRequirements): ProviderCapabilities = ProviderCapabilities(
        accountKind = kind,
        grantedScopes = grantedScopes,
        roleState = roleState,
        canReadPosts = roleState != ActorRoleState.REVOKED &&
            grantedScopes.any { it in setOf("r_organization_social", "r_organization_social_feed") },
        canReadComments = roleState != ActorRoleState.REVOKED &&
            grantedScopes.contains("r_organization_social_social_actions"),
        canReplyAsActor = roleState == ActorRoleState.ADMIN &&
            grantedScopes.contains("w_organization_social"),
        canReceiveCommentWebhooks = grantedScopes.contains("rw_organization_admin"),
        supportsNestedReplies = true,
        retention = retention,
    )
}

data class SocialPost(
    val scope: WorkspaceScope,
    val provider: SocialProvider,
    val actorId: String,
    val externalPostId: ExternalPostId,
    val publishedAt: Instant,
    val body: String? = null,
    val origin: PostOrigin = PostOrigin.EXTERNAL_OR_UNKNOWN,
    val localPublicationId: String? = null,
    val lifecycle: PostLifecycle = PostLifecycle.PUBLISHED,
    val expiresAt: Instant,
) {
    val isActive: Boolean get() = lifecycle == PostLifecycle.PUBLISHED
    val mutationAllowed: Boolean get() = false

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun reconcileWithLocalPublication(publicationId: String): SocialPost {
        require(publicationId.isNotBlank()) { "Local publication ID is required." }
        return copy(origin = PostOrigin.PROFILETAILORS, localPublicationId = publicationId)
    }

    fun tombstone(at: Instant): SocialPost = copy(lifecycle = PostLifecycle.TOMBSTONED, expiresAt = at)

    companion object {
        private val DEFAULT_ACTIVITY_TTL: Duration = Duration.ofHours(48)

        fun imported(
            scope: WorkspaceScope,
            actor: SocialContentActor,
            externalPostId: ExternalPostId,
            publishedAt: Instant,
            now: Instant,
            body: String? = null,
            expiresAt: Instant = now.plus(DEFAULT_ACTIVITY_TTL),
        ) = SocialPost(scope, actor.provider, actor.id, externalPostId, publishedAt, body, expiresAt = expiresAt)
    }
}

data class SyncCheckpoint(
    val scope: WorkspaceScope,
    val actorId: String,
    val resource: SyncResource,
    val cursor: PageCursor?,
    val highWaterMark: Instant? = null,
    val lastSuccessfulAt: Instant?,
) {
    init {
        require(actorId.isNotBlank()) { "Checkpoint actor ID is required." }
    }

    fun advance(
        nextCursor: PageCursor?,
        successfulAt: Instant,
        nextHighWaterMark: Instant? = highWaterMark,
    ): SyncCheckpoint = copy(
        cursor = nextCursor,
        highWaterMark = nextHighWaterMark,
        lastSuccessfulAt = successfulAt,
    )
}

data class WebhookEvent(
    val scope: WorkspaceScope,
    val providerEventId: String,
    val actorId: String,
    val receivedAt: Instant,
    val payloadCacheKey: String,
) {
    init {
        require(providerEventId.isNotBlank()) { "Provider event ID is required." }
        require(actorId.isNotBlank()) { "Webhook actor ID is required." }
        require(payloadCacheKey.isNotBlank()) { "Webhook payload cache key is required." }
    }
}

data class PayloadCache(
    val scope: WorkspaceScope,
    val key: String,
    val kind: CacheKind,
    val encryptedPayload: ByteArray,
    val expiresAt: Instant,
) {
    init {
        require(key.isNotBlank()) { "Payload cache key is required." }
        require(encryptedPayload.isNotEmpty()) { "Payload cache payload is required." }
    }

    fun isAvailable(now: Instant): Boolean = now.isBefore(expiresAt)
}

class DefaultCapabilityResolver(private val gates: SocialContentFeatureGates) {
    fun resolve(
        actor: SocialContentActor,
        operation: CapabilityOperation,
        retention: RetentionRequirements,
    ): CapabilityDecision {
        val failure = actor.failureFor(operation, retention)
        return if (failure == null) CapabilityDecision.Allowed else CapabilityDecision.Denied(failure)
    }

    private fun SocialContentActor.failureFor(
        operation: CapabilityOperation,
        retention: RetentionRequirements,
    ): CapabilityFailure? {
        if (roleState == ActorRoleState.REVOKED) return CapabilityFailure.REAUTH_REQUIRED
        if (operation == CapabilityOperation.REPLY && roleState != ActorRoleState.ADMIN) {
            return CapabilityFailure.ROLE_REQUIRED
        }
        if (!gates.isEnabled(operation)) return CapabilityFailure.UNSUPPORTED
        return if (capabilities(retention).supports(operation)) null else CapabilityFailure.MISSING_SCOPE
    }

    private fun SocialContentFeatureGates.isEnabled(operation: CapabilityOperation): Boolean = when (operation) {
        CapabilityOperation.DISCOVER_ACTORS -> discoveryEnabled
        CapabilityOperation.READ_POSTS -> importEnabled
        CapabilityOperation.READ_COMMENTS -> inboxEnabled
        CapabilityOperation.REPLY -> repliesEnabled
    }

    private fun ProviderCapabilities.supports(operation: CapabilityOperation): Boolean = when (operation) {
        CapabilityOperation.DISCOVER_ACTORS -> accountKind == SocialAccountKind.ORGANIZATION_PAGE
        CapabilityOperation.READ_POSTS -> canReadPosts
        CapabilityOperation.READ_COMMENTS -> canReadComments
        CapabilityOperation.REPLY -> canReplyAsActor
    }
}

data class SocialComment(
    val scope: WorkspaceScope,
    val postId: ExternalPostId,
    val ownerActorId: String,
    val externalCommentId: ExternalCommentId,
    val parentExternalCommentId: ExternalCommentId?,
    val actorExternalId: ProviderActorId,
    val body: String,
    val createdAt: Instant,
    val state: ThreadState,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}

data class ReplyCommand(
    val scope: WorkspaceScope,
    val actorId: String,
    val parentCommentId: ExternalCommentId,
    val body: String,
    val idempotencyKey: IdempotencyKey,
) {
    init {
        require(actorId.isNotBlank()) { "Reply actor ID is required." }
        require(body.isNotBlank()) { "Reply body is required." }
    }

    fun validateAgainst(comment: SocialComment, actorScope: WorkspaceScope, now: Instant) {
        val rejection = when {
            scope != actorScope || comment.scope != actorScope -> ReplyRejectionReason.WORKSPACE_MISMATCH
            comment.ownerActorId != actorId -> ReplyRejectionReason.ACTOR_MISMATCH
            comment.externalCommentId != parentCommentId -> ReplyRejectionReason.PARENT_NOT_FOUND
            comment.state != ThreadState.OPEN -> ReplyRejectionReason.THREAD_NOT_OPEN
            comment.isExpired(now) -> ReplyRejectionReason.EXPIRED
            else -> null
        }
        rejection?.let { throw ReplyRejectedException(it) }
    }
}

enum class ReplyRejectionReason {
    WORKSPACE_MISMATCH,
    ACTOR_MISMATCH,
    PARENT_NOT_FOUND,
    THREAD_NOT_OPEN,
    EXPIRED,
    CAPABILITY_DENIED,
}
class ReplyRejectedException(val reason: ReplyRejectionReason) : IllegalArgumentException("Reply rejected: $reason")
