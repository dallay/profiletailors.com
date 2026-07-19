package com.profiletailors.smp.identity.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class RegistrationConfigurationPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `registration defaults disabled`() {
        contextRunner.run { context ->
            assertThat(context.getBean(RegistrationConfigurationProperties::class.java).enabled).isFalse()
        }
    }

    @Test
    fun `registration binds explicit enabled value`() {
        contextRunner
            .withPropertyValues("app.identity.registration.enabled=true")
            .run { context ->
                assertThat(context.getBean(RegistrationConfigurationProperties::class.java).enabled).isTrue()
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RegistrationConfigurationProperties::class)
    private class TestConfiguration
}
