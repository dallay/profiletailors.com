package com.profiletailors.smp.publishing.domain

import java.time.Instant

interface SocialContentProvider {
    suspend fun discoverActors(scope: WorkspaceScope, connectionId: String): List<SocialContentActorCandidate>
    suspend fun fetchPosts(actor: SocialContentActor, cursor: PageCursor?): SocialContentPage<SocialPost>
    suspend fun fetchComments(actor: SocialContentActor, post: SocialPost): SocialContentPage<SocialComment>
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
    suspend fun findByWorkspaceAndId(scope: WorkspaceScope, actorId: String): SocialContentActor?
}

interface SocialContentPostRepository {
    /** Upserts by the workspace/provider/actor/external-post identity. */
    suspend fun upsert(post: SocialPost): SocialPost
    suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: ExternalPostId,
    ): SocialPost?
    suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<ExternalPostId>,
    )
}

interface SocialContentCommentRepository {
    suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        externalCommentId: ExternalCommentId,
    ): SocialComment?
    suspend fun upsert(comment: SocialComment): SocialComment
}

interface SocialContentCheckpointRepository {
    suspend fun find(scope: WorkspaceScope, actorId: String, resource: SyncResource): SyncCheckpoint?
    suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint
}

interface ReplyCommandRepository {
    suspend fun claim(command: ReplyCommand): ReplyCommandClaim
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
