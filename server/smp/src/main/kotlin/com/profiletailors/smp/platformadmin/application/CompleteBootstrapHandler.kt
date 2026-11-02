package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.governance.application.RecordBootstrapTermsConsentCommand
import com.profiletailors.smp.governance.application.RecordBootstrapTermsConsentHandler
import com.profiletailors.smp.identity.application.CurrentTermsVersionProvider
import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.PasswordHasher
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.normalizeUsername
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserRegistered
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenDigest
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.StaleBootstrapTermsException
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CompleteBootstrapCommand(
    val invitationToken: String,
    val password: String,
    val acceptedTermsVersion: String,
) : CommandWithResult<CompleteBootstrapResult>

data object CompleteBootstrapResult

@Service
internal class CompleteBootstrapHandler(
    private val invitationRepository: BootstrapInvitationRepository,
    private val identityRegistrationGateway: IdentityRegistrationGateway,
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val passwordHasher: PasswordHasher,
    private val recordBootstrapTermsConsentHandler: RecordBootstrapTermsConsentHandler,
    private val currentTermsVersionProvider: CurrentTermsVersionProvider,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) : CommandWithResultHandler<CompleteBootstrapCommand, CompleteBootstrapResult> {
    override suspend fun handle(command: CompleteBootstrapCommand): CompleteBootstrapResult {
        require(command.invitationToken.isNotBlank())
        require(command.password.length >= MIN_PASSWORD_LENGTH)
        val currentTermsVersion = currentTermsVersionProvider.currentTermsVersion()
        if (command.acceptedTermsVersion != currentTermsVersion) throw StaleBootstrapTermsException()

        val tokenHash = BootstrapTokenDigest.hash(command.invitationToken)
        val passwordHash = passwordHasher.hash(command.password)
        val registration = registerBootstrapOwner(
            tokenHash = tokenHash,
            passwordHash = passwordHash,
            currentTermsVersion = currentTermsVersion,
        )

        eventPublisher.publish(
            UserRegistered(
                principalId = registration.principalId,
                email = registration.email,
                username = registration.username,
                rawVerificationToken = registration.rawVerificationToken,
            ),
        )
        return CompleteBootstrapResult
    }

    private suspend fun registerBootstrapOwner(
        tokenHash: String,
        passwordHash: String,
        currentTermsVersion: String,
    ): BootstrapRegistration = transactionRunner.runAtomically {
        invitationRepository.lock()
        val invitation = findInvitation(tokenHash)
        val now = clock.instant()
        ensureInvitationCanBeClaimed(invitation, now)
        ensureEmailIsAvailable(invitation.email)

        val principalId = UUID.randomUUID()
        claimInvitation(tokenHash, principalId, now)
        val username = normalizeUsername(null, invitation.email)
        identityRegistrationGateway.createUserIdentity(
            principalId = principalId.toString(),
            subject = "local:${invitation.email}",
            email = invitation.email,
            username = username,
            provider = null,
            displayIdentity = username,
            emailStatus = EmailStatus.PENDING,
        )
        localPasswordCredentialGateway.create(principalId.toString(), passwordHash)
        recordBootstrapTermsConsentHandler.handle(
            RecordBootstrapTermsConsentCommand(
                principalId = principalId,
                termsVersion = currentTermsVersion,
                occurredAt = now,
            ),
        )
        val verificationToken = EmailVerificationTokenHasher.generate(now)
        identityRegistrationGateway.createEmailVerificationToken(
            email = invitation.email,
            tokenHash = verificationToken.tokenHash,
            expiresAt = verificationToken.expiresAt,
        )
        BootstrapRegistration(
            principalId = principalId.toString(),
            email = invitation.email,
            username = username,
            rawVerificationToken = verificationToken.rawToken,
        )
    }

    private suspend fun findInvitation(tokenHash: String): BootstrapInvitation {
        if (invitationRepository.hasActiveOwner()) throw BootstrapUnavailableException()
        return invitationRepository.findByTokenHash(tokenHash) ?: throw BootstrapUnavailableException()
    }

    private fun ensureInvitationCanBeClaimed(invitation: BootstrapInvitation, now: Instant) {
        if (!invitation.canClaim(now)) throw BootstrapUnavailableException()
    }

    private suspend fun ensureEmailIsAvailable(email: String) {
        if (
            principalIdentityLookup.findByEmail(email) != null ||
            localPasswordCredentialGateway.findByEmail(email) != null
        ) {
            throw BootstrapUnavailableException()
        }
    }

    private suspend fun claimInvitation(tokenHash: String, principalId: UUID, now: Instant) {
        if (!invitationRepository.claim(tokenHash, principalId, now)) throw BootstrapUnavailableException()
    }

    private data class BootstrapRegistration(
        val principalId: String,
        val email: String,
        val username: String,
        val rawVerificationToken: String,
    )

    private companion object {
        private const val MIN_PASSWORD_LENGTH = 12
    }
}
