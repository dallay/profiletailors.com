package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.DefaultCapabilityResolver
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialContentPage
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import com.profiletailors.smp.publishing.domain.SocialContentSyncFailureRecorder
import com.profiletailors.smp.publishing.domain.SocialContentSyncSuspension
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

class SocialContentSyncStateException(val actorId: String, cause: Throwable) :
    IllegalStateException("Social content sync failed for actor $actorId", cause)

class SocialContentSyncHandler(
    private val provider: SocialContentProvider,
    private val postRepository: SocialContentPostRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val capabilityResolver: DefaultCapabilityResolver,
    private val retention: RetentionRequirements,
    private val gates: SocialContentFeatureGates,
    private val maxPages: Int = 10,
    private val maxRetries: Int = 3,
    private val overlap: Duration = Duration.ZERO,
    private val backoff: suspend (Int, Duration) -> Unit = { _, duration -> delay(duration.toMillis()) },
    private val failureRecorder: SocialContentSyncFailureRecorder = SocialContentSyncFailureRecorder { _, _, _ -> },
    private val batchWriter: SocialContentBatchWriter,
) {
    init {
        require(maxPages >= 1) { "Social content sync page limit must be at least 1." }
        require(maxRetries >= 0) { "Social content sync retry limit cannot be negative." }
        require(!overlap.isNegative) { "Social content sync overlap cannot be negative." }
    }
    suspend fun importPosts(actor: SocialContentActor, now: Instant): SocialContentPage<SocialPost> {
        require(gates.importEnabled) { "Social content import is disabled." }
        authorizeImport(actor)
        val checkpoint = checkpointRepository.find(actor.scope, actor.id, SyncResource.POSTS)
        return try {
            val imported = collectPosts(actor, checkpoint, now)
            persist(actor, checkpoint, imported, now)
            SocialContentPage(imported.posts.values.toList(), null, imported.highWaterMark)
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (failure is SocialContentProviderException) {
                failureRecorder.record(actor.scope, actor.id, failure.suspension())
            }
            throw SocialContentSyncStateException(actor.id, failure)
        }
    }

    private fun authorizeImport(actor: SocialContentActor) {
        when (val decision = capabilityResolver.resolve(actor, CapabilityOperation.READ_POSTS, retention)) {
            com.profiletailors.smp.publishing.domain.CapabilityDecision.Allowed -> Unit
            is com.profiletailors.smp.publishing.domain.CapabilityDecision.Denied ->
                throw SocialContentCapabilityDeniedException(decision.failure)
        }
    }

    private suspend fun collectPosts(
        actor: SocialContentActor,
        checkpoint: SyncCheckpoint?,
        now: Instant,
    ): ImportedPosts {
        val posts = linkedMapOf<ExternalPostId, SocialPost>()
        val seen = mutableSetOf<ExternalPostId>()
        var cursor = checkpoint?.cursor
        var highWaterMark = checkpoint?.highWaterMark
        val requestedSince = checkpoint?.highWaterMark?.minus(overlap)
        var pages = 0
        do {
            pages++
            val page = fetchWithRetry(actor, cursor, requestedSince)
            highWaterMark = listOfNotNull(highWaterMark, page.highWaterMark).maxOrNull()
            page.items.forEach { post ->
                seen += post.externalPostId
                val normalized = normalize(actor, post, now)
                posts[post.externalPostId] = newer(posts[post.externalPostId], normalized)
                highWaterMark = listOfNotNull(
                    highWaterMark,
                    post.lastModifiedAt,
                    post.publishedAt,
                ).maxOrNull()
            }
            cursor = page.nextCursor
        } while (cursor != null && pages < maxPages)
        check(cursor == null) { "Social content sync exceeded page limit $maxPages" }
        return ImportedPosts(posts, seen, highWaterMark)
    }

    private suspend fun normalize(actor: SocialContentActor, post: SocialPost, now: Instant): SocialPost {
        val existing = postRepository.findByWorkspaceAndExternalId(
            scope = actor.scope,
            provider = actor.provider,
            actorId = actor.id,
            externalPostId = post.externalPostId,
        )
        return post.copy(
            expiresAt = now.plus(retention.activityTtl),
            origin = existing?.origin ?: post.origin,
            localPublicationId = existing?.localPublicationId ?: post.localPublicationId,
        )
    }

    private suspend fun persist(
        actor: SocialContentActor,
        checkpoint: SyncCheckpoint?,
        imported: ImportedPosts,
        now: Instant,
    ) {
        batchWriter.persist(
            posts = imported.posts.values,
            tombstoneIds = if (checkpoint == null) imported.seen else emptySet(),
            checkpoint = (checkpoint ?: newCheckpoint(actor))
                .advance(null, now, imported.highWaterMark),
        )
    }

    private fun newCheckpoint(actor: SocialContentActor): SyncCheckpoint = SyncCheckpoint(
        scope = actor.scope,
        actorId = actor.id,
        resource = SyncResource.POSTS,
        cursor = null,
        highWaterMark = null,
        lastSuccessfulAt = null,
        provider = actor.provider,
    )

    private data class ImportedPosts(
        val posts: Map<ExternalPostId, SocialPost>,
        val seen: Set<ExternalPostId>,
        val highWaterMark: Instant?,
    )

    private fun newer(previous: SocialPost?, candidate: SocialPost): SocialPost =
        if (previous == null || candidate.lastModifiedAt.orOlderThan(previous.lastModifiedAt)) candidate else previous

    private suspend fun fetchWithRetry(
        actor: SocialContentActor,
        cursor: com.profiletailors.smp.publishing.domain.PageCursor?,
        requestedSince: Instant?,
    ): SocialContentPage<SocialPost> {
        var attempt = 0
        while (true) {
            try {
                return provider.fetchPosts(actor, cursor, requestedSince)
            } catch (failure: SocialContentProviderException) {
                if (failure.failure != SocialContentProviderFailure.RATE_LIMITED || attempt >= maxRetries) {
                    throw failure
                }
                val wait = failure.retryAfter ?: Duration.ofSeconds(2L shl attempt)
                backoff(attempt, wait)
                attempt++
            }
        }
    }
}

private fun Instant?.orOlderThan(other: Instant?): Boolean = when {
    this == null -> false
    other == null -> true
    else -> !this.isBefore(other)
}

private fun SocialContentProviderException.suspension(): SocialContentSyncSuspension = when (failure) {
    SocialContentProviderFailure.UNAUTHORIZED -> SocialContentSyncSuspension.REAUTH_REQUIRED
    SocialContentProviderFailure.ROLE_FORBIDDEN -> SocialContentSyncSuspension.ROLE_REQUIRED
    SocialContentProviderFailure.PROVIDER_UNAVAILABLE -> SocialContentSyncSuspension.PROVIDER_UNAVAILABLE
    SocialContentProviderFailure.RATE_LIMITED -> SocialContentSyncSuspension.PROVIDER_UNAVAILABLE
}
