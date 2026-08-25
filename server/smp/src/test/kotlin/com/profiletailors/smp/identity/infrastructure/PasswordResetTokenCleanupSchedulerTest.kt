package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetTokenCleanup
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PasswordResetTokenCleanupSchedulerTest {

    @Test
    fun `uses the configured retention window to calculate the exclusive cutoff`() = runTest {
        val now = Instant.parse("2026-07-29T12:00:00Z")
        var capturedCutoff: Instant? = null
        val tokenCleanup = PasswordResetTokenCleanup { cutoff ->
            capturedCutoff = cutoff
            3L
        }
        val properties = PasswordRecoveryConfigurationProperties(
            cleanup = PasswordRecoveryConfigurationProperties.Cleanup(retention = Duration.ofDays(30)),
        )
        val scheduler = PasswordResetTokenCleanupScheduler(
            tokenCleanup = tokenCleanup,
            properties = properties,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            observability = com.profiletailors.smp.identity.infrastructure.observability
                .PasswordRecoveryObservability(SimpleMeterRegistry(), ObservationRegistry.NOOP),
        )

        scheduler.runCleanup()
        capturedCutoff shouldBe Instant.parse("2026-06-29T12:00:00Z")
    }

    @Test
    fun `records the deleted row count via observability`() = runTest {
        val meters = SimpleMeterRegistry()
        val now = Instant.parse("2026-07-29T12:00:00Z")
        val tokenCleanup = PasswordResetTokenCleanup { 5L }
        val scheduler = PasswordResetTokenCleanupScheduler(
            tokenCleanup = tokenCleanup,
            properties = PasswordRecoveryConfigurationProperties(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            observability = com.profiletailors.smp.identity.infrastructure.observability
                .PasswordRecoveryObservability(meters, ObservationRegistry.NOOP),
        )

        scheduler.runCleanup()

        meters.counter("identity.password.recovery.cleanup.deleted").count() shouldBe 5.0
    }
}
