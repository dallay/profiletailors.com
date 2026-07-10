package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.EmailStatus

data class CurrentUserProfile(
    val principalId: String,
    val email: String?,
    val username: String?,
    val displayIdentity: String,
    val emailStatus: EmailStatus?,
)
