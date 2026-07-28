package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.core.task.TaskExecutor
import java.util.concurrent.ConcurrentLinkedQueue

@ExtendWith(OutputCaptureExtension::class)
class SendPasswordResetEmailConsumerTest {

    @Test
    fun `dispatches a password reset email with the expected recipient and subject`() = runTest {
        val sender = RecordingEmailSender()
        val properties = EmailProperties(publicAppUrl = "https://app.example.com")
        val consumer = SendPasswordResetEmailConsumer(sender, properties, ImmediateTaskExecutor)

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
            sender,
            EmailProperties(publicAppUrl = "https://app.example.com"),
            executor,
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
        val consumer = SendPasswordResetEmailConsumer(
            sender,
            EmailProperties(publicAppUrl = "https://app.example.com"),
            ImmediateTaskExecutor,
        )

        consumer.consume(passwordResetRequested(locale = "en"))
        consumer.consume(passwordResetRequested(locale = "es"))

        assertThat(sender.messages[0].subject).isEqualTo("Reset your password")
        assertThat(sender.messages[0].content.text).contains("This link expires in 30 minutes")
        assertThat(sender.messages[1].subject).isEqualTo("Restablece tu contraseña")
        assertThat(sender.messages[1].content.text).contains("Este enlace caduca en 30 minutos")
        assertThat(sender.messages[1].content.html).contains("Restablece tu contraseña")
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
            val consumer = SendPasswordResetEmailConsumer(sender, properties, ImmediateTaskExecutor)

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
            return EmailSendResult(success = false, error = providerError)
        }
    }
}
