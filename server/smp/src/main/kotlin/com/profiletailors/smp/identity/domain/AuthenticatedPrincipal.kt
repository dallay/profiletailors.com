package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.authentication.CredentialType
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.smp.identity.domain.EmailStatus

data class AuthenticatedPrincipal(
    val context: PrincipalContext,
    val credentialType: CredentialType,
    val emailStatus: EmailStatus? = null,
)
