package com.profiletailors.smp.publishing.infrastructure.fake

import com.profiletailors.smp.publishing.domain.ExternalCommentId
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeReplyCommandRepositoryTest {
    @Test
    fun `should return Claimed on first claim and Existing with the previous result on subsequent claims`() = runTest {
        val repository = FakeReplyCommandRepository()
        val command = command()

        repository.claim(command) shouldBe ReplyCommandClaim.Claimed
        repository.claim(command) shouldBe ReplyCommandClaim.Existing(
            ReplyCommandResult(command, ReplyCommandState.PROCESSING),
        )
    }

    private fun command() = ReplyCommand(
        scope = WorkspaceScope("workspace-1"),
        actorId = "actor-1",
        parentCommentId = ExternalCommentId("comment-1"),
        body = "Reply",
        idempotencyKey = IdempotencyKey("key-1"),
    )
}
