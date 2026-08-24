package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import java.util.UUID

interface AdminInvitationQuery {
    suspend fun findById(invitationId: UUID): AdminInvitationSummary?
}
