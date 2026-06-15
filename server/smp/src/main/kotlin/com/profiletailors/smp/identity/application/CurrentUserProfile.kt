package com.profiletailors.smp.identity.application

data class CurrentUserProfile(
    val principalId: String,
    val email: String?,
    val username: String?,
    val displayIdentity: String,
)
