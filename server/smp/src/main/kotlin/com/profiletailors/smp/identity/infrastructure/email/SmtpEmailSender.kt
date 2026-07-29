package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
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
    } catch (e: MailAuthenticationException) {
        log.debug("SMTP authentication failed", e)
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    } catch (e: MailSendException) {
        log.debug("SMTP send failed: {}", e.message)
        when (categorizeMailSendException(e)) {
            EmailFailureCategory.PROVIDER_UNAVAILABLE ->
                EmailSendResult.temporaryFailure(EmailFailureCategory.PROVIDER_UNAVAILABLE)
            EmailFailureCategory.PROVIDER_REJECTED ->
                EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
            else -> EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
        }
    } catch (e: MailException) {
        log.debug("Mail exception", e)
        EmailSendResult.permanentFailure(EmailFailureCategory.PROVIDER_REJECTED)
    }
}

private fun categorizeMailSendException(e: MailSendException): EmailFailureCategory {
    val message = e.message?.lowercase() ?: ""
    val causeMessage = (e.cause as? Exception)?.message?.lowercase() ?: ""
    return when {
        isTransientSmtpCondition(message, causeMessage) -> EmailFailureCategory.PROVIDER_UNAVAILABLE
        isPermanentRejection(message, causeMessage) -> EmailFailureCategory.PROVIDER_REJECTED
        else -> EmailFailureCategory.PROVIDER_UNAVAILABLE
    }
}

private fun isTransientSmtpCondition(message: String, causeMessage: String): Boolean {
    val indicators = listOf(
        "connection refused",
        "connection timed out",
        "connection reset",
        "timeout",
        "network",
        "unreachable",
        "temporary failure",
        "service unavailable",
        "try again later",
    )
    return indicators.any { message.contains(it) || causeMessage.contains(it) }
}

private fun isPermanentRejection(message: String, causeMessage: String): Boolean {
    val indicators = listOf(
        "authentication",
        "invalid credentials",
        "not authorized",
        "user unknown",
        "recipient rejected",
        "invalid address",
        "mailbox unavailable",
        "user not found",
        "no such user",
    )
    return indicators.any { message.contains(it) || causeMessage.contains(it) }
}
