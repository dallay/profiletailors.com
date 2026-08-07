package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.SocialContentBatchWriter
import com.profiletailors.smp.publishing.domain.SocialContentCheckpointRepository
import com.profiletailors.smp.publishing.domain.SocialContentPostRepository
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SyncCheckpoint
import com.profiletailors.smp.publishing.domain.SyncResource

/**
 * Production batch seam for schema-supported social-content persistence.
 *
 * Actor and approval-evidence storage are intentionally not part of this adapter: the current
 * schema has no complete actor table or social-content approval-evidence mapping. Callers must pass
 * an already-authorized actor and cannot use this seam to bypass the access gate.
 *
 * The checkpoint table also has no provider column. This adapter therefore relies on the current
 * LinkedIn-only provider contract and does not claim provider-portable checkpoint persistence.
 */
class R2dbcSocialContentBatchWriter(
    private val postRepository: SocialContentPostRepository,
    private val checkpointRepository: SocialContentCheckpointRepository,
    private val transactionRunner: AtomicTransactionRunner,
) : SocialContentBatchWriter {
    override suspend fun persist(
        posts: Collection<SocialPost>,
        tombstoneIds: Set<ExternalPostId>,
        checkpoint: SyncCheckpoint,
    ) {
        require(checkpoint.resource == SyncResource.POSTS) {
            "Social content batch checkpoints must target posts."
        }
        require(posts.all { it.scope == checkpoint.scope && it.actorId == checkpoint.actorId }) {
            "Batch posts must belong to the checkpoint workspace and actor."
        }
        require(tombstoneIds.isEmpty() || posts.all { it.scope == checkpoint.scope }) {
            "Batch tombstones require a workspace-scoped post batch."
        }

        transactionRunner.runAtomically<Unit> {
            posts.forEach { postRepository.upsert(it) }
            if (tombstoneIds.isNotEmpty()) {
                postRepository.tombstoneMissing(
                    scope = checkpoint.scope,
                    provider = checkpoint.provider,
                    actorId = checkpoint.actorId,
                    seenExternalIds = tombstoneIds,
                )
            }
        }
        checkpointRepository.save(checkpoint)
    }
}
