package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** Workspace identifier that scopes every social-content read, sync, and reply operation. */
@ValueObject
@JvmInline
value class WorkspaceScope(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace scope is required." }
    }
}

/** Provider-assigned actor id (e.g. `urn:li:organization:123`) identifying the remote social account. */
@ValueObject
@JvmInline
value class ProviderActorId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider actor ID is required." }
    }
}

/** Provider-assigned post id used to reconcile external social posts with local publications. */
@ValueObject
@JvmInline
value class ExternalPostId(val value: String) {
    init {
        require(value.isNotBlank()) { "External post ID is required." }
    }
}

/** Provider-assigned comment id used to dedupe inbound social comments and replies. */
@ValueObject
@JvmInline
value class ExternalCommentId(val value: String) {
    init {
        require(value.isNotBlank()) { "External comment ID is required." }
    }
}

/** Caller-supplied key that guarantees a reply command is executed at most once. */
@ValueObject
@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Idempotency key is required." }
    }
}

@ValueObject
enum class ActorRoleState { ADMIN, MEMBER, UNKNOWN, REVOKED }

@ValueObject
enum class PostOrigin { EXTERNAL_OR_UNKNOWN, PROFILETAILORS }

@ValueObject
enum class PostLifecycle { PUBLISHED, TOMBSTONED }

@ValueObject
enum class ThreadState { OPEN, CLOSED, PROCESSING, DELETED }

@ValueObject
enum class SyncResource { POSTS, COMMENTS }

@ValueObject
enum class CacheKind { ACTIVITY, COMMENTER_PROFILE }

@ValueObject
enum class CapabilityOperation { DISCOVER_ACTORS, READ_POSTS, READ_COMMENTS, REPLY }

@ValueObject
enum class CapabilityFailure { REAUTH_REQUIRED, ROLE_REQUIRED, MISSING_SCOPE, UNSUPPORTED }

private const val MAX_CALENDAR_LIMIT = 100

@ValueObject
data class SocialContentCalendarQuery(
    val scope: WorkspaceScope,
    val from: Instant,
    val to: Instant,
    val actorId: String? = null,
    val lifecycle: PostLifecycle? = null,
    val cursor: PageCursor? = null,
    val limit: Int = 50,
) {
    init {
        require(from.isBefore(to)) { "Calendar range must have a start before its end." }
        require(limit in 1..MAX_CALENDAR_LIMIT) {
            "Calendar limit must be between 1 and $MAX_CALENDAR_LIMIT."
        }
    }
}

@ValueObject
enum class SocialContentAccessDenial {
    OPERATION_DISABLED,
    EVIDENCE_MISSING,
    COMMUNITY_MANAGEMENT_NOT_APPROVED,
    WORKSPACE_MISMATCH,
    ACCOUNT_MISMATCH,
    ORGANIZATION_PAGE_REQUIRED,
    ADMIN_ROLE_REQUIRED,
    REQUIRED_SCOPE_MISSING,
    API_VERSION_REQUIRED,
    API_VERSION_UNSUPPORTED,
    RETENTION_POLICY_VERSION_REQUIRED,
}

class SocialContentAccessDeniedException(val denial: SocialContentAccessDenial) :
    IllegalStateException("Social content access denied: $denial")

@ValueObject
data class SocialContentApprovalEvidence(
    val workspaceId: String,
    val socialAccountId: String,
    val roleState: ActorRoleState,
    val grantedScopes: Set<String>,
    val communityManagementApproved: Boolean,
    val apiVersion: String,
    val retentionPolicyVersion: String,
) {
    init {
        require(workspaceId.isNotBlank()) { "Approval evidence workspace is required." }
        require(socialAccountId.isNotBlank()) { "Approval evidence account is required." }
    }
}

data class SocialContentAccessRequest(
    val scope: WorkspaceScope,
    val socialAccountId: String,
    val operation: CapabilityOperation,
    val actorKind: SocialAccountKind,
    val roleState: ActorRoleState?,
    val grantedScopes: Set<String>?,
    val apiVersion: String?,
)

@ValueObject
data class SocialContentSyncLimits(val pageSize: Int, val maxPages: Int) {
    init {
        require(pageSize in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            "Social content page size must be between $MIN_PAGE_SIZE and $MAX_PAGE_SIZE."
        }
        require(maxPages >= MIN_PAGE_SIZE) { "Social content max pages must be at least $MIN_PAGE_SIZE." }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        const val DEFAULT_MAX_PAGES = 10
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = DEFAULT_PAGE_SIZE
    }
}

data class SocialContentFeatureGates(
    var discoveryEnabled: Boolean = false,
    var importEnabled: Boolean = false,
    var inboxEnabled: Boolean = false,
    var repliesEnabled: Boolean = false,
)

sealed interface CapabilityDecision {
    data object Allowed : CapabilityDecision
    data class Denied(val failure: CapabilityFailure) : CapabilityDecision
}

@ValueObject
@JvmInline
value class PageCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "Page cursor is required." }
    }
}

