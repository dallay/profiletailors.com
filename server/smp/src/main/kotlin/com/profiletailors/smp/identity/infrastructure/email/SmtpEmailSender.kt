package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

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

    override suspend fun send(to: String, subject: String, body: String): EmailSendResult = try {
        val message = SimpleMailMessage().apply {
            setFrom(emailProperties.sender)
            setTo(to)
            setSubject("${emailProperties.verificationSubjectPrefix} $subject")
            setText(body)
        }
        mailSender.send(message)
        EmailSendResult(success = true)
    } catch (e: MailException) {
        EmailSendResult(success = false, error = e.message)
    }
}
