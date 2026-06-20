package com.profiletailors.smp.identity.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.profiletailors.smp.identity.domain.EmailStatus
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import java.time.Instant

class NimbusLocalJwtIssuerTest {

    private val secret = "integration-test-local-jwt-secret-1234567890"
    private val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    private val jwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))
    private val jwtDecoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
    private val issuer = NimbusLocalJwtIssuer(
        jwtEncoder = jwtEncoder,
        issuer = "http://localhost/profiletailors-local",
        ttlSeconds = 3600,
    )

    @Test
    fun `issue includes emailStatus pending claim`() {
        val token = issuer.issue(
            principalId = "user-1",
            subject = "local:pending@example.com",
            email = "pending@example.com",
            username = "pending",
            emailStatus = EmailStatus.PENDING,
            issuedAt = Instant.now(),
        )

        val jwt = jwtDecoder.decode(token.value).block()
            ?: error("Failed to decode JWT")

        assertEquals("PENDING", jwt.getClaim<String>("emailStatus"))
        assertEquals("pending@example.com", jwt.getClaim<String>("email"))
        assertEquals("pending", jwt.getClaim<String>("preferred_username"))
    }

    @Test
    fun `issue includes emailStatus verified claim`() {
        val token = issuer.issue(
            principalId = "user-2",
            subject = "local:verified@example.com",
            email = "verified@example.com",
            username = null,
            emailStatus = EmailStatus.VERIFIED,
            issuedAt = Instant.now(),
        )

        val jwt = jwtDecoder.decode(token.value).block()
            ?: error("Failed to decode JWT")

        assertEquals("VERIFIED", jwt.getClaim<String>("emailStatus"))
        assertEquals("verified@example.com", jwt.getClaim<String>("email"))
        assertEquals("user-2", jwt.getClaim<String>("principal_id"))
    }
}
