package com.profiletailors.smp.identity.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

@Configuration
@EnableConfigurationProperties(LocalJwtProperties::class)
class LocalJwtConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder::class)
    fun reactiveJwtDecoder(
        properties: LocalJwtProperties,
    ): ReactiveJwtDecoder = NimbusReactiveJwtDecoder
        .withSecretKey(properties.secret.effectiveSecret().toSecretKey())
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Bean
    fun jwtEncoder(
        properties: LocalJwtProperties,
    ): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(properties.secret.effectiveSecret().toSecretKey()))

    private fun String.effectiveSecret(): String =
        if (isBlank()) {
            DEV_FALLBACK_SECRET
        } else {
            this
        }

    private fun String.toSecretKey(): SecretKeySpec {
        val bytes = toByteArray(Charsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) {
            "JWT secret must be at least $MIN_SECRET_BYTES bytes (256 bits) for HS256. " +
                "Current length: ${bytes.size} bytes."
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    private companion object {
        private const val MIN_SECRET_BYTES = 32
        private const val DEV_FALLBACK_SECRET = "profiletailors-local-jwt-secret-for-dev-only"
    }
}
