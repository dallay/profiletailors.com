package com.profiletailors.smp.bdd.glue

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.media.application.MediaRateLimitRepository
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono
import java.time.Instant

private val BDD_USER_TOKEN_PREFIXES = setOf(
    "e2e-",
    "register-",
    "pending-",
    "verified-",
    "login-",
    "auth-redirect",
    "owner-",
)

private fun isBddUserToken(token: String): Boolean = token == "valid-token" ||
    BDD_USER_TOKEN_PREFIXES.any(token::startsWith)

@TestConfiguration
@ConditionalOnProperty(name = ["bdd.variant"])
class CommonBddTestConfiguration {

    @Bean
    @Primary
    fun bddProviderCatalogPolicyControl(
        connectedSocialChannelReadRepository: ConnectedSocialChannelReadRepository,
    ): BddProviderCatalogPolicyControl = BddProviderCatalogPolicyControl(connectedSocialChannelReadRepository)

    @Bean
    fun bddDatabaseSupport(
        databaseClient: org.springframework.r2dbc.core.DatabaseClient,
        environment: org.springframework.core.env.Environment,
    ): BddDatabaseSupport = BddDatabaseSupport(
        databaseClient = databaseClient,
        liquibaseJdbcUrl = requireNotNull(environment.getProperty("bdd.liquibase.jdbc-url")),
        liquibaseUsername = requireNotNull(environment.getProperty("bdd.liquibase.username")),
        liquibasePassword = environment.getProperty("bdd.liquibase.password") ?: "",
    )

    @Bean
    @Primary
    fun testAuditHook(): CapturingAuditHook = CapturingAuditHook()

    @Bean
    @Primary
    fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()

    @Bean
    @Primary
    fun bddMediaRateLimitRepository(): MediaRateLimitRepository = object : MediaRateLimitRepository {
        override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean = true

        override suspend fun releaseConcurrentUploadSlot(workspaceId: String) = Unit

        override suspend fun tryIncrementHourlyCreationCount(
            workspaceId: String,
            maxPerHour: Int,
        ): MediaRateLimitRepository.RateLimitIncrementResult =
            MediaRateLimitRepository.RateLimitIncrementResult(1, true)
    }

    @Bean
    @Primary
    fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
        when {
            isBddUserToken(token) -> Mono.just(
                Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "subject-123")
                    .claim("iss", "https://issuer.example")
                    .claim("principal_id", "principal-1")
                    .claim(
                        "emailStatus",
                        if (token.startsWith("verified-")) "VERIFIED" else "PENDING",
                    )
                    .claim("preferred_username", "yuniel")
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build(),
            )

            token == "service-account-token" -> Mono.just(
                Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "service-account-subject")
                    .claim("iss", "https://issuer.example")
                    .claim("principal_type", "SERVICE_ACCOUNT")
                    .claim("credential_reference", "svc-cred-1")
                    .claim("jti", "jwt-service-1")
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build(),
            )

            else -> Mono.error(BadJwtException("Invalid token"))
        }
    }
}

class RecordingEmailSender : EmailSender {
    data class Message(val to: String, val subject: String, val content: EmailMessage)

    val messages = mutableListOf<Message>()

    override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
        messages += Message(to, subject, message)
        return EmailSendResult(success = true)
    }

    fun reset() = messages.clear()
}
