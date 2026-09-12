package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import java.util.UUID

fun interface AdminInvitationQuery {
    suspend fun findById(invitationId: UUID): AdminInvitationSummary?
}
