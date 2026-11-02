package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.identity.application.CurrentTermsVersionProvider
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapTermsConsentVerifier
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.AdminActorKind
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
internal class CompleteBootstrapOwnerGrantHandler(
    private val invitationRepository: BootstrapInvitationRepository,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val termsConsentVerifier: BootstrapTermsConsentVerifier,
    private val currentTermsVersionProvider: CurrentTermsVersionProvider,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(principalIdValue: String, email: String, verifiedAt: Instant) {
        val principalId = UUID.fromString(principalIdValue)
        invitationRepository.lock()
        val invitation = invitationRepository.findClaimedByPrincipalId(principalId) ?: return
        if (invitation.email != email.trim().lowercase() || invitationRepository.hasActiveOwner()) {
            throw BootstrapUnavailableException()
        }
        if (!termsConsentVerifier.hasCurrentTermsConsent(
                principalId,
                currentTermsVersionProvider.currentTermsVersion(),
            )
        ) {
            throw BootstrapUnavailableException()
        }

        roleAssignmentRepository.save(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OWNER,
                assignedAt = verifiedAt,
                assignedBy = null,
                actorKind = AdminActorKind.SYSTEM_BOOTSTRAP,
            ),
        )
        if (!invitationRepository.complete(principalId, clock.instant())) throw BootstrapUnavailableException()
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = clock.instant(),
                operatorPrincipalId = null,
                operatorPlatformRoles = emptySet(),
                actorKind = AdminActorKind.SYSTEM_BOOTSTRAP,
                action = AdminAuditAction.BOOTSTRAP_OWNER_GRANTED,
                targetType = TARGET_TYPE,
                targetId = principalId.toString(),
                result = AdminAuditResult.SUCCEEDED,
                metadata = mapOf("email" to invitation.email),
            ),
        )
    }

    private companion object {
        private const val TARGET_TYPE = "PLATFORM_OWNER"
    }
}
