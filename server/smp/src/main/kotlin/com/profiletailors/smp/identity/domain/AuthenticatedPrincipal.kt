package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.smp.credentials.domain.CredentialType

data class AuthenticatedPrincipal(
    val context: PrincipalContext,
    val credentialType: CredentialType,
)
