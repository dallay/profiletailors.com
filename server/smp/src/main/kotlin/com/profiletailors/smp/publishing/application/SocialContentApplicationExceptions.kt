package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.CapabilityFailure
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.ReplyRejectedException
import com.profiletailors.smp.publishing.domain.ReplyRejectionReason
import com.profiletailors.smp.publishing.domain.RetentionRequirements
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentCapabilityResolver

/** Raised when a social-content operation is denied before the provider is called. */
class SocialContentCapabilityDeniedException(val operation: CapabilityOperation, val failure: CapabilityFailure) :
    ReplyRejectedException(ReplyRejectionReason.CAPABILITY_DENIED) {
    override val message: String = "Social content operation $operation denied: $failure"
}

/** Reasons a bounded provider read cannot safely complete. */
enum class PaginationGuardReason { REPEATED_CURSOR, MAX_PAGES_EXCEEDED }

/** Raised when a provider pagination sequence violates the configured safety bound. */
class SocialContentPaginationException(val reason: PaginationGuardReason) :
    IllegalStateException("Social content pagination guard failed: $reason")

/** Raised when an idempotency key is reused for a different reply command. */
class ReplyIdempotencyConflictException :
    IllegalArgumentException("Reply idempotency key is already associated with a different command.")

/** Turns a capability decision into the typed application failure used by handlers. */
internal fun requireSocialContentCapability(
    actor: SocialContentActor,
    operation: CapabilityOperation,
    resolver: SocialContentCapabilityResolver,
    retention: RetentionRequirements,
) {
    when (val decision = resolver.resolve(actor, operation, retention)) {
        com.profiletailors.smp.publishing.domain.CapabilityDecision.Allowed -> Unit
        is com.profiletailors.smp.publishing.domain.CapabilityDecision.Denied ->
            throw SocialContentCapabilityDeniedException(operation, decision.failure)
    }
}
