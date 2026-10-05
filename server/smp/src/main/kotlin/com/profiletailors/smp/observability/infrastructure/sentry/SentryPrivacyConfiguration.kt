package com.profiletailors.smp.observability.infrastructure.sentry

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SentryPrivacyConfiguration {

    @Bean
    fun sentryBeforeSend(): SentryOptions.BeforeSendCallback =
        SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
            SentryEventPrivacySanitizer.sanitize(event)
        }
}
