package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailureRecorder
import com.profiletailors.smp.identity.application.PasswordResetNotificationStatus
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetryRecorder
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Duration
import java.time.Instant

@ExtendWith(OutputCaptureExtension::class)
class SendPasswordResetEmailConsumerTest {

    @Test
    fun `dispatches a password reset email with the expected recipient and subject`() = runTest {
        val sender = RecordingEmailSender()
        val consumer = consumer(sender)

        consumer.consume(
            PasswordResetRequested(
                principalId = "user-1",
                email = "user@example.com",
                rawResetToken = "raw-token",
            ),
        )

        sender.messages shouldHaveSize 1
        val message = sender.messages.single()
        message.to shouldBe "user@example.com"
        message.subject shouldBe "Reset your password"
        message.content.text shouldContain "https://app.example.com/reset-password?token=raw-token"
    }

    @Test
    fun `renders password reset email in English and Spanish from the event locale`() = runTest {
        val sender = RecordingEmailSender()
        val consumer = consumer(sender)

        consumer.consume(passwordResetRequested(locale = "en"))
        consumer.consume(passwordResetRequested(locale = "es"))

        sender.messages[0].subject shouldBe "Reset your password"
        sender.messages[0].content.text shouldContain "This link expires in 30 minutes"
        sender.messages[1].subject shouldBe "Restablece tu contraseña"
        sender.messages[1].content.text shouldContain "Este enlace caduca en 30 minutos"
        sender.messages[1].content.html.shouldNotBeNull() shouldContain "Restablece tu contraseña"
    }

