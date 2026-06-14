package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent

/**
 * Domain event emitted when a new user registers with local credentials.
 *
 * This event is published by [RegisterUserHandler] after persisting the
 * user identity. It is consumed asynchronously by [SendVerificationEmailConsumer]
 * to dispatch the verification email.
 *
 * @property principalId the unique identifier of the newly created principal
 * @property email the normalized email address of the registrant
 * @property username the display name of the registrant
 * @property rawVerificationToken the unhashed verification token (in-memory only, never persisted)
 */
data class UserRegistered(
    val principalId: String,
    val email: String,
    val username: String?,
    val rawVerificationToken: String,
) : BaseDomainEvent()
