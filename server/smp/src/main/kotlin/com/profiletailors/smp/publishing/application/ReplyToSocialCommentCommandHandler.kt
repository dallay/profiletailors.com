package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityDecision
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.IdempotencyKey
import com.profiletailors.smp.publishing.domain.ReplyCommand
import com.profiletailors.smp.publishing.domain.ReplyCommandClaim
import com.profiletailors.smp.publishing.domain.ReplyCommandRepository
import com.profiletailors.smp.publishing.domain.ReplyCommandResult
import com.profiletailors.smp.publishing.domain.ReplyCommandState
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialComment
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver
import com.profiletailors.smp.publishing.domain.SocialContentProvider
import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import java.time.Instant

/** Command handler that validates and executes one idempotent social-comment reply. */
class ReplyToSocialCommentCommandHandler(
    private val provider: SocialContentProvider,
    private val commandRepository: ReplyCommandRepository,
    private val capabilityResolver: SocialContentCapabilityResolver,
    private val retention: RetentionRequirements,
) {
    suspend fun handle(
        actor: SocialContentActor,
        parent: SocialComment,
        body: String,
        key: IdempotencyKey,
        now: Instant = Instant.now(),
    ): ReplyCommandResult {
        val command = ReplyCommand(actor.scope, actor.id, parent.externalCommentId, body, key)
        command.validateAgainst(parent, actor.scope, now)
        when (val decision = capabilityResolver.resolve(actor, CapabilityOperation.REPLY, retention)) {
            CapabilityDecision.Allowed -> Unit
            is CapabilityDecision.Denied -> throw SocialContentCapabilityDeniedException(
                CapabilityOperation.REPLY,
                decision.failure,
            )
        }
        return when (val claim = commandRepository.claim(command)) {
            is ReplyCommandClaim.Existing -> claim.result.takeIf { it.command == command }
                ?: throw ReplyIdempotencyConflictException()
            ReplyCommandClaim.Claimed -> execute(actor, parent, command)
        }
    }

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
            val failure = (exception as? SocialContentProviderException)?.failure
            commandRepository.save(processing.copy(state = ReplyCommandState.FAILED, failure = failure))
            throw exception
        }
    }
}
