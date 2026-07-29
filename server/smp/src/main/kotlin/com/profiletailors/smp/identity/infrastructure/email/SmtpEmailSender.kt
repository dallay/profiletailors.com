package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import jakarta.mail.SendFailedException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

/**
 * SMTP implementation of [EmailSender] using Spring Boot's [JavaMailSender].
 *
 * Active only when `spring.mail.host` is configured. Takes priority over
 * [MockEmailSender] via [@Primary].
 */
@Component
@Primary
@ConditionalOnProperty(name = ["spring.mail.host"])
class SmtpEmailSender(private val mailSender: JavaMailSender, private val emailProperties: EmailProperties) :
    EmailSender {

    private val log = LoggerFactory.getLogger(SmtpEmailSender::class.java)

    /**
     * Sends an email using plain text or HTML content.
     *
     * @param to The recipient email address.
     * @param subject The email subject.
     * @param message The email content, including optional HTML markup.
     * @return The result of the send operation, including its success or failure category.
     */
    override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult = try {
        if (message.html == null) {
            mailSender.send(
                SimpleMailMessage().apply {
                    setFrom(emailProperties.sender)
                    setTo(to)
                    setSubject("${emailProperties.verificationSubjectPrefix} $subject")
                    setText(message.text)
                },
            )
        } else {
            val mimeMessage = mailSender.createMimeMessage()
            MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name(),
            ).apply {
                setFrom(emailProperties.sender)
                setTo(to)
                setSubject("${emailProperties.verificationSubjectPrefix} $subject")
                setText(message.text, message.html)
            }
            mailSender.send(mimeMessage)
        }
        EmailSendResult(success = true)
    } catch (_: MailAuthenticationException) {
        log.debug("SMTP authentication failed")
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    } catch (e: MailSendException) {
        val category = categorizeMailSendException(e)
        log.debug("SMTP send failed with category {}", category.name.lowercase())
        when (category) {
            EmailFailureCategory.PROVIDER_UNAVAILABLE ->
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE)
            EmailFailureCategory.PROVIDER_TIMEOUT ->
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_TIMEOUT)
            EmailFailureCategory.PROVIDER_REJECTED ->
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
            else -> EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
        }
    } catch (_: MailException) {
        log.debug("SMTP mail failure classified as provider-rejected")
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    }
}

private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

private fun categorizeMailSendException(exception: MailSendException): EmailFailureCategory {
    val causes = sequenceOf(exception)
        .plus(exception.messageExceptions.asSequence())
        .plus(exception.failedMessages.values.asSequence())
        .filterNotNull()
        .flatMap { it.causeChain() }
        .toList()

    return when {
        causes.any { it is SocketTimeoutException } -> EmailFailureCategory.PROVIDER_TIMEOUT
        causes.any {
            it is ConnectException || it is SocketException || it is UnknownHostException
        } -> EmailFailureCategory.PROVIDER_UNAVAILABLE
        causes.any { it is SendFailedException } -> EmailFailureCategory.PROVIDER_REJECTED
        else -> EmailFailureCategory.PROVIDER_REJECTED
    }
}
