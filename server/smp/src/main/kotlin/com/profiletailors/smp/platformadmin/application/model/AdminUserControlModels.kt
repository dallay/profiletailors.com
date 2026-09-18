package com.profiletailors.smp.platformadmin.application.model

import com.profiletailors.smp.identity.domain.UserAccountState

data class UserControlResult(val principalId: String, val accountState: UserAccountState, val revokedSessionCount: Int)

data class UserSessionsRevokeResult(val principalId: String, val revokedSessionCount: Int)
