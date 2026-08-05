package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityDecision
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.ReplyRejectedException
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentCommentRepository
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import java.time.Instant

/**
 * Retries provider calls when the failure is a transient rate-limit signal.
 *
 * Non-rate-limited failures rethrow immediately; rate-limited failures retry until [maxAttempts] is
 * exhausted, in which case the original exception propagates.
 */
class SocialContentRetryPolicy(
    private val maxAttempts: Int = 3,
    private val backoff: suspend (attempt: Int) -> Unit = {},
) {
    init {
        require(maxAttempts >= 1) { "Social content retry attempts must be at least 1." }
    }

    /**
     * Executes an operation and retries rate-limited provider failures up to the configured attempt limit.
     *
     * @param operation The suspending operation to execute.
     * @return The result produced by the operation.
     * @throws SocialContentProviderException If the operation fails with a non-rate-limited failure or exhausts the allowed attempts.
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return operation()
            } catch (exception: SocialContentProviderException) {
                if (exception.failure != SocialContentProviderFailure.RATE_LIMITED || attempt == maxAttempts) {
                    throw exception
                }
                backoff(attempt)
                attempt += 1
            }
        }
    }
}

/**
 * Top-level application handler that orchestrates social-content discovery, sync, and tombstoning.
 *
 * Each public entry point enforces workspace capability gates via [capabilityResolver] before invoking
 * the provider, persists the resulting state through the repository ports, and delegates transient
 * retries to [SocialContentRetryPolicy].
 */
class SocialContentFoundationHandlers(
    private val provider: SocialContentProvider,
    private val postRepository: SocialContentPostRepository,
    private val commentRepository: SocialContentCommentRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val capabilityResolver: DefaultCapabilityResolver,
    private val retention: RetentionRequirements,
    private val retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
) {
    /**
     * Discovers organization pages where the actor has administrator privileges.
     *
     * @param actor The actor whose scope and connection are used for discovery.
     * @return The discovered organization pages with administrator privileges.
     * @throws IllegalStateException If the actor is not permitted to discover actors.
     */
    suspend fun discoverActors(actor: SocialContentActor): List<SocialContentActor> {
        requireAllowed(actor, CapabilityOperation.DISCOVER_ACTORS)
        return provider.discoverActors(actor.scope, actor.connectionId)
            .asSequence()
            .filter { it.kind == SocialAccountKind.ORGANIZATION_PAGE }
            .filter { it.roleState == com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN }
            .map { candidate ->
                SocialContentActor(
                    id = candidate.id,
                    scope = actor.scope,
                    connectionId = actor.connectionId,
                    provider = actor.provider,
                    externalActorId = candidate.externalActorId,
                    kind = candidate.kind,
                    displayName = candidate.displayName,
                    roleState = candidate.roleState,
                    grantedScopes = candidate.grantedScopes,
                )
            }
            .toList()
    }

    /**
     * Imports all posts for an actor and updates the corresponding synchronization state.
     *
     * @param actor The actor whose posts are imported.
     * @param now The timestamp used for post expiration and checkpoint updates.
     * @return The imported posts and completed synchronization high-water mark.
     */
    suspend fun importPosts(actor: SocialContentActor, now: Instant): SocialContentPage<SocialPost> {
        requireAllowed(actor, CapabilityOperation.READ_POSTS)
        val checkpoint = checkpointRepository.find(actor.scope, actor.id, SyncResource.POSTS)
        var cursor = checkpoint?.cursor
        var finalPage: SocialContentPage<SocialPost>? = null
        val seenExternalIds = mutableSetOf<ExternalPostId>()
        val importedPosts = mutableListOf<SocialPost>()

        do {
            val page = retryPolicy.execute { provider.fetchPosts(actor, cursor) }
            page.items.forEach { post ->
                seenExternalIds += post.externalPostId
                importedPosts += post.copy(expiresAt = now.plus(retention.activityTtl))
            }
            cursor = page.nextCursor
            finalPage = page
        } while (cursor != null)

        importedPosts.forEach { postRepository.upsert(it) }
        postRepository.tombstoneMissing(
            scope = actor.scope,
            provider = actor.provider,
            actorId = actor.id,
            seenExternalIds = seenExternalIds,
        )
        val completedPage = requireNotNull(finalPage)
        checkpointRepository.save(
            (checkpoint ?: SyncCheckpoint(actor.scope, actor.id, SyncResource.POSTS, null, null, null))
                .advance(null, now, completedPage.highWaterMark),
        )
        return SocialContentPage(importedPosts, null, completedPage.highWaterMark)
    }

    /**
     * Imports comments for a post and persists them with the configured activity expiration time.
     *
     * @param actor The actor whose comments are being imported.
     * @param post The post whose comments are being imported.
     * @param now The time used to calculate comment expiration.
     * @return The imported comment page.
     */
    suspend fun importComments(
        actor: SocialContentActor,
        post: SocialPost,
        now: Instant,
    ): SocialContentPage<SocialComment> {
        requireAllowed(actor, CapabilityOperation.READ_COMMENTS)
        var cursor: com.profiletailors.smp.publishing.domain.PageCursor? = null
        val allComments = mutableListOf<SocialComment>()

        do {
            val page = retryPolicy.execute { provider.fetchComments(actor, post, cursor) }
            page.items.forEach { commentRepository.upsert(it.copy(expiresAt = now.plus(retention.activityTtl))) }
            allComments.addAll(page.items)
            cursor = page.nextCursor
        } while (cursor != null)

        return SocialContentPage(allComments, null)
    }

    /**
     * Ensures the actor is authorized to perform the specified operation.
     *
     * @param actor The actor requesting the operation.
     * @param operation The operation to authorize.
     * @throws IllegalStateException If the operation is denied.
     */
    private fun requireAllowed(actor: SocialContentActor, operation: CapabilityOperation) {
        when (val decision = capabilityResolver.resolve(actor, operation, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> error("Social content operation $operation denied: ${decision.failure}")
        }
    }
}

/**
 * Application handler that dispatches reply commands to the LinkedIn provider with idempotency.
 *
 * The handler claims the reply command atomically through [ReplyCommandRepository.claim], enforces the
 * reply capability gate, executes the provider reply, and persists the resulting state transitions
 * (PROCESSING → SUCCEEDED/FAILED). A second invocation with the same idempotency key returns the
 * previously persisted result without invoking the provider.
 */
class IdempotentReplyHandler(
    private val provider: SocialContentProvider,
    private val commandRepository: ReplyCommandRepository,
    private val capabilityResolver: DefaultCapabilityResolver,
    private val retention: RetentionRequirements,
) {
    /**
     * Handles a reply command idempotently.
     *
     * @param actor The actor submitting the reply.
     * @param parent The comment to which the reply is added.
     * @param body The reply text.
     * @param key The key used to identify duplicate reply requests.
     * @param now The time used to validate the command.
     * @return The result of the existing or newly processed reply command.
     */
    suspend fun handle(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        key: IdempotencyKey,
        now: Instant = Instant.now(),
    ): ReplyCommandResult {
        val command = ReplyCommand(actor.scope, actor.id, parent.externalCommentId, body, key)
        command.validateAgainst(parent, actor.scope, now)
        requireAllowed(actor)
        return when (val claim = commandRepository.claim(command)) {
            is ReplyCommandClaim.Existing -> claim.result
            ReplyCommandClaim.Claimed -> executeReply(actor, parent, command)
        }
    }

    /**
     * Executes a reply command and records its processing, success, or failure state.
     *
     * @param actor The actor sending the reply.
     * @param parent The comment receiving the reply.
     * @param command The validated reply command to execute.
     * @return The completed reply command result.
     * @throws Exception If the provider fails to create the reply.
     */
    private suspend fun executeReply(
        actor: SocialContentActor,
        parent: SocialComment,
        command: ReplyCommand,
    ): ReplyCommandResult {
        val processing = ReplyCommandResult(command, ReplyCommandState.PROCESSING)
        commandRepository.save(processing)
        return try {
            val reply = provider.reply(actor, parent, command.body, command.idempotencyKey)
            commandRepository.save(
                processing.copy(
                    state = ReplyCommandState.SUCCEEDED,
                    externalCommentId = reply.externalCommentId,
                ),
            )
        } catch (exception: Exception) {
            commandRepository.save(processing.copy(state = ReplyCommandState.FAILED))
            throw exception
        }
    }

    /**
     * Ensures the actor is authorized to reply to a comment.
     *
     * @param actor The actor requesting reply capability.
     * @throws ReplyRejectedException If the actor is denied permission to reply.
     */
    private fun requireAllowed(actor: SocialContentActor) {
        when (val decision = capabilityResolver.resolve(actor, CapabilityOperation.REPLY, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> throw ReplyRejectedException(
                com.profiletailors.smp.publishing.domain.ReplyRejectionReason.CAPABILITY_DENIED,
            )
        }
    }
}
