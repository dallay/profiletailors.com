package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.mail.MailException
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
    } catch (e: MailException) {
        EmailSendResult(success = false, error = e.message)
    }
}
