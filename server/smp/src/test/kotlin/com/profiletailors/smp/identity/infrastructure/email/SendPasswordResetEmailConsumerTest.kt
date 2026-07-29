package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailurePort
import com.profiletailors.smp.identity.application.PasswordResetNotificationStatus
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetryPort
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.core.task.TaskExecutor
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

@ExtendWith(OutputCaptureExtension::class)
class SendPasswordResetEmailConsumerTest {

    @Test
    fun `dispatches a password reset email with the expected recipient and subject`() = runTest {
        val sender = RecordingEmailSender()
        val properties = EmailProperties(publicAppUrl = "https://app.example.com")
        val consumer = consumer(sender)

        consumer.consume(
            PasswordResetRequested(
                principalId = "user-1",
                email = "user@example.com",
                rawResetToken = "raw-token",
            ),
        )

        assertThat(sender.messages).hasSize(1)
        val message = sender.messages.single()
        assertThat(message.to).isEqualTo("user@example.com")
        assertThat(message.subject).isEqualTo("Reset your password")
        assertThat(message.content.text).contains("https://app.example.com/reset-password?token=raw-token")
    }

    @Test
    fun `consume schedules delivery without awaiting the email provider`() = runTest {
        val sender = BlockingEmailSender()
        val executor = RecordingTaskExecutor()
        val consumer = SendPasswordResetEmailConsumer(
            emailSender = sender,
            emailProperties = EmailProperties(publicAppUrl = "https://app.example.com"),
            taskExecutor = executor,
            retryPolicy = PasswordRecoveryConfigurationProperties.NotificationRetry(),
            retryDelay = RecordingRetryDelay(),
            failurePort = RecordingFailurePort(),
            telemetryPort = RecordingTelemetryPort(),
            clock = java.time.Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC),
        )

        consumer.consume(passwordResetRequested())

