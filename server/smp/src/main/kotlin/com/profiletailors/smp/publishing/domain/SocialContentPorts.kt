package com.profiletailors.smp.publishing.domain

import java.time.Instant

/**
 * Outbound port for the LinkedIn social-content provider.
 *
 * Implementations are responsible for translating LinkedIn API calls into typed social-content responses.
 */
interface SocialContentProvider {
    suspend fun discoverActors(scope: WorkspaceScope, connectionId: String): List<SocialContentActorCandidate> =
        discoverActors(scope, connectionId, connectionId)

    suspend fun discoverActors(
        scope: WorkspaceScope,
        connectionId: String,
        socialAccountId: String,
    ): List<SocialContentActorCandidate> = discoverActors(scope, connectionId)

    /**
     * Fetches a page of posts for an actor.
     *
     * @param actor The actor whose posts are fetched.
     * @param cursor The cursor identifying the page to fetch, or `null` for the first page.
     * @return A page of posts with optional pagination metadata.
     */
    suspend fun fetchPosts(actor: SocialContentActor, cursor: PageCursor?): SocialContentPage<SocialPost>

    suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        pageSize: Int,
    ): SocialContentPage<SocialPost> = fetchPosts(actor, cursor)

    suspend fun fetchPosts(
        actor: SocialContentActor,
        cursor: PageCursor?,
        modifiedSince: Instant?,
    ): SocialContentPage<SocialPost> = fetchPosts(actor, cursor)

    /**
     * Fetches comments associated with a social post.
     *
     * @param actor The actor whose comments are being fetched.
     * @param post The post whose comments are being fetched.
     * @param cursor The cursor identifying the page to fetch, or `null` for the first page.
     * @return A page of social comments with optional pagination metadata.
     */
    suspend fun fetchComments(actor: SocialContentActor, post: SocialPost): SocialContentPage<SocialComment>

    suspend fun fetchComments(
        actor: SocialContentActor,
        post: SocialPost,
        cursor: PageCursor? = null,
        pageSize: Int,
    ): SocialContentPage<SocialComment> = fetchComments(actor, post)

    /**
     * Publishes a reply to a parent comment.
     *
     * @param actor The actor publishing the reply.
     * @param parent The comment receiving the reply.
     * @param body The reply content.
     * @param idempotencyKey The key used to prevent duplicate replies.
     * @return The published reply comment.
     */
    suspend fun reply(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        idempotencyKey: IdempotencyKey,
    ): SocialComment
}

/** Generic paged result returned by provider read operations. */
data class SocialContentPage<T>(val items: List<T>, val nextCursor: PageCursor?, val highWaterMark: Instant? = null)

/** Canonical failure categories raised by social-content provider adapters. */
enum class SocialContentProviderFailure {
    UNAUTHORIZED,
    ROLE_FORBIDDEN,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
}

/** Thrown by provider implementations to surface typed failures surfaced from the LinkedIn API. */
class SocialContentProviderException(
    val failure: SocialContentProviderFailure,
    val statusCode: Int? = null,
    val retryAfter: java.time.Duration? = null,
) : IllegalStateException("Social content provider failure: $failure")

enum class SocialContentSyncSuspension { REAUTH_REQUIRED, ROLE_REQUIRED, PROVIDER_UNAVAILABLE }

fun interface SocialContentSyncFailureRecorder {
    suspend fun record(scope: WorkspaceScope, actorId: String, suspension: SocialContentSyncSuspension)
}

/** Persistent repository for [SocialContentActor] records keyed by workspace and actor id. */
interface SocialContentReader {
    suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost>
    suspend fun findPost(scope: WorkspaceScope, externalPostId: ExternalPostId): SocialPost? = null
}

fun interface SocialContentApprovalEvidenceRepository {
    suspend fun findByWorkspaceAndAccount(workspaceId: String, socialAccountId: String): SocialContentApprovalEvidence?
}

interface SocialContentActorRepository {
    suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor?

    suspend fun findByWorkspaceExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        externalActorId: ProviderActorId,
    ): SocialContentActor?

    suspend fun upsert(actor: SocialContentActor): SocialContentActor
}

/** Persistent repository for imported social posts; identity is workspace + provider + actor + external post id. */
interface SocialContentPostRepository {
    /** Upserts by the workspace/provider/actor/external-post identity. */
    suspend fun upsert(post: SocialPost): SocialPost

