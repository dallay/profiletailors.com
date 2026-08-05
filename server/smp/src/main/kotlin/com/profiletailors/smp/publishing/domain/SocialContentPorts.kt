package com.profiletailors.smp.publishing.domain

import java.time.Instant

interface SocialContentProvider {
    /**
 * Discovers actors available through a social content connection.
 *
 * @param scope The workspace scope for the discovery operation.
 * @param connectionId The identifier of the connection to query.
 * @return The discovered social content actor candidates.
 */
suspend fun discoverActors(scope: WorkspaceScope, connectionId: String): List<SocialContentActorCandidate>
    /**
 * Fetches a page of posts for an actor.
 *
 * @param actor The actor whose posts are fetched.
 * @param cursor The cursor identifying the page to fetch, or `null` for the first page.
 * @return A page of posts with optional pagination metadata.
 */
suspend fun fetchPosts(actor: SocialContentActor, cursor: PageCursor?): SocialContentPage<SocialPost>
    /**
 * Fetches comments associated with a social post.
 *
 * @param actor The actor whose comments are being fetched.
 * @param post The post whose comments are being fetched.
 * @return A page of social comments with optional pagination metadata.
 */
suspend fun fetchComments(actor: SocialContentActor, post: SocialPost): SocialContentPage<SocialComment>
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

data class SocialContentPage<T>(val items: List<T>, val nextCursor: PageCursor?, val highWaterMark: Instant? = null)

enum class SocialContentProviderFailure {
    UNAUTHORIZED,
    ROLE_FORBIDDEN,
    RATE_LIMITED,
}

class SocialContentProviderException(val failure: SocialContentProviderFailure) :
    IllegalStateException("Social content provider failure: $failure")

interface SocialContentActorRepository {
    /**
 * Finds a social content actor within a workspace by its identifier.
 *
 * @param scope The workspace scope in which to search.
 * @param actorId The actor identifier.
 * @return The matching social content actor, or `null` if none exists.
 */
suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor?
}

interface SocialContentPostRepository {
    /**
 * Creates or updates a social post identified by its workspace, provider, actor, and external ID.
 *
 * @param post The social post to create or update.
 * @return The persisted social post.
 */
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

interface SocialContentCheckpointRepository {
    /**
 * Finds the synchronization checkpoint for an actor and resource within a workspace.
 *
 * @param scope The workspace scope.
 * @param actorId The actor identifier.
 * @param resource The synchronized resource.
 * @return The matching synchronization checkpoint, or `null` if none exists.
 */
suspend fun find(scope: WorkspaceScope, actorId: String, resource: SyncResource): SyncCheckpoint?
    /**
 * Saves a synchronization checkpoint.
 *
 * @param checkpoint The synchronization checkpoint to save.
 * @return The saved synchronization checkpoint.
 */
suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint
}

interface ReplyCommandRepository {
    /**
 * Attempts to claim a reply command for processing.
 *
 * @param command The reply command to claim.
 * @return The claim indicating ownership or an existing command result.
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

sealed interface ReplyCommandClaim {
    data object Claimed : ReplyCommandClaim
    data class Existing(val result: ReplyCommandResult) : ReplyCommandClaim
}

data class ReplyCommandResult(
    val command: ReplyCommand,
    val state: ReplyCommandState,
    val externalCommentId: ExternalCommentId? = null,
)

enum class ReplyCommandState { PROCESSING, SUCCEEDED, FAILED }

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
    /**
     * Resolves the actor's capability to perform an operation under the specified retention requirements.
     *
     * @param actor The actor requesting the operation.
     * @param operation The capability operation to evaluate.
     * @param retention The retention requirements applied to the decision.
     * @return The capability decision.
     */
    override fun resolve(
        actor: SocialContentActor,
        operation: CapabilityOperation,
        retention: RetentionRequirements,
    ): CapabilityDecision = resolver.resolve(actor, operation, retention)
}
