package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.domain.RegistrationMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class RegistrationConfigurationPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `should default to closed when property not set`() {
        contextRunner.run { context ->
            context.getBean(RegistrationConfigurationProperties::class.java).mode shouldBe RegistrationMode.CLOSED
        }
    }

    @Test
    fun `should bind explicit invite only mode when property set`() {
        contextRunner
            .withPropertyValues("app.identity.registration.mode=INVITE_ONLY")
            .run { context ->
                context.getBean(RegistrationConfigurationProperties::class.java).mode shouldBe
                    RegistrationMode.INVITE_ONLY
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RegistrationConfigurationProperties::class)
    private class TestConfiguration
}