    @Test
    fun `temporary failures retry with configured bounded backoff then succeed`() = runTest {
        val sender = SequencedEmailSender(
            EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE),
            EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE),
            EmailSendResult.sent(),
        )
        val delay = RecordingRetryDelay()
        val failures = RecordingFailurePort()
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(
            sender = sender,
            retry = PasswordRecoveryConfigurationProperties.NotificationRetry(
                maxAttempts = 3,
                initialBackoff = Duration.ofSeconds(2),
                multiplier = 2.0,
                maxBackoff = Duration.ofSeconds(10),
            ),
            delay = delay,
            failures = failures,
            telemetry = telemetry,
        )

        consumer.consume(passwordResetRequested())

        sender.attempts shouldBe 3
        delay.delays shouldContainExactly listOf(Duration.ofSeconds(2), Duration.ofSeconds(4))
        failures.records.shouldBeEmpty()
        telemetry.events.map { it.status } shouldContainExactly listOf(
            PasswordResetNotificationStatus.RETRYING,
            PasswordResetNotificationStatus.RETRYING,
            PasswordResetNotificationStatus.SENT,
        )
    }

    @Test
    fun `permanent failure is not retried and stores only safe terminal identity`() = runTest {
        val sender = SequencedEmailSender(
            EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
        )
        val delay = RecordingRetryDelay()
        val failures = RecordingFailurePort()
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(sender = sender, delay = delay, failures = failures, telemetry = telemetry)

        consumer.consume(passwordResetRequested())

        sender.attempts shouldBe 1
        delay.delays.shouldBeEmpty()
        failures.records.single() shouldBe PasswordResetNotificationFailure(
            principalId = "user-1",
            notificationType = "PASSWORD_RESET",
            attempts = 1,
            failedAt = Instant.EPOCH,
            category = EmailFailureCategory.PROVIDER_REJECTED,
        )
        telemetry.events.single().status shouldBe PasswordResetNotificationStatus.FAILED
    }

    @Test
    fun `terminal telemetry is attempted when failure persistence fails`() = runTest {
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(
            sender = SequencedEmailSender(
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
            ),
            failures = ThrowingFailureRecorder(
                org.springframework.dao.DataAccessResourceFailureException("store unavailable"),
            ),
            telemetry = telemetry,
        )

        consumer.consume(passwordResetRequested())

        telemetry.events.single().status shouldBe PasswordResetNotificationStatus.FAILED
    }

    @Test
    fun `unexpected persistence programming errors propagate`() = runTest {
        val consumer = consumer(
            sender = SequencedEmailSender(
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
            ),
            failures = ThrowingFailureRecorder(IllegalStateException("programming error")),
        )

        shouldThrow<IllegalStateException> { consumer.consume(passwordResetRequested()) }
    }

    @Test
    fun `terminal persistence cancellation is recorded after telemetry is attempted`() = runTest {
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(
            sender = SequencedEmailSender(
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
            ),
            failures = ThrowingFailureRecorder(CancellationException("cancelled")),
            telemetry = telemetry,
        )

        runCatching { consumer.consume(passwordResetRequested()) }

        telemetry.events.single().status shouldBe PasswordResetNotificationStatus.FAILED
    }

    @Test
    @Suppress("MaxLineLength")
    fun `exhausted retries records and logs no email token url password or provider text`(output: CapturedOutput) =
        runTest {
            val providerText = "smtp unavailable user@example.com raw-token " +
                "https://app.example.com/reset-password?token=raw-token NewPassword123!"
            val sender = SequencedEmailSender(
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE, providerText),
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE, providerText),
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE, providerText),
            )
            val failures = RecordingFailurePort()
            val telemetry = RecordingTelemetryPort()
            val consumer = consumer(sender = sender, failures = failures, telemetry = telemetry)

            consumer.consume(passwordResetRequested())

            sender.attempts shouldBe 3
            val serializedFailure = failures.records.single().toString()
            val serializedTelemetry = telemetry.events.toString()
            listOf(
                "user@example.com",
                "raw-token",
                "reset-password?token=",
                "NewPassword123!",
                "smtp unavailable",
            ).forEach { sensitive ->
                serializedFailure shouldNotContain sensitive
                serializedTelemetry shouldNotContain sensitive
                output.out shouldNotContain sensitive
            }
            failures.records.single().attempts shouldBe 3
            output.out shouldContain "provider-unavailable"
        }

    @Test
    @Suppress("MaxLineLength")
    fun `provider failure logs exclude email token reset URL password and provider text`(output: CapturedOutput) =
        runTest {
            val sender = FailingEmailSender(
                providerError = "smtp rejected user@example.com raw-token " +
                    "https://app.example.com/reset-password?token=raw-token NewPassword123!",
            )
            val consumer = consumer(sender)

            consumer.consume(
                PasswordResetRequested(
                    principalId = "user-1",
                    email = "user@example.com",
                    rawResetToken = "raw-token",
                ),
            )

            sender.attempts shouldBe 1
            output.out shouldContain "provider-rejected"
            output.out shouldNotContain "user@example.com"
            output.out shouldNotContain "raw-token"
            output.out shouldNotContain "reset-password?token="
            output.out shouldNotContain "NewPassword123!"
            output.out shouldNotContain "smtp rejected"
        }

    private fun consumer(
        sender: EmailSender,
        retry: PasswordRecoveryConfigurationProperties.NotificationRetry =
            PasswordRecoveryConfigurationProperties.NotificationRetry(),
        delay: PasswordResetRetryDelay = RecordingRetryDelay(),
        failures: PasswordResetNotificationFailureRecorder = RecordingFailurePort(),
        telemetry: PasswordResetNotificationTelemetryRecorder = RecordingTelemetryPort(),
    ) = SendPasswordResetEmailConsumer(
        emailSender = sender,
        emailProperties = EmailProperties(publicAppUrl = "https://app.example.com"),
        taskExecutor = ImmediateTaskExecutor,
        retryPolicy = retry,
        retryDelay = delay,
        failureRecorder = failures,
        telemetryRecorder = telemetry,
        clock = java.time.Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC),
    )

    private fun passwordResetRequested(locale: String = "en") = PasswordResetRequested(
        principalId = "user-1",
        email = "user@example.com",
        rawResetToken = "raw-token",
        locale = locale,
    )

    private object ImmediateTaskExecutor : org.springframework.core.task.TaskExecutor {
        override fun execute(task: Runnable) = task.run()
    }

    private class RecordingEmailSender : EmailSender {
        data class Message(val to: String, val subject: String, val content: EmailMessage)
        val messages = mutableListOf<Message>()

        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            messages.add(Message(to, subject, message))
            return EmailSendResult(success = true)
        }
    }

    private class FailingEmailSender(private val providerError: String) : EmailSender {
        var attempts: Int = 0

        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            attempts += 1
            return EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED, providerError)
        }
    }

    private class SequencedEmailSender(vararg outcomes: EmailSendResult) : EmailSender {
        private val outcomes = ArrayDeque(outcomes.toList())
        var attempts: Int = 0

        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            attempts += 1
            return outcomes.removeFirst()
        }
    }

    private class RecordingRetryDelay : PasswordResetRetryDelay {
        val delays = mutableListOf<Duration>()
        override suspend fun await(duration: Duration) {
            delays += duration
        }
    }

    private class RecordingFailurePort : PasswordResetNotificationFailureRecorder {
        val records = mutableListOf<PasswordResetNotificationFailure>()
        override suspend fun record(failure: PasswordResetNotificationFailure) {
            records += failure
        }
    }

    private class ThrowingFailureRecorder(private val failure: RuntimeException) :
        PasswordResetNotificationFailureRecorder {
        override suspend fun record(failure: PasswordResetNotificationFailure): Unit = throw this.failure
    }

    private class RecordingTelemetryPort : PasswordResetNotificationTelemetryRecorder {
        val events = mutableListOf<PasswordResetNotificationTelemetry>()
        override fun record(event: PasswordResetNotificationTelemetry) {
            events += event
        }
    }
}
