package com.profiletailors.smp.credentials.domain

import java.time.Instant

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
)
