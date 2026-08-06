package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentCommentRepository
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentSyncLimits
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import java.time.Instant

/** Command handler that buffers bounded comment pages before persisting them. */
class SyncSocialCommentsCommandHandler(
    private val provider: SocialContentProvider,
    private val commentRepository: SocialContentCommentRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val capabilityResolver: SocialContentCapabilityResolver,
    private val retention: RetentionRequirements,
    private val syncLimits: SocialContentSyncLimits,
    private val retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
) {
    suspend fun handle(actor: SocialContentActor, post: SocialPost, now: Instant): SocialContentPage<SocialComment> {
        requireSocialContentCapability(actor, CapabilityOperation.READ_COMMENTS, capabilityResolver, retention)
        val checkpoint = checkpointRepository.find(actor.scope, actor.id, SyncResource.COMMENTS)
        val pages = readPages(actor, post, checkpoint?.cursor)
        val comments = pages.flatMap { it.items }.map { it.copy(expiresAt = now.plus(retention.activityTtl)) }
        comments.forEach { commentRepository.upsert(it) }
        val highWaterMark = listOfNotNull(
            pages.mapNotNull { it.highWaterMark }.maxOrNull(),
            comments.map { it.createdAt }.maxOrNull(),
        ).maxOrNull()
        checkpointRepository.save(
            (checkpoint ?: SyncCheckpoint(actor.scope, actor.id, SyncResource.COMMENTS, null, null, null))
                .advance(null, now, highWaterMark),
        )
        return SocialContentPage(comments, null, highWaterMark)
    }

    private suspend fun readPages(
        actor: SocialContentActor,
        post: SocialPost,
        initialCursor: PageCursor?,
    ): List<SocialContentPage<SocialComment>> {
        val pages = mutableListOf<SocialContentPage<SocialComment>>()
        val requestedCursors = mutableSetOf<PageCursor>()
        var cursor: PageCursor? = initialCursor
        while (true) {
            if (cursor != null && !requestedCursors.add(cursor)) {
                throw SocialContentPaginationException(PaginationGuardReason.REPEATED_CURSOR)
            }
            if (pages.size >= syncLimits.maxPages) {
                throw SocialContentPaginationException(PaginationGuardReason.MAX_PAGES_EXCEEDED)
            }
            val page = retryPolicy.execute { provider.fetchComments(actor, post, cursor, syncLimits.pageSize) }
            pages += page
            cursor = page.nextCursor
            if (cursor == null) return pages
        }
    }
}
