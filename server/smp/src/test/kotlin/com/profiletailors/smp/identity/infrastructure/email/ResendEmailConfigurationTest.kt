package com.profiletailors.smp.identity.infrastructure.email

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Verifies that [ResendEmailConfiguration] and [ResendProperties] load correctly
 * (or stay absent) depending on whether a non-blank api-key is supplied.
 *
 * Uses [ApplicationContextRunner] so no network call or full Spring context is needed.
 */
class ResendEmailConfigurationTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(ResendEmailConfiguration::class.java)

    @Test
    fun `ResendEmailGateway bean is absent when api-key is not set`() {
        runner.run { ctx ->
            assertThat(ctx).doesNotHaveBean(ResendEmailGateway::class.java)
        }
    }

    @Test
    fun `ResendEmailGateway bean is absent when api-key is blank`() {
        runner
            .withPropertyValues("app.email.resend.api-key=   ")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ResendEmailGateway::class.java)
            }
    }

    @Test
    fun `ResendEmailGateway bean is absent when api-key is empty string`() {
        runner
            .withPropertyValues("app.email.resend.api-key=")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ResendEmailGateway::class.java)
            }
    }

    @Test
    fun `ResendEmailGateway bean is present when api-key is non-blank`() {
        runner
            .withPropertyValues("app.email.resend.api-key=re_test_key_abc123")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ResendEmailGateway::class.java)
            }
    }

    @Test
    fun `ResendProperties binds api-key from property`() {
        runner
            .withPropertyValues("app.email.resend.api-key=re_test_key_xyz")
            .run { ctx ->
                val props = ctx.getBean(ResendProperties::class.java)
                assertThat(props.apiKey).isEqualTo("re_test_key_xyz")
            }
    }
}
