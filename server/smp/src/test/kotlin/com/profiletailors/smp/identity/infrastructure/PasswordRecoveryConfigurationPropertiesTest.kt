package com.profiletailors.smp.identity.infrastructure

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class PasswordRecoveryConfigurationPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `cleanup defaults preserve thirty days and run daily after warmup`() {
        contextRunner.run { context ->
            val cleanup = context.getBean(PasswordRecoveryConfigurationProperties::class.java).cleanup

            cleanup.retention shouldBe Duration.ofDays(30)
            cleanup.interval shouldBe Duration.ofHours(24)
            cleanup.initialDelay shouldBe Duration.ofMinutes(5)
        }
    }

    @Test
    fun `notification retry policy is bounded and configurable`() {
        contextRunner
            .withPropertyValues(
                "app.identity.password-recovery.notification-retry.max-attempts=4",
                "app.identity.password-recovery.notification-retry.initial-backoff=500ms",
                "app.identity.password-recovery.notification-retry.multiplier=3",
                "app.identity.password-recovery.notification-retry.max-backoff=5s",
            )
            .run { context ->
                val retry = context.getBean(PasswordRecoveryConfigurationProperties::class.java).notificationRetry

                retry.maxAttempts shouldBe 4
                retry.initialBackoff shouldBe Duration.ofMillis(500)
                retry.multiplier shouldBe 3.0
                retry.maxBackoff shouldBe Duration.ofSeconds(5)
            }
    }

    @Test
    fun `cleanup durations bind from configuration`() {
        contextRunner
            .withPropertyValues(
                "app.identity.password-recovery.cleanup.retention=45d",
                "app.identity.password-recovery.cleanup.interval=12h",
                "app.identity.password-recovery.cleanup.initial-delay=1m",
            )
            .run { context ->
                val cleanup = context.getBean(PasswordRecoveryConfigurationProperties::class.java).cleanup

                cleanup.retention shouldBe Duration.ofDays(45)
                cleanup.interval shouldBe Duration.ofHours(12)
                cleanup.initialDelay shouldBe Duration.ofMinutes(1)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordRecoveryConfigurationProperties::class)
    private class TestConfiguration
}
