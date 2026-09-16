package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.RefreshSessionCookieFactory
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RefreshSessionInfrastructureConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(RefreshSessionInfrastructureConfiguration::class.java)
        .withPropertyValues(
            "app.security.refresh-session.cookie-name=test_refresh",
            "app.security.refresh-session.cookie-path=/api/test",
            "app.security.refresh-session.same-site=Strict",
            "app.security.refresh-session.secure=false",
            "app.security.refresh-session.ttl-seconds=3600",
        )

    @Test
    fun `maps configuration properties to application properties`() {
        contextRunner.run { context ->
            val properties = context.getBean(RefreshSessionProperties::class.java)
            properties.cookieName shouldBe "test_refresh"
            properties.cookiePath shouldBe "/api/test"
            properties.sameSite shouldBe "Strict"
            properties.secure shouldBe false
            properties.ttlSeconds shouldBe 3600L
        }
    }

    @Test
    fun `registers credential beans with expected implementations`() {
        contextRunner.run { context ->
            context.getBean(ApiKeySecretVerifier::class.java).shouldBeInstanceOf<BCryptApiKeySecretVerifier>()
            context.getBean(RefreshTokenHasher::class.java).shouldBeInstanceOf<BCryptRefreshTokenHasher>()
            context.getBean(RefreshSessionCookieFactory::class.java).shouldNotBeNull()
        }
    }
}
