package com.profiletailors.smp.identity.infrastructure.email

import com.resend.Resend
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the Resend HTTP client and its gateway adapter.
 *
 * Both beans are created only when `app.email.resend.api-key` is present AND non-blank,
 * matching the condition on [ResendEmailSender].
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResendProperties::class)
@ConditionalOnExpression("'\${app.email.resend.api-key:}'.trim().length() > 0")
class ResendEmailConfiguration {

    @Bean
    fun resendEmailGateway(resendProperties: ResendProperties): ResendEmailGateway {
        val client = Resend(resendProperties.apiKey)
        return ResendEmailGateway { options -> client.emails().send(options) }
    }
}
