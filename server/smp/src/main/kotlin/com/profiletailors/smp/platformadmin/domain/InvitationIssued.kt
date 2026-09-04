package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

/**
 * Domain event published when a platform operator issues a new invitation to a waitlist lead.
 *
 * This event contains the non-sensitive invitation data required by notification delivery.
 *
 * This replaces the previous `InvitationCreated` event as part of establishing clean architectural
 * boundaries between the Invitation lifecycle (ACTIVE/ACCEPTED/EXPIRED/REVOKED) and Notification
 * delivery lifecycle (PENDING/SENT/FAILED).
 *
 * @property invitationId canonical invitation identifier (UUID)
 * @property recipientEmail normalized email address of the invitee
 * @property workspaceName human-readable workspace name used in email copy
 * @property locale optional BCP-47 locale code (e.g. "en", "es") for template selection
 * @property rawToken ephemeral bearer token used by the notification consumer to build acceptance URLs
 */
data class InvitationIssued(
    val invitationId: UUID,
    val recipientEmail: String,
    val workspaceName: String,
    val locale: String?,
    val rawToken: String,
) : BaseDomainEvent()
