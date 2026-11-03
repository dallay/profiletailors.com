package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.ActiveBootstrapInvitationException
import com.profiletailors.smp.platformadmin.domain.AdminActorKind
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationStatus
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenGenerator
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.ExistingBootstrapAccountException
import com.profiletailors.smp.platformadmin.domain.PlatformOwnerAlreadyExistsException
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class IssueBootstrapInvitationCommand(
    val email: String,
    val confirmedEmail: String,
    val replacementConfirmed: Boolean,
)

data class BootstrapInvitationLink(val email: String, val link: String, val expiresAt: java.time.Instant)

fun interface BootstrapInvitationIssuer {
    suspend fun handle(command: IssueBootstrapInvitationCommand): BootstrapInvitationLink
}

class IssueBootstrapInvitationHandler(
    private val repository: BootstrapInvitationRepository,
    private val identityLookup: PrincipalIdentityLookup,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val tokenGenerator: BootstrapTokenGenerator,
    private val clock: Clock,
    adminPublicOrigin: String,
) : BootstrapInvitationIssuer {
    private val adminPublicOrigin = adminPublicOrigin.trimEnd('/')

    init {
        require(this.adminPublicOrigin.startsWith("https://") || this.adminPublicOrigin.startsWith("http://localhost"))
    }

    override suspend fun handle(command: IssueBootstrapInvitationCommand): BootstrapInvitationLink {
        val email = command.email.trim().lowercase()
        val confirmedEmail = command.confirmedEmail.trim().lowercase()
        validateCommand(email, confirmedEmail)

        val (token, expiresAt) = issueInvitation(email, command.replacementConfirmed)

        return BootstrapInvitationLink(
            email = email,
            link = "$adminPublicOrigin/bootstrap#${token.rawToken}",
            expiresAt = expiresAt,
        )
    }

    private fun validateCommand(email: String, confirmedEmail: String) {
        require(EMAIL_PATTERN.matches(email) && email == confirmedEmail)
        require(adminPublicOrigin.isNotBlank())
    }

    private suspend fun issueInvitation(email: String, replacementConfirmed: Boolean) =
        transactionRunner.runAtomically {
            repository.lock()
            ensureNoActiveOwner()
            ensureEmailIsAvailable(email)
            val current = repository.findCurrent()
            ensureInvitationCanBeReplaced(current, replacementConfirmed)
            val currentPending = current?.status == BootstrapInvitationStatus.PENDING

            val generated = tokenGenerator.generate()
            val expiration = clock.instant().plus(INVITATION_TTL)
            repository.replace(
                BootstrapInvitation(
                    email = email,
                    tokenHash = generated.tokenHash,
                    expiresAt = expiration,
                    createdAt = clock.instant(),
                ),
            )
            auditPublisher.publish(
                AdminAuditEvent(
                    eventId = UUID.randomUUID(),
                    occurredAt = clock.instant(),
                    operatorPrincipalId = null,
                    operatorPlatformRoles = emptySet(),
                    actorKind = AdminActorKind.SYSTEM_BOOTSTRAP,
                    action = if (currentPending) {
                        AdminAuditAction.BOOTSTRAP_INVITATION_REPLACED
                    } else {
                        AdminAuditAction.BOOTSTRAP_INVITATION_ISSUED
                    },
                    targetType = TARGET_TYPE,
                    targetId = TARGET_ID,
                    result = AdminAuditResult.SUCCEEDED,
                    metadata = mapOf("email" to email),
                ),
            )
            generated to expiration
        }

    private suspend fun ensureNoActiveOwner() {
        if (repository.hasActiveOwner()) throw PlatformOwnerAlreadyExistsException()
    }

    private suspend fun ensureEmailIsAvailable(email: String) {
        if (identityLookup.findByEmail(email) != null) throw ExistingBootstrapAccountException()
    }

    private fun ensureInvitationCanBeReplaced(current: BootstrapInvitation?, replacementConfirmed: Boolean) {
        if (current?.status == BootstrapInvitationStatus.CLAIMED ||
            current?.status == BootstrapInvitationStatus.COMPLETED
        ) {
            throw BootstrapUnavailableException()
        }
        if (current?.status == BootstrapInvitationStatus.PENDING && !replacementConfirmed) {
            throw ActiveBootstrapInvitationException()
        }
    }

    private companion object {
        private val INVITATION_TTL: Duration = Duration.ofMinutes(30)
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        private const val TARGET_TYPE = "PLATFORM_BOOTSTRAP_INVITATION"
        private const val TARGET_ID = "initial-owner"
    }
}
