package com.profiletailors.spring.boot.security

import com.profiletailors.common.domain.context.PrincipalType
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

data class JwtTokenClaims(
    val tokenValue: String,
    val subject: String,
    val issuer: String,
    val audience: Set<String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val tokenId: String?,
    val stringClaims: Map<String, String>,
    val principalTypeHint: PrincipalType,
    val credentialReference: String?,
)

class SpringJwtClaimsMapper {
    fun map(token: Jwt): JwtTokenClaims {
        val principalTypeHint = resolvePrincipalTypeHint(token)

        return JwtTokenClaims(
            tokenValue = token.tokenValue,
            subject = token.subject,
            issuer = token.issuer?.toString() ?: throw IllegalArgumentException("JWT missing 'iss' claim"),
            audience = token.audience?.toSet() ?: emptySet(),
            issuedAt = token.issuedAt,
            expiresAt = token.expiresAt,
            tokenId = token.id,
            stringClaims = token.claims
                .filterValues { value -> value is String }
                .mapValues { (_, value) -> value as String },
            principalTypeHint = principalTypeHint,
            credentialReference = token.getClaimAsString("credential_reference") ?: token.id,
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
