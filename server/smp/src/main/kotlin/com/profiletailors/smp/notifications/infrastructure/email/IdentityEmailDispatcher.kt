package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Adapter from the notifications module's [EmailDispatcher] port to the identity
 * module's [EmailSender].
 *
 * The notifications module deliberately defines its own thin port so it does not depend
 * on identity internals; this adapter is the only seam between the two modules.
 */
@Component
internal class IdentityEmailDispatcher(
    private val emailSender: EmailSender,
    @Value("\${app.email.subject-prefix:}")
    private val subjectPrefix: String,
) : EmailDispatcher {

    override suspend fun dispatch(to: String, email: RenderedEmail): EmailDispatchResult {
        val result = emailSender.send(
            to = to,
            subject = "$subjectPrefix${email.subject}",
            message = EmailMessage(text = email.text, html = email.html),
        )
        return if (result.success) {
            EmailDispatchResult.Success
        } else {
            EmailDispatchResult.Failure(result.error ?: "Email send failed without error message")
        }
    }
}
