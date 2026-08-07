package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

class FakeReplyCommandRepositoryTest {
    @Test
    fun `should atomically claim a key only once under concurrent callers`() = runTest {
        val repository = FakeReplyCommandRepository()
        val command = command()
        val startBarrier = CountDownLatch(1)

        val claims = coroutineScope {
            (1..32).map {
                async(Dispatchers.Default) {
                    startBarrier.await()
                    repository.claim(command)
                }
            }.also { startBarrier.countDown() }.awaitAll()
        }

        claims.count { it == ReplyCommandClaim.Claimed } shouldBe 1
        claims.count { it is ReplyCommandClaim.Existing } shouldBe 31
    }

    @Test
    fun `should return Claimed on first claim and Existing with the previous result on subsequent claims`() = runTest {
        val repository = FakeReplyCommandRepository()
        val command = command()

        repository.claim(command) shouldBe ReplyCommandClaim.Claimed
        repository.claim(command) shouldBe ReplyCommandClaim.Existing(
            ReplyCommandResult(command, ReplyCommandState.PROCESSING),
        )
    }

    @Test
    fun `should find a stored result without creating a processing record`() = runTest {
        val repository = FakeReplyCommandRepository()
        val command = command()

        repository.find(command) shouldBe null
        repository.claim(command) shouldBe ReplyCommandClaim.Claimed
        val saved = ReplyCommandResult(
            command,
            ReplyCommandState.SUCCEEDED,
            externalCommentId = ExternalCommentId("reply-1"),
        )
        repository.save(saved)
        repository.find(command) shouldBe saved
    }

    private fun command() = ReplyCommand(
        scope = WorkspaceScope("workspace-1"),
        actorId = "actor-1",
        parentCommentId = ExternalCommentId("comment-1"),
        body = "Reply",
        idempotencyKey = IdempotencyKey("key-1"),
    )
}