        assertThat(sender.started.isCompleted).isFalse()
        assertThat(executor.tasks).hasSize(1)
        executor.runNext()
        sender.started.await()
        assertThat(sender.completed.isCompleted).isFalse()
        sender.release.complete(Unit)
        sender.completed.await()
    }

    @Test
    fun `renders password reset email in English and Spanish from the event locale`() = runTest {
        val sender = RecordingEmailSender()
        val consumer = consumer(sender)

        consumer.consume(passwordResetRequested(locale = "en"))
        consumer.consume(passwordResetRequested(locale = "es"))

        assertThat(sender.messages[0].subject).isEqualTo("Reset your password")
        assertThat(sender.messages[0].content.text).contains("This link expires in 30 minutes")
        assertThat(sender.messages[1].subject).isEqualTo("Restablece tu contraseña")
        assertThat(sender.messages[1].content.text).contains("Este enlace caduca en 30 minutos")
        assertThat(sender.messages[1].content.html).contains("Restablece tu contraseña")
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

        assertThat(sender.attempts).isEqualTo(3)
        assertThat(delay.delays).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4))
        assertThat(failures.records).isEmpty()
        assertThat(telemetry.events.map { it.status }).containsExactly(
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

        assertThat(sender.attempts).isEqualTo(1)
        assertThat(delay.delays).isEmpty()
        assertThat(failures.records.single()).isEqualTo(
            PasswordResetNotificationFailure(
                principalId = "user-1",
                notificationType = "PASSWORD_RESET",
                attempts = 1,
                failedAt = Instant.EPOCH,
                category = EmailFailureCategory.PROVIDER_REJECTED,
            ),
        )
        assertThat(telemetry.events.single().status).isEqualTo(PasswordResetNotificationStatus.FAILED)
    }

    @Test
    fun `terminal telemetry is attempted when failure persistence fails`() = runTest {
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(
            sender = SequencedEmailSender(
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
            ),
            failures = ThrowingFailurePort(IllegalStateException("store unavailable")),
            telemetry = telemetry,
        )

        consumer.consume(passwordResetRequested())

        assertThat(telemetry.events.single().status).isEqualTo(PasswordResetNotificationStatus.FAILED)
    }

    @Test
    fun `terminal persistence cancellation is propagated after telemetry is attempted`() = runTest {
        val telemetry = RecordingTelemetryPort()
        val consumer = consumer(
            sender = SequencedEmailSender(
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED),
            ),
            failures = ThrowingFailurePort(CancellationException("cancelled")),
            telemetry = telemetry,
        )

        assertThrows<CancellationException> { consumer.consume(passwordResetRequested()) }
        assertThat(telemetry.events.single().status).isEqualTo(PasswordResetNotificationStatus.FAILED)
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

            assertThat(sender.attempts).isEqualTo(3)
            val serializedFailure = failures.records.single().toString()
            val serializedTelemetry = telemetry.events.toString()
            listOf(
                "user@example.com",
                "raw-token",
                "reset-password?token=",
                "NewPassword123!",
                "smtp unavailable",
            ).forEach { sensitive ->
                assertThat(serializedFailure).doesNotContain(sensitive)
                assertThat(serializedTelemetry).doesNotContain(sensitive)
                assertThat(output.out).doesNotContain(sensitive)
            }
            assertThat(failures.records.single().attempts).isEqualTo(3)
            assertThat(output.out).contains("provider-unavailable")
        }

    @Test
    @Suppress("MaxLineLength")
    fun `provider failure logs exclude email token reset URL password and provider text`(output: CapturedOutput) =
        runTest {
            val sender = FailingEmailSender(
                providerError = "smtp rejected user@example.com raw-token " +
                    "https://app.example.com/reset-password?token=raw-token NewPassword123!",
            )
            val properties = EmailProperties(publicAppUrl = "https://app.example.com")
            val consumer = consumer(sender)

            // The consumer must NOT rethrow — failed deliveries are absorbed.
            consumer.consume(
                PasswordResetRequested(
                    principalId = "user-1",
                    email = "user@example.com",
                    rawResetToken = "raw-token",
                ),
            )

            assertThat(sender.attempts).isEqualTo(1)
            assertThat(output.out).contains("provider-rejected")
            assertThat(output.out).doesNotContain("user@example.com")
            assertThat(output.out).doesNotContain("raw-token")
            assertThat(output.out).doesNotContain("reset-password?token=")
            assertThat(output.out).doesNotContain("NewPassword123!")
            assertThat(output.out).doesNotContain("smtp rejected")
        }

    private fun consumer(
        sender: EmailSender,
        retry: PasswordRecoveryConfigurationProperties.NotificationRetry =
            PasswordRecoveryConfigurationProperties.NotificationRetry(),
        delay: PasswordResetRetryDelay = RecordingRetryDelay(),
        failures: PasswordResetNotificationFailurePort = RecordingFailurePort(),
        telemetry: PasswordResetNotificationTelemetryPort = RecordingTelemetryPort(),
    ) = SendPasswordResetEmailConsumer(
        emailSender = sender,
        emailProperties = EmailProperties(publicAppUrl = "https://app.example.com"),
        taskExecutor = ImmediateTaskExecutor,
        retryPolicy = retry,
        retryDelay = delay,
        failurePort = failures,
        telemetryPort = telemetry,
        clock = java.time.Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC),
    )

    private fun passwordResetRequested(locale: String = "en") = PasswordResetRequested(
        principalId = "user-1",
        email = "user@example.com",
        rawResetToken = "raw-token",
        locale = locale,
    )

    private object ImmediateTaskExecutor : TaskExecutor {
        override fun execute(task: Runnable) = task.run()
    }

    private class RecordingTaskExecutor : TaskExecutor {
        val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun execute(task: Runnable) {
            tasks += task
        }
        fun runNext() = Thread(tasks.remove()).start()
    }

    private class BlockingEmailSender : EmailSender {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            started.complete(Unit)
            release.await()
            completed.complete(Unit)
            return EmailSendResult(success = true)
        }
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

    private class RecordingFailurePort : PasswordResetNotificationFailurePort {
        val records = mutableListOf<PasswordResetNotificationFailure>()
        override suspend fun record(failure: PasswordResetNotificationFailure) {
            records += failure
        }
    }

    private class ThrowingFailurePort(private val failure: RuntimeException) : PasswordResetNotificationFailurePort {
        override suspend fun record(failure: PasswordResetNotificationFailure): Unit = throw this.failure
    }

    private class RecordingTelemetryPort : PasswordResetNotificationTelemetryPort {
        val events = mutableListOf<PasswordResetNotificationTelemetry>()
        override fun record(event: PasswordResetNotificationTelemetry) {
            events += event
        }
    }
}
