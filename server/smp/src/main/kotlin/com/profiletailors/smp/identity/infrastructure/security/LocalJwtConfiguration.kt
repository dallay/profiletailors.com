package com.profiletailors.smp.identity.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

@Configuration
class LocalJwtConfiguration {

    @Bean
    fun reactiveJwtDecoder(
        @Value("\${app.security.local-jwt.secret}") secret: String,
    ): ReactiveJwtDecoder = NimbusReactiveJwtDecoder
        .withSecretKey(secret.toSecretKey())
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Bean
    fun jwtEncoder(
        @Value("\${app.security.local-jwt.secret}") secret: String,
    ): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secret.toSecretKey()))

    private fun String.toSecretKey() = SecretKeySpec(toByteArray(Charsets.UTF_8), "HmacSHA256")
}
