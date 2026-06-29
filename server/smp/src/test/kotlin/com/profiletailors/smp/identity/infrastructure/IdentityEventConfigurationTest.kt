package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.UserRegistered
import com.profiletailors.smp.identity.infrastructure.email.SendVerificationEmailConsumer
import com.profiletailors.spring.boot.bus.event.EventConfiguration
import com.profiletailors.spring.boot.bus.event.EventEmitter
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class IdentityEventConfigurationTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            IdentityEventConfiguration::class.java,
            SendVerificationEmailConsumer::class.java,
        )
        .withBean(RecordingEmailSender::class.java)

    @Test
    fun `uses one publisher and dispatches user registered through active consumer`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(EventPublisher::class.java)
            assertThat(context).hasSingleBean(EventEmitter::class.java)
            assertThat(context).hasSingleBean(EventConfiguration::class.java)

            val publisher = context.getBean(EventEmitter::class.java)
            val emailSender = context.getBean(RecordingEmailSender::class.java)

            runBlocking {
                publisher.publish(
                    UserRegistered(
                        principalId = "user-1",
                        email = "yuniel@example.com",
                        username = "yuniel",
                        rawVerificationToken = "newest-token",
                    ),
                )
            }

            assertThat(emailSender.lastRecipient).isEqualTo("yuniel@example.com")
            assertThat(emailSender.lastBody).contains("newest-token")
        }
    }

    private class RecordingEmailSender : EmailSender {
        var lastRecipient: String? = null
        var lastBody: String? = null

        override suspend fun send(to: String, subject: String, body: String): EmailSendResult {
            lastRecipient = to
            lastBody = body
            return EmailSendResult(success = true)
        }
    }
}
