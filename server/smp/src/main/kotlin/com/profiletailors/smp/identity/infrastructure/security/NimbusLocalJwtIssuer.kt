package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.IssuedAccessToken
import com.profiletailors.smp.identity.application.LocalJwtIssuer
import com.profiletailors.smp.identity.domain.EmailStatus
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant

class NimbusLocalJwtIssuer(
    private val jwtEncoder: JwtEncoder,
    private val issuer: String,
    private val ttlSeconds: Long,
) : LocalJwtIssuer {

    override fun issue(
        principalId: String,
        subject: String,
        email: String,
        username: String?,
        emailStatus: EmailStatus,
        issuedAt: Instant,
    ): IssuedAccessToken {
        val expiresAt = issuedAt.plusSeconds(ttlSeconds)
        val claimsBuilder = JwtClaimsSet.builder()
            .claim("iss", issuer)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(subject)
            .claim("principal_id", principalId)
            .claim("principal_type", PrincipalType.USER.name)
            .claim("email", email)
            .claim("emailStatus", emailStatus.name)

        if (!username.isNullOrBlank()) {
            claimsBuilder.claim("preferred_username", username)
        }

        val jwt = jwtEncoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claimsBuilder.build(),
            ),
        )

        return IssuedAccessToken(
            value = jwt.tokenValue,
            expiresInSeconds = ttlSeconds,
        )
    }
}
