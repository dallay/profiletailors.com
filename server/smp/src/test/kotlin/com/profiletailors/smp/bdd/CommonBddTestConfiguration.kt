package com.profiletailors.smp.bdd.glue

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailurePort
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetryPort
import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.media.application.MediaRateLimitRepository
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
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

    /**
     * Provides a primary email sender that records messages sent during tests.
     *
     * @return The recording email sender.
     */
    @Bean("smtpEmailSender")
    @Primary
    fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()

    /**
     * Provides a task executor that runs password-reset tasks synchronously.
     *
     * @return The synchronous task executor.
     */
    @Bean("passwordResetEmailTaskExecutor")
    @Primary
    fun passwordResetEmailTaskExecutor(): TaskExecutor = SyncTaskExecutor()

    /**
         * Provides a password reset retry delay with no waiting for BDD tests.
         *
         * @return The configured password reset retry delay.
         */
        @Bean
    @Primary
    fun passwordResetRetryDelay(): com.profiletailors.smp.identity.infrastructure.email.PasswordResetRetryDelay =
        com.profiletailors.smp.identity.infrastructure.email.PasswordResetRetryDelay { }

    /**
     * Provides a recording port for capturing password reset notification failures.
     *
     * @return The recording password reset failure port.
     */
    @Bean
    @Primary
    fun recordingPasswordResetFailurePort(): RecordingPasswordResetFailurePort = RecordingPasswordResetFailurePort()

    /**
         * Provides the password reset notification retry policy for BDD tests.
         *
         * @return A retry policy with no initial backoff.
         */
        @Bean
    @Primary
    fun passwordResetRetryPolicy():
        com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties.NotificationRetry =
        com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties.NotificationRetry(
            initialBackoff = java.time.Duration.ZERO,
        )

    /**
         * Provides a telemetry port that records password reset notification events.
         *
         * @return The recording password reset telemetry port.
         */
        @Bean
    @Primary
    fun recordingPasswordResetTelemetryPort(): RecordingPasswordResetTelemetryPort =
        RecordingPasswordResetTelemetryPort()

    /**
     * Provides a mutable flag for controlling password recovery in BDD tests.
     *
     * @return An enabled password recovery flag.
     */
    @Bean
    fun mutablePasswordRecoveryFlag(): MutablePasswordRecoveryFlag = MutablePasswordRecoveryFlag()

    @Bean("bddPasswordRecoveryEnabled")
    @Primary
    fun bddPasswordRecoveryEnabled(flag: MutablePasswordRecoveryFlag): () -> Boolean = flag::isEnabled

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

class MutablePasswordRecoveryFlag {
    private var enabled: Boolean = true

    fun isEnabled(): Boolean = enabled
    fun enable() {
        enabled = true
    }
    /**
     * Disables password recovery.
     */
    fun disable() {
        enabled = false
    }
}

class RecordingPasswordResetFailurePort : PasswordResetNotificationFailurePort {
    private val recorded = java.util.concurrent.CopyOnWriteArrayList<PasswordResetNotificationFailure>()
    val records: List<PasswordResetNotificationFailure>
        get() = recorded.toList()

    /**
     * Records a password reset notification failure.
     *
     * @param failure The failure to record.
     */
    override suspend fun record(failure: PasswordResetNotificationFailure) {
        recorded += failure
    }

    /**
 * Clears all recorded events.
 */
fun reset() = recorded.clear()
}

class RecordingPasswordResetTelemetryPort : PasswordResetNotificationTelemetryPort {
    private val recorded = java.util.concurrent.CopyOnWriteArrayList<PasswordResetNotificationTelemetry>()
    val events: List<PasswordResetNotificationTelemetry>
        get() = recorded.toList()

    /**
     * Records a password reset notification telemetry event.
     *
     * @param event The telemetry event to record.
     */
    override fun record(event: PasswordResetNotificationTelemetry) {
        recorded += event
    }

    /**
 * Clears all recorded events.
 */
fun reset() = recorded.clear()
}

class RecordingEmailSender : EmailSender {
    data class Message(val to: String, val subject: String, val content: EmailMessage)

    private val recordedMessages = java.util.concurrent.CopyOnWriteArrayList<Message>()
    private val configuredResults = java.util.concurrent.ConcurrentLinkedQueue<EmailSendResult>()
    private val deliverySignal = java.util.concurrent.Semaphore(0)
    var attempts: Int = 0
        private set
    val messages: List<Message>
        get() = recordedMessages.toList()

    /**
     * Records an email delivery attempt and provides its configured result.
     *
     * @param to The recipient email address.
     * @param subject The email subject.
     * @param message The email content.
     * @return The next configured send result, or a successful result when none is configured.
     */
    override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
        attempts += 1
        recordedMessages += Message(to, subject, message)
        deliverySignal.release()
        return configuredResults.poll() ?: EmailSendResult.sent()
    }

    /**
     * Queues temporary provider-unavailable failures for future email send attempts.
     *
     * @param attempts The number of temporary failures to queue.
     */
    fun failTemporarily(attempts: Int) {
        repeat(attempts) {
            configuredResults += EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE)
        }
    }

    /**
 * Waits for an email delivery signal for up to five seconds.
 *
 * @return `true` if a delivery signal is acquired, `false` otherwise.
 */
fun awaitDelivery(): Boolean = deliverySignal.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)

    /**
     * Clears recorded messages, configured results, delivery signals, and the attempt count.
     */
    fun reset() {
        recordedMessages.clear()
        configuredResults.clear()
        deliverySignal.drainPermits()
        attempts = 0
    }
}
