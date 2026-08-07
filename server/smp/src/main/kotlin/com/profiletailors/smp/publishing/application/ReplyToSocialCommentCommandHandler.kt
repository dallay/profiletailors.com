package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityDecision
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.ReplyRejectedException
import com.profiletailors.smp.publishing.domain.ReplyRejectionReason
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import java.time.Instant

/** Explicit application command for one idempotent social-comment reply. */
data class ReplyToSocialCommentCommand(
    val actor: SocialContentActor,
    val parent: SocialComment,
    val command: ReplyCommand,
    val now: Instant,
)

/** Command handler that validates and executes one idempotent social-comment reply. */
class ReplyToSocialCommentCommandHandler(
    private val provider: SocialContentProvider,
    private val commandRepository: ReplyCommandRepository,
    private val capabilityResolver: SocialContentCapabilityResolver,
    private val retention: RetentionRequirements,
) {
    suspend fun handle(command: ReplyToSocialCommentCommand): ReplyCommandResult {
        val actor = command.actor
        val parent = command.parent
        val reply = command.command
        requireExecutorMatch(actor, reply)
        commandRepository.find(reply)?.let { existing -> return matchingResult(existing, reply) }
        reply.validateAgainst(parent, actor.scope, command.now)?.let { throw ReplyRejectedException(it) }
        when (val decision = capabilityResolver.resolve(actor, CapabilityOperation.REPLY, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> throw SocialContentCapabilityDeniedException(
                CapabilityOperation.REPLY,
                decision.failure,
            )
        }
        return when (val claim = commandRepository.claim(reply)) {
            is ReplyCommandClaim.Existing -> matchingResult(claim.result, reply)
            ReplyCommandClaim.Claimed -> execute(actor, parent, reply)
        }
    }

    private fun requireExecutorMatch(actor: SocialContentActor, reply: ReplyCommand) {
        if (reply.actorId != actor.id) throw ReplyRejectedException(ReplyRejectionReason.EXECUTOR_MISMATCH)
    }

    private fun matchingResult(result: ReplyCommandResult, command: ReplyCommand): ReplyCommandResult =
        result.takeIf { it.command == command } ?: throw ReplyIdempotencyConflictException()

    suspend fun handle(
        actor: SocialContentActor,
        parent: SocialComment,
        command: ReplyCommand,
        now: Instant = Instant.now(),
    ): ReplyCommandResult = handle(ReplyToSocialCommentCommand(actor, parent, command, now))

    private suspend fun execute(
        actor: SocialContentActor,
        parent: SocialComment,
        command: ReplyCommand,
    ): ReplyCommandResult {
        val processing = ReplyCommandResult(command, ReplyCommandState.PROCESSING)
        return try {
            val reply = provider.reply(actor, parent, command.body, command.idempotencyKey)
            commandRepository.save(
                processing.copy(
                    state = ReplyCommandState.SUCCEEDED,
                    externalCommentId = reply.externalCommentId,
                ),
            )
        } catch (exception: Exception) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            commandRepository.save(processing.copy(state = ReplyCommandState.FAILED))
            throw exception
        }
    }
}
