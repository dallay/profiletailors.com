package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentSyncLimits
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import java.time.Instant

/** Command for importing bounded post pages for an actor. */
data class SyncSocialPostsCommand(val actor: SocialContentActor, val now: Instant)

/** Command handler that buffers bounded post pages before mutating repositories. */
class ImportSocialPostsHandler(
    private val provider: SocialContentProvider,
    private val postRepository: SocialContentPostRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val capabilityResolver: SocialContentCapabilityResolver,
    private val retention: RetentionRequirements,
    private val syncLimits: SocialContentSyncLimits,
    private val retryPolicy: SocialContentRetryPolicy = SocialContentRetryPolicy(),
) {
    suspend fun handle(command: SyncSocialPostsCommand): SocialContentPage<SocialPost> {
        val actor = command.actor
        val now = command.now
        requireSocialContentCapability(actor, CapabilityOperation.READ_POSTS, capabilityResolver, retention)
        val checkpoint = checkpointRepository.find(actor.scope, actor.id, SyncResource.POSTS)
        val pages = readPages(actor, checkpoint?.cursor)
        val posts = pages
            .flatMap { it.items }
            .map { it.copy(expiresAt = now.plus(retention.activityTtl)) }
            .fold(linkedMapOf<ExternalPostId, SocialPost>()) { latest, post ->
                val existing = latest[post.externalPostId]
                if (existing == null || post.isNewerThan(existing)) {
                    latest[post.externalPostId] = post
                }
                latest
            }
            .values
            .toList()
        val seenExternalIds = posts.mapTo(mutableSetOf()) { it.externalPostId }
        posts.forEach { postRepository.upsert(it) }
        if (checkpoint?.cursor == null) {
            postRepository.tombstoneMissing(actor.scope, actor.provider, actor.id, seenExternalIds)
        }
        val highWaterMark = listOfNotNull(
            checkpoint?.highWaterMark,
            pages.mapNotNull { it.highWaterMark }.maxOrNull(),
            posts.map { it.publishedAt }.maxOrNull(),
        ).maxOrNull()
        checkpointRepository.save(
            (checkpoint ?: SyncCheckpoint(actor.scope, actor.id, SyncResource.POSTS, null, null, null))
                .advance(null, now, highWaterMark),
        )
        return SocialContentPage(posts, null, highWaterMark)
    }

    private suspend fun readPages(
        actor: SocialContentActor,
        initialCursor: PageCursor?,
    ): List<SocialContentPage<SocialPost>> {
        val pages = mutableListOf<SocialContentPage<SocialPost>>()
        val requestedCursors = mutableSetOf<PageCursor>()
        var cursor = initialCursor
        while (true) {
            if (cursor != null && !requestedCursors.add(cursor)) {
                throw SocialContentPaginationException(PaginationGuardReason.REPEATED_CURSOR)
            }
            if (pages.size >= syncLimits.maxPages) {
                throw SocialContentPaginationException(PaginationGuardReason.MAX_PAGES_EXCEEDED)
            }
            val page = retryPolicy.execute { provider.fetchPosts(actor, cursor, syncLimits.pageSize) }
            pages += page
            cursor = page.nextCursor
            if (cursor == null) return pages
        }
    }
}

private fun SocialPost.isNewerThan(other: SocialPost): Boolean = when {
    lastModifiedAt == null -> false
    other.lastModifiedAt == null -> true
    else -> lastModifiedAt.isAfter(other.lastModifiedAt)
}
