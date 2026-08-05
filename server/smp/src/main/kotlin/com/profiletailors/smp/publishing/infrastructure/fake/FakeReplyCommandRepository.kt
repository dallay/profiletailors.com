package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory [ReplyCommandRepository] that de-duplicates claim calls by idempotency key. */
class FakeReplyCommandRepository : ReplyCommandRepository {
    private val mutex = Mutex()
    private val results = mutableMapOf<Pair<WorkspaceScope, String>, ReplyCommandResult>()

    /**
     * Claims a reply command for processing while enforcing idempotency within its workspace.
     *
     * @param command The reply command to claim.
     * @return An existing result when the command was previously claimed; otherwise, a claimed status after storing a processing result.
     */
    override suspend fun claim(command: ReplyCommand): ReplyCommandClaim = mutex.withLock {
        val idempotencyKey = command.scope to command.idempotencyKey.value
        val existing = results[idempotencyKey]
        if (existing != null) {
            ReplyCommandClaim.Existing(existing)
        } else {
            results[idempotencyKey] = ReplyCommandResult(command, ReplyCommandState.PROCESSING)
            ReplyCommandClaim.Claimed
        }
    }

    /**
     * Saves a reply command result for its scope and idempotency key.
     *
     * @param result The reply command result to store.
     * @return The saved reply command result.
     */
    override suspend fun save(result: ReplyCommandResult): ReplyCommandResult = mutex.withLock {
        results[result.command.scope to result.command.idempotencyKey.value] = result
        result
    }
}
