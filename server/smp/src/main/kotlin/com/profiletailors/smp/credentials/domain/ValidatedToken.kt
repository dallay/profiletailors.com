package com.profiletailors.smp.credentials.domain

import com.profiletailors.common.domain.ValueObject
import com.profiletailors.common.domain.context.PrincipalType
import java.time.Instant

@ValueObject
data class ValidatedToken(
    val credentialType: CredentialType,
    val tokenValue: String,
    val subject: String,
    val issuer: String,
    val audience: Set<String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val tokenId: String? = null,
    val claims: Map<String, String> = emptyMap(),
    val principalTypeHint: PrincipalType = PrincipalType.USER,
    val credentialReference: String? = tokenId,
) {
    init {
        require(tokenValue.isNotBlank()) { "Token value must not be blank." }
        require(subject.isNotBlank()) { "Token subject must not be blank." }
    }
}
