package com.profiletailors.smp.identity.infrastructure.email

import com.resend.Resend
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the Resend HTTP client and its gateway adapter.
 *
 * Both beans are created only when `app.email.resend.api-key` is present,
 * matching the condition on [ResendEmailSender].
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResendProperties::class)
@ConditionalOnProperty(name = ["app.email.resend.api-key"])
class ResendEmailConfiguration {

    @Bean
    fun resendEmailGateway(resendProperties: ResendProperties): ResendEmailGateway {
        val client = Resend(resendProperties.apiKey)
        return ResendEmailGateway { options -> client.emails().send(options) }
    }
}
