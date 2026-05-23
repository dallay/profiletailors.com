package com.profiletailors.smp.identity.application

interface LocalPasswordCredentialGateway {
    suspend fun create(principalId: String, passwordHash: String)
    suspend fun findByEmail(email: String): LocalPasswordCredentialRecord?
}

data class LocalPasswordCredentialRecord(
    val principalId: String,
    val email: String,
    val username: String?,
    val passwordHash: String,
)
