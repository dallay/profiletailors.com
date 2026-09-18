package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminDirectInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminDirectInvitationsQuery
import java.util.UUID

interface AdminInvitationQuery {
    suspend fun findById(invitationId: UUID): AdminInvitationSummary?
    suspend fun list(query: ListAdminDirectInvitationsQuery): PagedResult<AdminDirectInvitationSummary>
}
