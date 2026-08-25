package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry

/**
 * Context the platform-admin invitation handler needs to send an invitation email.
 *
 * Kept separate from [WaitlistEntry] (which is intentionally email-free at the domain
 * layer for privacy reasons) and exposed only through this narrow port so the invitation
 * flow can compose the email payload without broadening the [WaitlistEntry] surface.
 */
data class WaitlistInvitationContext(val recipientEmail: String, val workspaceName: String, val locale: String?)

/**
 * Read/write port for waitlist entry mutations required by platform administration.
 * Implemented in infrastructure using R2DBC, keeping domain ownership in lead-capture.
 */
interface WaitlistEntryAdminPort {
    suspend fun findById(id: String): WaitlistEntry?
    suspend fun save(entry: WaitlistEntry): WaitlistEntry

    /**
     * Returns the minimal context required by the invitation email flow: the invitee's
     * normalised email and the public workspace name. Implementations MUST scope this
     * lookup to the same authorisation rules that govern [findById]; the returned email
     * addresses MUST be the normalised form.
     */
    suspend fun findInvitationContext(id: String): WaitlistInvitationContext?
}
