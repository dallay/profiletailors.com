package com.profiletailors.smp.identity.infrastructure

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class RegistrationConfigurationPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `should default to disabled when property not set`() {
        contextRunner.run { context ->
            context.getBean(RegistrationConfigurationProperties::class.java).enabled shouldBe false
        }
    }

    @Test
    fun `should bind explicit enabled value when property set`() {
        contextRunner
            .withPropertyValues("app.identity.registration.enabled=true")
            .run { context ->
                context.getBean(RegistrationConfigurationProperties::class.java).enabled shouldBe true
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RegistrationConfigurationProperties::class)
    private class TestConfiguration
}
