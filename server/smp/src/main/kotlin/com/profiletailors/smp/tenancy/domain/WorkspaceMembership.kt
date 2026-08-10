package com.profiletailors.smp.tenancy.domain

import com.profiletailors.common.domain.DomainEntity
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus

@DomainEntity
data class WorkspaceMembership(
    override val id: String = "",
    override val workspaceId: String,
    override val principalId: String,
    override val principalType: PrincipalType,
    override val status: WorkspaceMembershipStatus,
    override val roleKeys: Set<String> = emptySet(),
) : WorkspaceMembershipSnapshot
