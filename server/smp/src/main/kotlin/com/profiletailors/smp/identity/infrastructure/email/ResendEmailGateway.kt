package com.profiletailors.smp.identity.infrastructure.email

import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse

/**
 * Infrastructure-scoped functional interface that wraps the Resend email send operation.
 *
 * This thin adapter exists solely to make [ResendEmailSender] testable without
 * depending on `Emails`, which is a final Java class that cannot be subclassed.
 */
fun interface ResendEmailGateway {
    @Throws(ResendException::class)
    fun send(options: CreateEmailOptions): CreateEmailResponse
}
