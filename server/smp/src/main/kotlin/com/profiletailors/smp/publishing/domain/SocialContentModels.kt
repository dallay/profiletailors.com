package com.profiletailors.smp.publishing.domain

import java.time.Duration
import java.time.Instant

/** Workspace identifier that scopes every social-content read, sync, and reply operation. */
@JvmInline
value class WorkspaceScope(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace scope is required." }
    }
}

/** Provider-assigned actor id (e.g. `urn:li:organization:123`) identifying the remote social account. */
@JvmInline
value class ProviderActorId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider actor ID is required." }
    }
}

/** Provider-assigned post id used to reconcile external social posts with local publications. */
@JvmInline
value class ExternalPostId(val value: String) {
    init {
        require(value.isNotBlank()) { "External post ID is required." }
    }
}

/** Provider-assigned comment id used to dedupe inbound social comments and replies. */
@JvmInline
value class ExternalCommentId(val value: String) {
    init {
        require(value.isNotBlank()) { "External comment ID is required." }
    }
}

/** Caller-supplied key that guarantees a reply command is executed at most once. */
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

/**
 * Snapshot of the caller's permissions as observed by the social-content provider for an actor.
 *
 * @property accountKind LinkedIn account type that drove the capability check.
 * @property grantedScopes OAuth scopes currently granted to the actor.
 * @property roleState Effective administrative role of the actor on the underlying page.
 * @property canReadPosts Whether the actor's permissions allow post imports.
 * @property canReadComments Whether the actor's permissions allow comment imports.
 * @property canReplyAsActor Whether the actor may post replies under its identity.
 * @property canReceiveCommentWebhooks Whether LinkedIn delivers comment webhooks for the actor.
 * @property supportsNestedReplies Whether LinkedIn accepts threaded replies under the actor.
 * @property retention Cache retention windows enforced for activity read by this actor.
 */
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
    /**
     * Whether posts from this actor's company page can be imported into the workspace.
     *
     * Organization pages are the only account kind allowed to ingest posts.
     */
    fun canImportCompanyPage(): Boolean = accountKind == SocialAccountKind.ORGANIZATION_PAGE && canReadPosts
}

/**
 * Cache retention windows applied to social-content reads.
 *
 * Both TTLs must be strictly positive; the values feed payload-cache expiry and tombstone cleanup.
 */
data class RetentionRequirements(val activityTtl: java.time.Duration, val commenterProfileTtl: java.time.Duration) {
    init {
        require(!activityTtl.isNegative && !activityTtl.isZero) { "Activity TTL must be positive." }
        require(!commenterProfileTtl.isNegative && !commenterProfileTtl.isZero) {
            "Commenter profile TTL must be positive."
        }
    }
}

/**
 * LinkedIn-discovered actor candidate before workspace reconciliation.
 *
 * Discovery returns these before any workspace binding; [SocialContentActor] materializes one per workspace scope.
 */
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

/**
 * Workspace-scoped social-content actor configured for a single LinkedIn organization page.
 *
 * The actor is the unit of capability and import: only organization pages are accepted, and derived
 * capabilities drive every downstream permission check.
 */
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

    /**
     * Derive a [ProviderCapabilities] view for this actor scoped to the supplied retention windows.
     */
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

/**
 * Workspace-scoped social post either imported from a provider or locally reconciled.
 *
 * [origin] traces the provenance; [lifecycle] distinguishes published posts from tombstoned ones;
 * [expiresAt] drives automated cleanup once the activity TTL elapses.
 */
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
    /** Whether the post is still considered live in the workspace feed. */
    val isActive: Boolean get() = lifecycle == PostLifecycle.PUBLISHED

    /** Whether local services may mutate the post; false for externally imported posts. */
    val mutationAllowed: Boolean get() = false

    /** Returns true when [now] is at or past the post expiry instant. */
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    /**
     * Mark the post as reconciled with the supplied local publication id.
     *
     * Updates [origin] to [PostOrigin.PROFILETAILORS] and stores [publicationId]; rejection for blank ids preserves
     * invariants during command-to-post linking.
     */
    fun reconcileWithLocalPublication(publicationId: String): SocialPost {
        require(publicationId.isNotBlank()) { "Local publication ID is required." }
        return copy(origin = PostOrigin.PROFILETAILORS, localPublicationId = publicationId)
    }

    /** Mark the post as tombstoned at [at], preserving identity. */
    fun tombstone(at: Instant): SocialPost = copy(lifecycle = PostLifecycle.TOMBSTONED, expiresAt = at)

    companion object {
        private val DEFAULT_ACTIVITY_TTL: Duration = Duration.ofHours(48)

        /**
         * Build a freshly imported external post with the activity TTL applied to the supplied [now].
         *
         * @param expiresAt Defaults to `now + DEFAULT_ACTIVITY_TTL`; override when the provider returns its own expiry.
         */
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

/**
 * Cursor used to resume a social-content sync.
 *
 * [lastSuccessfulAt] records the last successful sync attempt; [highWaterMark] tracks the highest known publishedAt.
 */
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

    /**
     * Return a new checkpoint advanced to [nextCursor] and marked successful at [successfulAt].
     *
     * @param nextHighWaterMark Defaults to the previous high-water mark to keep progress monotonic.
     */
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

/** Inbound LinkedIn webhook event whose payload is stored under [payloadCacheKey]. */
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

/** Encrypted webhook payload cached for replay within the lifetime of [expiresAt]. */
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

    /** Whether the cached payload is still retrievable at [now]. */
    fun isAvailable(now: Instant): Boolean = now.isBefore(expiresAt)
}

/**
 * Default capability resolution backed by [SocialContentFeatureGates].
 *
 * Implements [SocialContentCapabilityResolver] returning [CapabilityDecision.Allowed] when the actor's role, scopes,
 * and feature gates permit [operation], otherwise a [CapabilityDecision.Denied] with the matching failure.
 */
class DefaultCapabilityResolver(private val gates: SocialContentFeatureGates) : SocialContentCapabilityResolver {
    override fun resolve(
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

/** Inbound LinkedIn comment eligible for reply when its [state] is [ThreadState.OPEN] and not expired. */
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
    /** Returns true when [now] is at or past the comment expiry instant. */
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}

/**
 * Outbound reply command triggered by an actor against a [SocialComment] parent.
 *
 * Carries the workspace scope and the idempotency key used to dedupe replays.
 */
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

    /**
     * Validate the command against the parent [comment] and the supplied [actorScope].
     *
     * @throws ReplyRejectedException When the parent comment, actor, or workspace context does not match.
     */
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

/** Reasons a [ReplyCommand] can be rejected before being forwarded to the provider. */
enum class ReplyRejectionReason {
    WORKSPACE_MISMATCH,
    ACTOR_MISMATCH,
    PARENT_NOT_FOUND,
    THREAD_NOT_OPEN,
    EXPIRED,
    CAPABILITY_DENIED,
}

/** Thrown when a [ReplyCommand] is rejected by [ReplyCommand.validateAgainst] or the capability check. */
class ReplyRejectedException(val reason: ReplyRejectionReason) : IllegalArgumentException("Reply rejected: $reason")
