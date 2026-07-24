package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.spring.boot.security.SpringJwtClaimsMapper
import org.springframework.security.oauth2.jwt.Jwt

class SpringJwtValidatedTokenMapper(private val claimsMapper: SpringJwtClaimsMapper = SpringJwtClaimsMapper()) :
    FederatedTokenValidator<Jwt> {
    override suspend fun validate(token: Jwt): ValidatedToken {
        val claims = claimsMapper.map(token)

        return ValidatedToken(
            credentialType = if (claims.principalTypeHint == PrincipalType.SERVICE_ACCOUNT) {
                CredentialType.SERVICE_ACCOUNT
            } else {
                CredentialType.JWT
            },
            tokenValue = claims.tokenValue,
            subject = claims.subject,
            issuer = claims.issuer,
            audience = claims.audience,
            issuedAt = claims.issuedAt,
            expiresAt = claims.expiresAt,
            tokenId = claims.tokenId,
            claims = claims.stringClaims,
            principalTypeHint = claims.principalTypeHint,
            credentialReference = claims.credentialReference,
        )
    }
}
