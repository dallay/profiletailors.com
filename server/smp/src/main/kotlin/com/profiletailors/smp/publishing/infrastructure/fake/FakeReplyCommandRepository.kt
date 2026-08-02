package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeReplyCommandRepository : ReplyCommandRepository {
    private val mutex = Mutex()
    private val results = mutableMapOf<Pair<WorkspaceScope, String>, ReplyCommandResult>()

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

    override suspend fun save(result: ReplyCommandResult): ReplyCommandResult = mutex.withLock {
        results[result.command.scope to result.command.idempotencyKey.value] = result
        result
    }
}
