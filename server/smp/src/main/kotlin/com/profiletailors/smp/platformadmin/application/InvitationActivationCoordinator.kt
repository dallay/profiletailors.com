package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.identity.application.InvitationRegistrationContext
import com.profiletailors.smp.identity.application.InvitationRegistrationSource
import com.profiletailors.smp.identity.application.InvitationRegistrationTarget
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationAcceptanceFailureCode
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import java.time.Clock
import java.time.Instant

class InvitationActivationCoordinator(
    private val invitationRepository: InvitationRepository,
    private val tokenHasher: TokenHasher,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val workspaceProvisioningService: WorkspaceProvisioningService,
    private val waitlistEntryAdmin: WaitlistEntryAdmin,
    private val membershipProvisioner: WorkspaceMembershipProvisioner,
    private val clock: Clock,
    private val telemetry: InvitationTelemetry = InvitationTelemetry.noop(),
) {
    data class InvitationActivationResult(val invitation: Invitation, val membershipStatus: WorkspaceMembershipStatus)

    private fun fail(code: InvitationAcceptanceFailureCode): Nothing {
        when (code) {
            InvitationAcceptanceFailureCode.EXPIRED -> telemetry.recordInvitationExpired()
            InvitationAcceptanceFailureCode.ALREADY_CONSUMED,
            InvitationAcceptanceFailureCode.REPLAYED,
            -> telemetry.recordInvitationReplayRejected()

            else -> Unit
        }
        throw InvitationNotAcceptableException(code)
    }

    suspend fun prepare(rawToken: String, normalizedEmail: String): InvitationRegistrationContext {
        val candidateKey = candidateKey(rawToken)
        val invitation = invitationRepository.findByCandidateKey(candidateKey)
            ?: fail(InvitationAcceptanceFailureCode.INVALID)
        validateToken(invitation, rawToken)
        validateLifecycle(invitation)
        validateEmail(invitation, normalizedEmail)
        return invitation.toRegistrationContext()
    }

    suspend fun complete(
        context: InvitationRegistrationContext,
        rawToken: String,
        principalId: String,
        displayName: String,
    ): InvitationActivationResult {
        val candidateKey = candidateKey(rawToken)
        val invitation = invitationRepository.findByCandidateKeyForUpdate(candidateKey)
            ?: fail(InvitationAcceptanceFailureCode.INVALID)
        return completeLocked(context, invitation, rawToken, principalId, displayName)
    }

    suspend fun activateForRegistration(
        rawToken: String,
        email: String,
        principalId: String,
    ): InvitationActivationResult {
        val candidateKey = candidateKey(rawToken)
        val invitation = invitationRepository.findByCandidateKeyForUpdate(candidateKey)
            ?: fail(InvitationAcceptanceFailureCode.INVALID)
        return completeLocked(
            context = invitation.toRegistrationContext(),
            invitation = invitation,
            rawToken = rawToken,
            principalId = principalId,
            displayName = email,
            requestedEmail = email,
        )
    }

    private suspend fun completeLocked(
        context: InvitationRegistrationContext,
        invitation: Invitation,
        rawToken: String,
        principalId: String,
        displayName: String,
        requestedEmail: String? = null,
    ): InvitationActivationResult {
        validateToken(invitation, rawToken)
        validateLifecycle(invitation)
        validateContext(invitation, context)
        requestedEmail?.let { validateEmail(invitation, it) }

        val identity = principalIdentityLookup.findByPrincipalId(principalId)
            ?: fail(InvitationAcceptanceFailureCode.INVALID)
        if (identity.principalType != PrincipalType.USER) {
            fail(InvitationAcceptanceFailureCode.INVALID)
        }
        validateEmail(invitation, identity.email)

        val now = clock.instant()
        val provisioningDisplayName = if (requestedEmail != null) identity.email ?: displayName else displayName
        val resolvedWorkspaceId = when (invitation.target) {
            InvitationTarget.EXISTING_WORKSPACE -> requireWorkspaceId(invitation.workspaceId)
            InvitationTarget.NEW_WORKSPACE -> requireWorkspaceId(
                workspaceProvisioningService.provisionDefaultWorkspace(
                    principalId = identity.principalId,
                    displayName = provisioningDisplayName,
                ).workspaceId,
            )
        }

        convertWaitlistEntryIfNeeded(invitation, now)

        val acceptedPrincipalId = PlatformPrincipalIds.fromUuid(identity.principalId)
        require(acceptedPrincipalId.startsWith("user-")) { "Accepted principal id must use user- prefix" }
        val accepted = invitation.accept(now, acceptedPrincipalId, resolvedWorkspaceId)
        val success = invitationRepository.updateIfVersionMatches(accepted)
        if (!success) throw OptimisticLockException()

        val membership = membershipProvisioner.reconcile(resolvedWorkspaceId, identity.principalId)
        return InvitationActivationResult(accepted, membership.status)
    }

    private fun candidateKey(rawToken: String): String = (tokenHasher as? InvitationTokenCandidateKey)
        ?.candidateKey(rawToken)
        ?: fail(InvitationAcceptanceFailureCode.INVALID)

    private fun validateToken(invitation: Invitation, rawToken: String) {
        if (!tokenHasher.matches(rawToken, invitation.tokenHash)) {
            fail(InvitationAcceptanceFailureCode.INVALID)
        }
    }

    private fun validateLifecycle(invitation: Invitation) {
        when (invitation.status) {
            InvitationStatus.ACTIVE -> if (invitation.isExpired(clock.instant())) {
                fail(InvitationAcceptanceFailureCode.EXPIRED)
            }

            InvitationStatus.ACCEPTED -> fail(InvitationAcceptanceFailureCode.ALREADY_CONSUMED)
            InvitationStatus.EXPIRED -> fail(InvitationAcceptanceFailureCode.EXPIRED)
            InvitationStatus.REVOKED -> fail(InvitationAcceptanceFailureCode.REVOKED)
        }
    }

    private fun validateEmail(invitation: Invitation, email: String?) {
        if (normalize(invitation.invitedEmailNormalized) != normalize(email)) {
            fail(InvitationAcceptanceFailureCode.EMAIL_MISMATCH)
        }
    }

    private fun validateContext(invitation: Invitation, context: InvitationRegistrationContext) {
        val target = when (invitation.target) {
            InvitationTarget.EXISTING_WORKSPACE -> InvitationRegistrationTarget.EXISTING_WORKSPACE
            InvitationTarget.NEW_WORKSPACE -> InvitationRegistrationTarget.NEW_WORKSPACE
        }
        val source = when (invitation.source) {
            InvitationSource.DIRECT -> InvitationRegistrationSource.DIRECT
            InvitationSource.WAITLIST -> InvitationRegistrationSource.WAITLIST
        }
        val contextMatches = invitation.id.value.toString() == context.invitationId &&
            target == context.target &&
            source == context.source &&
            context.workspaceId == invitation.workspaceId
        if (!contextMatches) {
            fail(InvitationAcceptanceFailureCode.INVALID)
        }
    }

    private fun Invitation.toRegistrationContext(): InvitationRegistrationContext = InvitationRegistrationContext(
        invitationId = id.value.toString(),
        target = when (target) {
            InvitationTarget.EXISTING_WORKSPACE -> InvitationRegistrationTarget.EXISTING_WORKSPACE
            InvitationTarget.NEW_WORKSPACE -> InvitationRegistrationTarget.NEW_WORKSPACE
        },
        workspaceId = workspaceId,
        source = when (source) {
            InvitationSource.DIRECT -> InvitationRegistrationSource.DIRECT
            InvitationSource.WAITLIST -> InvitationRegistrationSource.WAITLIST
        },
    )

    private fun requireWorkspaceId(workspaceId: String?): String = workspaceId
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Invitation workspace could not be resolved.")

    private suspend fun convertWaitlistEntryIfNeeded(invitation: Invitation, at: Instant) {
        if (invitation.target != InvitationTarget.NEW_WORKSPACE || invitation.source != InvitationSource.WAITLIST) {
            return
        }

        val entry = waitlistEntryAdmin.findById(requireNotNull(invitation.sourceReferenceId))
            ?: throw IllegalStateException("Waitlist entry not found")
        entry.convert(at)
        waitlistEntryAdmin.save(entry)
    }

    private fun normalize(value: String?): String = value?.trim()?.lowercase() ?: ""
}

class OptimisticLockException : RuntimeException("Invitation update failed due to concurrent modification")
