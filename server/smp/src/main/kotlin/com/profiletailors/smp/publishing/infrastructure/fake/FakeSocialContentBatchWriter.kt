package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource
import com.profiletailors.smp.publishing.domain.WorkspaceScope

/**
 * Deterministic in-memory seam for application and BDD tests.
 *
 * The fake stages every operation on a private snapshot and publishes the snapshot only after the
 * checkpoint operation succeeds, mirroring the all-or-nothing contract of the production writer.
 */
class FakeSocialContentBatchWriter(
    initialPosts: Collection<SocialPost> = emptyList(),
    private val failOnExternalPostId: ExternalPostId? = null,
    private val failOnCheckpoint: Boolean = false,
) : SocialContentBatchWriter {
    private val postRepository = BatchFakeSocialContentPostRepository(initialPosts)
    private val checkpointRepository = BatchFakeSocialContentCheckpointRepository()
    private val recordedEvents = mutableListOf<String>()

    val events: List<String> get() = recordedEvents.toList()

    override suspend fun persist(
        posts: Collection<SocialPost>,
        tombstoneIds: Set<ExternalPostId>,
        checkpoint: SyncCheckpoint,
    ) {
        require(posts.all { it.scope == checkpoint.scope && it.actorId == checkpoint.actorId }) {
            "Batch posts must belong to the checkpoint workspace and actor."
        }
        require(tombstoneIds.isEmpty() || posts.all { it.scope == checkpoint.scope }) {
            "Batch tombstones must belong to the checkpoint workspace."
        }

        val stagedPosts = postRepository.copy()
        val stagedCheckpoint = checkpointRepository.copy()
        val stagedEvents = mutableListOf<String>()

        posts.forEach { post ->
            if (post.externalPostId == failOnExternalPostId) {
                error("Configured fake batch failure for ${post.externalPostId.value}")
            }
            stagedPosts.upsert(post)
            stagedEvents += "upsert:${post.externalPostId.value}"
        }
        if (tombstoneIds.isNotEmpty()) {
            stagedPosts.tombstoneMissing(
                scope = checkpoint.scope,
                provider = posts.firstOrNull()?.provider ?: checkpoint.provider,
                actorId = checkpoint.actorId,
                seenExternalIds = tombstoneIds,
            )
            stagedEvents += "tombstone-missing"
        }
        if (failOnCheckpoint) {
            error("Configured fake checkpoint failure")
        }
        stagedCheckpoint.save(checkpoint)
        stagedEvents += "checkpoint"

        postRepository.replaceWith(stagedPosts)
        checkpointRepository.replaceWith(stagedCheckpoint)
        recordedEvents += stagedEvents
    }

    suspend fun find(scope: WorkspaceScope, externalPostId: String): SocialPost? =
        postRepository.findByExternalId(scope, externalPostId)

    suspend fun savedCheckpoint(scope: WorkspaceScope, actorId: String): SyncCheckpoint? =
        checkpointRepository.find(scope, actorId, SyncResource.POSTS)
}

private class BatchFakeSocialContentCheckpointRepository : SocialContentCheckpointRepository {
    private val checkpoints = linkedMapOf<CheckpointIdentity, SyncCheckpoint>()

    override suspend fun find(
        scope: WorkspaceScope,
        actorId: String,
        resource: SyncResource,
        postId: ExternalPostId?,
    ): SyncCheckpoint? = checkpoints[CheckpointIdentity(scope, actorId, resource, postId)]

    override suspend fun save(checkpoint: SyncCheckpoint): SyncCheckpoint {
        checkpoints[CheckpointIdentity(checkpoint.scope, checkpoint.actorId, checkpoint.resource, checkpoint.postId)] =
            checkpoint
        return checkpoint
    }

    fun copy() = BatchFakeSocialContentCheckpointRepository().also { it.checkpoints.putAll(checkpoints) }

    fun replaceWith(other: BatchFakeSocialContentCheckpointRepository) {
        checkpoints.clear()
        checkpoints.putAll(other.checkpoints)
    }

    private data class CheckpointIdentity(
        val scope: WorkspaceScope,
        val actorId: String,
        val resource: SyncResource,
        val postId: ExternalPostId?,
    )
}

private class BatchFakeSocialContentPostRepository(initialPosts: Collection<SocialPost>) : SocialContentPostRepository {
    private val records = linkedMapOf<PostIdentity, SocialPost>()

    init {
        initialPosts.forEach { records[PostIdentity(it)] = it }
    }

    override suspend fun upsert(post: SocialPost): SocialPost {
        records[PostIdentity(post)] = post
        return post
    }

    override suspend fun findByWorkspaceAndExternalId(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        externalPostId: ExternalPostId,
    ): SocialPost? = records[PostIdentity(scope, provider, actorId, externalPostId)]

    override suspend fun tombstoneMissing(
        scope: WorkspaceScope,
        provider: SocialProvider,
        actorId: String,
        seenExternalIds: Set<ExternalPostId>,
    ) {
        records.replaceAll { identity, post ->
            if (
                identity.scope == scope &&
                identity.provider == provider &&
                identity.actorId == actorId &&
                identity.externalPostId !in seenExternalIds
            ) {
                post.tombstone(post.expiresAt)
            } else {
                post
            }
        }
    }

    suspend fun findByExternalId(scope: WorkspaceScope, externalPostId: String): SocialPost? =
        records.values.firstOrNull { it.scope == scope && it.externalPostId.value == externalPostId }

    fun providerFor(scope: WorkspaceScope, actorId: String): SocialProvider? =
        records.keys.firstOrNull { it.scope == scope && it.actorId == actorId }?.provider

    fun copy() = BatchFakeSocialContentPostRepository(records.values)

    fun replaceWith(other: BatchFakeSocialContentPostRepository) {
        records.clear()
        records.putAll(other.records)
    }
}

private data class PostIdentity(
    val scope: WorkspaceScope,
    val provider: SocialProvider,
    val actorId: String,
    val externalPostId: ExternalPostId,
) {
    constructor(post: SocialPost) : this(post.scope, post.provider, post.actorId, post.externalPostId)
}
