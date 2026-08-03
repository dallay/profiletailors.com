package com.profiletailors.smp.mcp.infrastructure.security

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

/**
 * Provides an RSA-based [ReactiveJwtDecoder] for [McpSecurityConfigurationTest].
 * This class has NO `@Configuration` or `@TestConfiguration` annotation so Spring Boot
 * component scanning will NOT auto-discover it. It is activated exclusively via `@Import`
 * on the test class that needs it.
 */
class McpTestJwtDecoderConfig {

    @Bean
    @Primary
    fun mcpTestJwtDecoder(): ReactiveJwtDecoder =
        NimbusReactiveJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build()

    companion object {
        /** Shared RSA key used both for signing test JWTs and for decoder verification. */
        val RSA_KEY: RSAKey =
            RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate()
    }
}
