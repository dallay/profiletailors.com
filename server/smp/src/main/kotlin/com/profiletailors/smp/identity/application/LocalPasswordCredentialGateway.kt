package com.profiletailors.smp.identity.application

interface LocalPasswordCredentialGateway {
    suspend fun create(principalId: String, passwordHash: String, passwordAlgorithm: String)
    suspend fun findByEmail(email: String): LocalPasswordCredentialRecord?
    suspend fun updatePassword(principalId: String, passwordHash: String, passwordAlgorithm: String)
}

data class LocalPasswordCredentialRecord(
    val principalId: String,
    val email: String,
    val username: String?,
    val passwordHash: String,
    val passwordAlgorithm: String? = null,
)
