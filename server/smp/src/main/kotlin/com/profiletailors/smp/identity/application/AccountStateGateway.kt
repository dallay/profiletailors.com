package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.UserAccountState

interface AccountStateGateway {
    suspend fun findAccountState(principalId: String): UserAccountState?

    suspend fun changeAccountState(
        principalId: String,
        expected: UserAccountState,
        replacement: UserAccountState,
    ): Boolean
}
