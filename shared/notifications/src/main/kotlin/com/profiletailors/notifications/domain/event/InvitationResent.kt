package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

/**
 * Domain event published by the platform-admin bounded context when an operator resends an
 * existing invitation. Consumed by
 * [com.profiletailors.smp.notifications.infrastructure.email.SendInvitationEmailConsumer]
 * to dispatch a fresh invitation email.
 *
 * See [InvitationCreated] for the contract on `rawToken`.
 */
data class InvitationResent(
    val invitationId: UUID,
    val waitlistEntryId: String,
    val operatorPrincipalId: UUID,
    val recipient: String,
    val workspaceName: String,
    val acceptUrl: String,
    val locale: String?,
    val rawToken: String,
    val previousInvitationId: UUID,
) : BaseDomainEvent()
