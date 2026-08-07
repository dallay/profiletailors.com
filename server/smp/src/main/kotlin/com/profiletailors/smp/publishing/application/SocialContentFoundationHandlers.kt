package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentCommentRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentSyncLimits
import com.profiletailors.smp.publishing.domain.SocialPost
import java.time.Instant

/** Backward-compatible façade used by the existing foundation tests while callers migrate to dedicated handlers. */
class SocialContentFoundationHandlers(
    provider: SocialContentProvider,
    postRepository: SocialContentPostRepository,
    commentRepository: SocialContentCommentRepository,
    checkpointRepository: SocialContentCheckpointRepository,
    capabilityResolver: SocialContentCapabilityResolver,
    retention: RetentionRequirements,
    retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
    syncLimits: SocialContentSyncLimits = SocialContentSyncLimits(
        pageSize = SocialContentSyncLimits.DEFAULT_PAGE_SIZE,
        maxPages = SocialContentSyncLimits.DEFAULT_MAX_PAGES,
    ),
) {
    private val discovery = DiscoverSocialContentActorsHandler(provider, capabilityResolver, retention)
    private val posts = ImportSocialPostsHandler(
        provider,
        postRepository,
        checkpointRepository,
        capabilityResolver,
        retention,
        syncLimits,
        retryPolicy,
    )
    private val comments = ImportSocialCommentsHandler(
        provider,
        commentRepository,
        checkpointRepository,
        capabilityResolver,
        retention,
        syncLimits,
        retryPolicy,
    )

    suspend fun discoverActors(actor: SocialContentActor) = discovery.handle(DiscoverSocialContentActorsQuery(actor))

    suspend fun importPosts(actor: SocialContentActor, now: Instant) = posts.handle(SyncSocialPostsCommand(actor, now))

    suspend fun importComments(actor: SocialContentActor, post: SocialPost, now: Instant) =
        comments.handle(SyncSocialCommentsCommand(actor, post, now))
}

/** Compatibility alias for the former direct reply handler while adopting the dedicated CQRS name. */
class IdempotentReplyHandler(
    provider: SocialContentProvider,
    commandRepository: ReplyCommandRepository,
    capabilityResolver: SocialContentCapabilityResolver,
    retention: RetentionRequirements,
) {
    private val delegate = ReplyToSocialCommentCommandHandler(
        provider,
        commandRepository,
        capabilityResolver,
        retention,
    )

    suspend fun handle(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        key: IdempotencyKey,
        now: Instant = Instant.now(),
    ) = delegate.handle(
        ReplyToSocialCommentCommand(
            actor,
            parent,
            ReplyCommand(actor.scope, actor.id, parent.externalCommentId, body, key),
            now,
        ),
    )
}
