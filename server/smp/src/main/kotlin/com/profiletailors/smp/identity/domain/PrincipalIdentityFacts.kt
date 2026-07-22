package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.context.PrincipalType

data class PrincipalIdentityFacts(
    val principalId: String,
    val principalType: PrincipalType,
    val subject: String,
    val provider: String?,
    val displayIdentity: String?,
    val email: String?,
    val username: String?,
    val emailStatus: EmailStatus? = null,
)
