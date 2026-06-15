package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.common.domain.context.PrincipalType
import org.springframework.security.oauth2.jwt.Jwt

class SpringJwtValidatedTokenMapper : FederatedTokenValidator<Jwt> {
    override suspend fun validate(token: Jwt): ValidatedToken {
        val principalTypeHint = resolvePrincipalTypeHint(token)
        val credentialReference = token.getClaimAsString("credential_reference") ?: token.id

        return ValidatedToken(
            credentialType = if (principalTypeHint == PrincipalType.SERVICE_ACCOUNT) {
                CredentialType.SERVICE_ACCOUNT
            } else {
                CredentialType.JWT
            },
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
            principalTypeHint = principalTypeHint,
            credentialReference = credentialReference,
        )
    }

    private fun resolvePrincipalTypeHint(token: Jwt): PrincipalType {
        val principalTypeClaim = token.getClaimAsString("principal_type")?.uppercase()
        val actorTypeClaim = token.getClaimAsString("actor_type")?.lowercase()

        return when {
            principalTypeClaim == PrincipalType.SERVICE_ACCOUNT.name -> PrincipalType.SERVICE_ACCOUNT
            actorTypeClaim == "service_account" -> PrincipalType.SERVICE_ACCOUNT
            else -> PrincipalType.USER
        }
    }
}