    /**
     * Finds a social post by workspace, provider, actor, and external post identifier.
     *
     * @param scope The workspace scope.
     * @param provider The social content provider.
     * @param actorId The actor identifier.
     * @param externalPostId The provider-specific post identifier.
     * @return The matching social post, or `null` if no post is found.
     */
    suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: ExternalPostId,
    ): SocialPost?

    /**
     * Marks posts for an actor as tombstoned when their external IDs are absent from the synchronization result.
     *
     * @param scope The workspace scope for the posts.
     * @param provider The social provider associated with the posts.
     * @param actorId The actor whose posts are synchronized.
     * @param seenExternalIds The external post IDs present in the synchronization result.
     */
    suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<ExternalPostId>,
    )
}

/** Persistent repository for inbound social comments keyed by workspace + external comment id. */
interface SocialContentCommentRepository {
    /**
     * Finds a social comment by workspace and external comment identifier.
     *
     * @param scope The workspace scope in which to search.
     * @param externalCommentId The external identifier of the comment.
     * @return The matching social comment, or `null` if no comment is found.
     */
    suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        externalCommentId: ExternalCommentId,
    ): SocialComment?

    /**
     * Creates or updates a social content comment.
     *
     * @param comment The comment to create or update.
     * @return The persisted social content comment.
     */
    suspend fun upsert(comment: SocialComment): SocialComment
}

interface SocialContentBatchWriter {
    suspend fun persist(posts: Collection<SocialPost>, tombstoneIds: Set<ExternalPostId>, checkpoint: SyncCheckpoint)
}

/** Persistent repository for [SyncCheckpoint] records used to resume incremental syncs. */
interface SocialContentCheckpointRepository {
    /**
     * Finds the synchronization checkpoint for an actor and resource within a workspace.
     *
     * Comment checkpoints are additionally isolated by [postId]. Post checkpoints must use `null`.
     *
     * @param scope The workspace scope.
     * @param actorId The actor identifier.
     * @param resource The synchronized resource.
     * @param postId The post identifier for a comment checkpoint, or `null` for post checkpoints.
     * @return The matching synchronization checkpoint, or `null` if none exists.
     */
    suspend fun find(
        scope: WorkspaceScope,
        actorId: String,
        resource: SyncResource,
        postId: ExternalPostId? = null,
    ): SyncCheckpoint?

    /**
     * Saves a synchronization checkpoint.
     *
     * @param checkpoint The synchronization checkpoint to save.
     * @return The saved synchronization checkpoint.
     */
    suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint
}

/** Persistent repository for in-flight reply commands and their eventual state transitions. */
interface ReplyCommandRepository {
    /**
     * Non-mutating lookup of a previously claimed reply command result.
     *
     * @param command The reply command to look up.
     * @return The stored result for the command's scope and idempotency key, or `null` when never claimed.
     */
    suspend fun find(command: ReplyCommand): ReplyCommandResult?

    /**
     * Atomically persists a new command in [ReplyCommandState.PROCESSING] state.
     *
     * @param command The reply command to claim.
     * @return [ReplyCommandClaim.Claimed] when this caller owns the command, or [ReplyCommandClaim.Existing]
     * when the idempotency key is already claimed.
     */
    suspend fun claim(command: ReplyCommand): ReplyCommandClaim

    /**
     * Persists a reply command result.
     *
     * @param result The reply command result to save.
     * @return The saved reply command result.
     */
    suspend fun save(result: ReplyCommandResult): ReplyCommandResult
}

/** Result returned by [ReplyCommandRepository.claim]. */
sealed interface ReplyCommandClaim {
    data object Claimed : ReplyCommandClaim
    data class Existing(val result: ReplyCommandResult) : ReplyCommandClaim
}

/** Persisted result for an in-flight or completed reply command. */
data class ReplyCommandResult(
    val command: ReplyCommand,
    val state: ReplyCommandState,
    val externalCommentId: ExternalCommentId? = null,
)

/** Lifecycle states for a [ReplyCommandResult] persisted through [ReplyCommandRepository]. */
enum class ReplyCommandState { PROCESSING, SUCCEEDED, FAILED }

/** Resolves whether an actor is allowed to perform a [CapabilityOperation] given the supplied retention. */
interface SocialContentCapabilityResolver {
    /**
     * Determines whether an actor may perform an operation under the specified retention requirements.
     *
     * @param actor The actor requesting the operation.
     * @param operation The capability operation to evaluate.
     * @param retention The retention requirements for the operation.
     * @return The capability decision for the actor and operation.
     */
    fun resolve(
        actor: SocialContentActor,
        operation: CapabilityOperation,
        retention: RetentionRequirements,
    ): CapabilityDecision
}

class DefaultSocialContentCapabilityResolver(private val resolver: DefaultCapabilityResolver) :
    SocialContentCapabilityResolver {
    override fun resolve(
        actor: SocialContentActor,
        operation: CapabilityOperation,
        retention: RetentionRequirements,
    ): CapabilityDecision = resolver.resolve(actor, operation, retention)
}
