package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
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
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    } catch (_: MailSendException) {
        EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE)
    } catch (_: MailException) {
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    }
}