@JvmInline
value class CalendarCursorVersion(val value: String) {
    init {
        require(value in SUPPORTED_VERSIONS) { "Unsupported calendar cursor version: $value" }
    }

    companion object {
        const val V1 = "1"
        val SUPPORTED_VERSIONS = setOf(V1)
    }
}

data class SocialContentCalendarCursor(
    val version: CalendarCursorVersion,
    val workspaceId: String,
    val publishedAt: Instant,
    val provider: SocialProvider,
    val socialAccountId: String,
    val externalPostId: String,
) {
    init {
        require(workspaceId.isNotBlank()) { "Calendar cursor workspace is required." }
        require(socialAccountId.isNotBlank()) { "Calendar cursor social account is required." }
        require(externalPostId.isNotBlank()) { "Calendar cursor external post is required." }
        require(listOf(workspaceId, socialAccountId, externalPostId).none { it.contains(CALENDAR_CURSOR_DELIMITER) }) {
            "Calendar cursor fields cannot contain the delimiter."
        }
    }

    private companion object {
        const val CALENDAR_CURSOR_DELIMITER: Char = '\u001F'
    }
}

class InvalidSocialContentCursorException(
    message: String = "Invalid social content cursor",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

object SocialContentCalendarCursorCodec {
    private const val DELIMITER: Char = '\u001F'
    private const val FIELD_COUNT = 6
    private const val VERSION_INDEX = 0
    private const val WORKSPACE_INDEX = 1
    private const val PUBLISHED_AT_INDEX = 2
    private const val PROVIDER_INDEX = 3
    private const val SOCIAL_ACCOUNT_INDEX = 4
    private const val EXTERNAL_POST_INDEX = 5
    private val BASE64_URL_TOKEN = Regex("[A-Za-z0-9_-]+")

    fun encode(cursor: SocialContentCalendarCursor): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            listOf(
                cursor.version.value,
                cursor.workspaceId,
                cursor.publishedAt.toString(),
                cursor.provider.name,
                cursor.socialAccountId,
                cursor.externalPostId,
            ).joinToString(DELIMITER.toString()).toByteArray(Charsets.UTF_8),
        )

    @Suppress("ThrowsCount")
    fun decode(value: String): SocialContentCalendarCursor {
        val token = value.trim()
        if (token.isBlank() || !BASE64_URL_TOKEN.matches(token)) throw InvalidSocialContentCursorException()

        return try {
            val fields = decodePayload(token).split(DELIMITER)
            if (fields.size != FIELD_COUNT) throw InvalidSocialContentCursorException()
            val version = try {
                CalendarCursorVersion(fields[VERSION_INDEX])
            } catch (exception: IllegalArgumentException) {
                throw InvalidSocialContentCursorException(cause = exception)
            }
            SocialContentCalendarCursor(
                version = version,
                workspaceId = fields[WORKSPACE_INDEX],
                publishedAt = Instant.parse(fields[PUBLISHED_AT_INDEX]),
                provider = SocialProvider.entries.singleOrNull { it.name == fields[PROVIDER_INDEX] }
                    ?: throw InvalidSocialContentCursorException(),
                socialAccountId = fields[SOCIAL_ACCOUNT_INDEX],
                externalPostId = fields[EXTERNAL_POST_INDEX],
            )
        } catch (exception: InvalidSocialContentCursorException) {
            throw exception
        } catch (exception: DateTimeParseException) {
            throw InvalidSocialContentCursorException(cause = exception)
        } catch (exception: IllegalArgumentException) {
            throw InvalidSocialContentCursorException(cause = exception)
        }
    }

    private fun decodePayload(token: String): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(token)))
            .toString()
    } catch (exception: CharacterCodingException) {
        throw InvalidSocialContentCursorException(cause = exception)
    } catch (exception: IllegalArgumentException) {
        throw InvalidSocialContentCursorException(cause = exception)
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
@ValueObject
data class RetentionRequirements(val activityTtl: Duration, val commenterProfileTtl: Duration) {
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
@AggregateRoot
data class SocialContentActor(
    val id: String,
    val scope: WorkspaceScope,
    val socialAccountId: String = id,
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
            grantedScopes.any(POST_READ_SCOPES::contains),
        canReadComments = roleState != ActorRoleState.REVOKED &&
            grantedScopes.contains(COMMENT_READ_SCOPE),
        canReplyAsActor = roleState == ActorRoleState.ADMIN &&
            grantedScopes.contains(REPLY_SCOPE),
        canReceiveCommentWebhooks = grantedScopes.contains(ADMIN_SCOPE),
        supportsNestedReplies = true,
        retention = retention,
    )

    private companion object {
        val POST_READ_SCOPES = setOf("r_organization_social", "r_organization_social_feed")
        const val COMMENT_READ_SCOPE = "r_organization_social_social_actions"
        const val REPLY_SCOPE = "w_organization_social"
        const val ADMIN_SCOPE = "rw_organization_admin"
    }
}

/**
 * Workspace-scoped social post either imported from a provider or locally reconciled.
 *
 * [origin] traces the provenance; [lifecycle] distinguishes published posts from tombstoned ones;
 * [expiresAt] drives automated cleanup once the activity TTL elapses.
 */
@AggregateRoot
data class SocialPost(
    val scope: WorkspaceScope,
    val provider: SocialProvider,
    val actorId: String,
    val externalPostId: ExternalPostId,
    val publishedAt: Instant,
    val body: String? = null,
    val lastModifiedAt: Instant? = null,
    val origin: PostOrigin = PostOrigin.EXTERNAL_OR_UNKNOWN,
    val localPublicationId: String? = null,
    val lifecycle: PostLifecycle = PostLifecycle.PUBLISHED,
    val expiresAt: Instant,
) {
    init {
        require(actorId.isNotBlank()) { "Post actor ID is required." }
        body?.let { require(it.isNotBlank()) { "Post body must not be blank." } }
        if (origin == PostOrigin.PROFILETAILORS) {
            require(!localPublicationId.isNullOrBlank()) {
                "Profile Tailors posts require a local publication ID."
            }
        }
    }

    /** Whether the post is still considered live in the workspace feed. */
    val isActive: Boolean get() = lifecycle == PostLifecycle.PUBLISHED

    val mutationAllowed: Boolean get() = origin == PostOrigin.PROFILETAILORS && localPublicationId != null

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
@AggregateRoot
data class SyncCheckpoint(
    val scope: WorkspaceScope,
    val actorId: String,
    val resource: SyncResource,
    val cursor: PageCursor?,
    val highWaterMark: Instant? = null,
    val lastSuccessfulAt: Instant?,
    val postId: ExternalPostId? = null,
    val provider: SocialProvider = SocialProvider.LINKEDIN,
) {
    init {
        require(actorId.isNotBlank()) { "Checkpoint actor ID is required." }
        if (resource == SyncResource.COMMENTS) {
            requireNotNull(postId) { "Comment checkpoints require a post ID." }
        } else {
            require(postId == null) { "Post checkpoints must not have a post ID." }
        }
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
        highWaterMark = listOfNotNull(highWaterMark, nextHighWaterMark).maxOrNull(),
        lastSuccessfulAt = successfulAt,
    )
}

/** Inbound LinkedIn webhook event whose payload is stored under [payloadCacheKey]. */
@ValueObject
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
@AggregateRoot
class PayloadCache(
    val scope: WorkspaceScope,
    val key: String,
    val kind: CacheKind,
    encryptedPayload: ByteArray,
    val expiresAt: Instant,
) {
    private val payload = encryptedPayload.copyOf()
    val encryptedPayload: ByteArray get() = payload.copyOf()

    init {
        require(key.isNotBlank()) { "Payload cache key is required." }
        require(payload.isNotEmpty()) { "Payload cache payload is required." }
    }

    /** Whether the cached payload is still retrievable at [now]. */
    fun isAvailable(now: Instant): Boolean = now.isBefore(expiresAt)

    override fun equals(other: Any?): Boolean = other is PayloadCache &&
        scope == other.scope &&
        key == other.key &&
        kind == other.kind &&
        payload.contentEquals(other.payload) &&
        expiresAt == other.expiresAt

    override fun hashCode(): Int {
        var result = scope.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + expiresAt.hashCode()
        return result
    }
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
@AggregateRoot
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
    init {
        require(ownerActorId.isNotBlank()) { "Comment owner actor ID is required." }
        require(body.isNotBlank()) { "Comment body must not be blank." }
    }

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
     * @return The rejection reason, or `null` when the command is valid.
     */
    fun validateAgainst(comment: SocialComment, actorScope: WorkspaceScope, now: Instant): ReplyRejectionReason? =
        when {
            scope != actorScope || comment.scope != actorScope -> ReplyRejectionReason.WORKSPACE_MISMATCH
            comment.ownerActorId != actorId -> ReplyRejectionReason.ACTOR_MISMATCH
            comment.externalCommentId != parentCommentId -> ReplyRejectionReason.PARENT_NOT_FOUND
            comment.state != ThreadState.OPEN -> ReplyRejectionReason.THREAD_NOT_OPEN
            comment.isExpired(now) -> ReplyRejectionReason.EXPIRED
            else -> null
        }
}

/** Reasons a [ReplyCommand] can be rejected before being forwarded to the provider. */
@ValueObject
enum class ReplyRejectionReason {
    WORKSPACE_MISMATCH,
    ACTOR_MISMATCH,

    /** The command's actor is not the actor executing the request; distinct from [ACTOR_MISMATCH]. */
    EXECUTOR_MISMATCH,
    PARENT_NOT_FOUND,
    THREAD_NOT_OPEN,
    EXPIRED,
    CAPABILITY_DENIED,
}

/**
 * Thrown when a [ReplyCommand] is rejected by [ReplyCommand.validateAgainst] or the capability check.
 */
open class ReplyRejectedException(val reason: ReplyRejectionReason) :
    IllegalArgumentException("Reply rejected: $reason")
