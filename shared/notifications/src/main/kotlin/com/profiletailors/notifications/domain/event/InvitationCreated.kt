package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

/**
 * Domain event published by the platform-admin bounded context when a new invitation is
 * minted for a waitlist lead (operator action: invite).
 *
 * Consumed by [com.profiletailors.smp.notifications.infrastructure.email.SendInvitationEmailConsumer]
 * to dispatch an invitation email. The consumer owns the raw token at the time of dispatch
 * and updates the invitation's `deliveryStatus` to `SENT` or `FAILED`.
 *
 * @property invitationId canonical invitation identifier (UUID)
 * @property waitlistEntryId id of the waitlist entry the invitation is for
 * @property operatorPrincipalId id of the platform-operator that issued the invitation
 * @property recipient normalised email address of the invitee
 * @property workspaceName human-readable workspace name used in email copy
 * @property acceptUrl fully-built URL the invitee clicks to accept; the raw token is
 *                  embedded in this URL at construction time
 * @property locale optional BCP-47 locale code (e.g. "en", "es")
 * @property rawToken raw invitation token; only the consumer uses it to render the email
 *                  and then drops it on the floor. MUST NOT be persisted in
 *                  [BaseDomainEvent.toPayload] or audit/log output.
 */
data class InvitationCreated(
    val invitationId: UUID,
    val waitlistEntryId: String,
    val operatorPrincipalId: UUID,
    val recipient: String,
    val workspaceName: String,
    val acceptUrl: String,
    val locale: String?,
    val rawToken: String,
) : BaseDomainEvent()
