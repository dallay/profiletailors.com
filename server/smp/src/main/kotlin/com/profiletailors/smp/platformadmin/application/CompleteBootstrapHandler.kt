package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.governance.application.RecordConsentCommand
import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import com.profiletailors.smp.identity.application.CurrentTermsVersionProvider
import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.PasswordHasher
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.normalizeUsername
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserRegistered
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenDigest
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.StaleBootstrapTermsException
import java.time.Clock
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
    private val consentHandler: RecordConsentHandler,
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
        val registration = transactionRunner.runAtomically {
            invitationRepository.lock()
            if (invitationRepository.hasActiveOwner()) throw BootstrapUnavailableException()
            val invitation = invitationRepository.findByTokenHash(tokenHash) ?: throw BootstrapUnavailableException()
            val now = clock.instant()
            if (!invitation.canClaim(now)) throw BootstrapUnavailableException()
            if (
                principalIdentityLookup.findByEmail(invitation.email) != null ||
                localPasswordCredentialGateway.findByEmail(invitation.email) != null
            ) {
                throw BootstrapUnavailableException()
            }

            val principalId = UUID.randomUUID()
            if (!invitationRepository.claim(tokenHash, principalId, now)) throw BootstrapUnavailableException()
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
            consentHandler.handle(
                RecordConsentCommand(
                    scope = ConsentScope.Platform,
                    subjectReference = SubjectReference.user(principalId.toString()),
                    consentType = ConsentType.CONTRACT_ACCEPTANCE,
                    purpose = TERMS_ACCEPTANCE_PURPOSE,
                    policyVersion = currentTermsVersion,
                    source = CONSENT_SOURCE,
                    locale = CONSENT_LOCALE,
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

    private data class BootstrapRegistration(
        val principalId: String,
        val email: String,
        val username: String,
        val rawVerificationToken: String,
    )

    private companion object {
        private const val MIN_PASSWORD_LENGTH = 12
        private const val TERMS_ACCEPTANCE_PURPOSE = "terms.acceptance"
        private const val CONSENT_SOURCE = "bootstrap"
        private const val CONSENT_LOCALE = "en"
    }
}
