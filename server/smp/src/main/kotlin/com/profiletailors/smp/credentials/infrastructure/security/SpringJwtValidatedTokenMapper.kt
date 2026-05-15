package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import org.springframework.security.oauth2.jwt.Jwt

class SpringJwtValidatedTokenMapper : FederatedTokenValidator<Jwt> {
    override suspend fun validate(token: Jwt): ValidatedToken =
        ValidatedToken(
            credentialType = CredentialType.JWT,
            tokenValue = token.tokenValue,
            subject = token.subject,
            issuer = token.issuer?.toString().orEmpty(),
            audience = token.audience?.toSet() ?: emptySet(),
            issuedAt = token.issuedAt,
            expiresAt = token.expiresAt,
            tokenId = token.id,
            claims = token.claims
                .filterValues { value -> value is String }
                .mapValues { (_, value) -> value as String },
        )
}
