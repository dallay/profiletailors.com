package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.ExternalIdentityLinkConflictException
import com.profiletailors.smp.identity.application.ExternalIdentityLinkProvisioningService
import com.profiletailors.smp.identity.application.ExternalIdentityLinkTargetNotFoundException
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.util.UUID

data class ProvisionExternalIdentityLinkCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
    val targetPrincipalId: String,
    val issuer: String,
    val subject: String,
)

@Service
class ProvisionExternalIdentityLinkHandler(
    private val provisioningService: ExternalIdentityLinkProvisioningService,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun rejectPermission(command: ProvisionExternalIdentityLinkCommand) {
        audit(command, AdminAuditResult.REJECTED, "permission_denied")
        throw PlatformAccessDeniedException(PlatformPermission.USERS_MANAGE)
    }

    suspend fun handle(command: ProvisionExternalIdentityLinkCommand) {
        if (PlatformPermission.USERS_MANAGE !in command.operatorRoles.effectivePermissions()) {
            rejectPermission(command)
        }
        try {
            transactionRunner.runAtomically {
                provisioningService.provision(command.targetPrincipalId, command.issuer, command.subject)
                audit(command, AdminAuditResult.SUCCEEDED)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            val rejected = exception is ExternalIdentityLinkConflictException ||
                exception is ExternalIdentityLinkTargetNotFoundException ||
                exception is IllegalArgumentException
            try {
                audit(
                    command,
                    if (rejected) AdminAuditResult.REJECTED else AdminAuditResult.FAILED,
                    exception.auditReason(),
                )
            } catch (auditFailure: Exception) {
                if (auditFailure !== exception) exception.addSuppressed(auditFailure)
            }
            throw exception
        }
    }

    private fun Exception.auditReason(): String = when (this) {
        is ExternalIdentityLinkConflictException -> "identity_conflict"
        is ExternalIdentityLinkTargetNotFoundException -> "target_not_found"
        is IllegalArgumentException -> "invalid_identity"
        else -> "persistence_failure"
    }

    private suspend fun audit(
        command: ProvisionExternalIdentityLinkCommand,
        result: AdminAuditResult,
        reason: String? = null,
    ) {
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = clock.instant(),
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.EXTERNAL_IDENTITY_LINK_PROVISIONED,
                targetType = "PRINCIPAL",
                targetId = command.targetPrincipalId,
                result = result,
                reason = reason,
                metadata = mapOf("issuer" to command.issuer, "subject" to command.subject),
            ),
        )
    }
}
