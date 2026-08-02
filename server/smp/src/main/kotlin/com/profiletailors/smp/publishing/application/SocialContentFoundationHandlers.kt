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

class SocialContentRetryPolicy(
    private val maxAttempts: Int = 3,
    private val backoff: suspend (attempt: Int) -> Unit = {},
) {
    init {
        require(maxAttempts >= 1) { "Social content retry attempts must be at least 1." }
    }

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

class SocialContentFoundationHandlers(
    private val provider: SocialContentProvider,
    private val postRepository: SocialContentPostRepository,
    private val commentRepository: SocialContentCommentRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val capabilityResolver: DefaultCapabilityResolver,
    private val retention: RetentionRequirements,
    private val retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
) {
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

    suspend fun importComments(
        actor: SocialContentActor,
        post: SocialPost,
        now: Instant,
    ): SocialContentPage<SocialComment> {
        requireAllowed(actor, CapabilityOperation.READ_COMMENTS)
        val page = retryPolicy.execute { provider.fetchComments(actor, post) }
        page.items.forEach { commentRepository.upsert(it.copy(expiresAt = now.plus(retention.activityTtl))) }
        return page
    }

    private fun requireAllowed(actor: SocialContentActor, operation: CapabilityOperation) {
        when (val decision = capabilityResolver.resolve(actor, operation, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> error("Social content operation $operation denied: ${decision.failure}")
        }
    }
}

class IdempotentReplyHandler(
    private val provider: SocialContentProvider,
    private val commandRepository: ReplyCommandRepository,
    private val capabilityResolver: DefaultCapabilityResolver,
    private val retention: RetentionRequirements,
) {
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

    private fun requireAllowed(actor: SocialContentActor) {
        when (val decision = capabilityResolver.resolve(actor, CapabilityOperation.REPLY, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> throw ReplyRejectedException(
                com.profiletailors.smp.publishing.domain.ReplyRejectionReason.CAPABILITY_DENIED,
            )
        }
    }
}
