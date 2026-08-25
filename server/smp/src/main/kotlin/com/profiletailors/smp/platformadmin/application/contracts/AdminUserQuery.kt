package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery

interface AdminUserQuery {
    suspend fun list(query: ListAdminUsersQuery): PagedResult<AdminUserSummary>
    suspend fun findById(principalId: String): AdminUserDetail?
    suspend fun findWorkspacesByPrincipalId(principalId: String): List<AdminWorkspaceMembershipSummary>
}
