package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant

data class AdminUserSummary(
    val principalId: String,
    val email: String?,
    val displayIdentity: String?,
    val principalType: String,
    val createdAt: Instant,
    val lastAuthenticatedAt: Instant? = null,
    val authenticationMethods: List<String> = emptyList(),
    val workspaceCount: Int = 0,
    val platformRoles: List<String> = emptyList(),
)

data class AdminUserDetail(
    val principalId: String,
    val email: String?,
    val displayIdentity: String?,
    val principalType: String,
    val createdAt: Instant,
    val lastAuthenticatedAt: Instant? = null,
    val authenticationMethods: List<String> = emptyList(),
    val workspaceMemberships: List<AdminWorkspaceMembershipSummary> = emptyList(),
    val platformRoles: List<String> = emptyList(),
)

data class AdminWorkspaceMembershipSummary(
    val workspaceId: String,
    val workspaceName: String,
    val membershipStatus: String,
    val workspaceRoles: List<String>,
    val joinedAt: Instant,
)
