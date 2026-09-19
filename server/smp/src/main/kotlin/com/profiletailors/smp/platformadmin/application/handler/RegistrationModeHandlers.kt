package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.RegistrationModeChange
import com.profiletailors.smp.identity.application.RegistrationModeGateway
import com.profiletailors.smp.identity.domain.RegistrationMode
import com.profiletailors.smp.platformadmin.application.command.ChangeRegistrationModeCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvalidRegistrationModeException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class RegistrationModeHandlers(
    private val registrationModeGateway: RegistrationModeGateway,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) {
    suspend fun currentMode(operatorRoles: Set<PlatformRole>): RegistrationMode {
        if (PlatformPermission.CONFIGURATION_READ !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.CONFIGURATION_READ)
        }
        return registrationModeGateway.currentMode()
    }

    suspend fun changeMode(command: ChangeRegistrationModeCommand): RegistrationModeChange {
        requireManagePermission(command)
        return try {
            val newMode = parseMode(command.newMode)
            val change = transactionRunner.runAtomically {
                registrationModeGateway.changeMode(newMode)
            }
            publishSuccess(command, change)
            change
        } catch (error: Throwable) {
            publishFailure(command)
            throw error
        }
    }

    private fun parseMode(value: String): RegistrationMode =
        RegistrationMode.entries.find { it.name == value } ?: throw InvalidRegistrationModeException(value)

    private suspend fun requireManagePermission(command: ChangeRegistrationModeCommand) {
        if (PlatformPermission.CONFIGURATION_MANAGE !in command.operatorRoles.effectivePermissions()) {
            auditPublisher.publish(
                auditEvent(
                    command.operatorPrincipalId,
                    command.operatorRoles,
                    AdminAuditResult.REJECTED,
                    reason = "Platform configuration-management permission required.",
                ),
            )
            throw PlatformAccessDeniedException(PlatformPermission.CONFIGURATION_MANAGE)
        }
    }

    private suspend fun publishSuccess(command: ChangeRegistrationModeCommand, change: RegistrationModeChange) {
        auditPublisher.publish(
            auditEvent(
                command.operatorPrincipalId,
                command.operatorRoles,
                AdminAuditResult.SUCCEEDED,
                metadata = mapOf(
                    "previousMode" to change.previousMode.name,
                    "newMode" to change.newMode.name,
                ),
            ),
        )
    }

    private suspend fun publishFailure(command: ChangeRegistrationModeCommand) {
        auditPublisher.publish(
            auditEvent(
                command.operatorPrincipalId,
                command.operatorRoles,
                AdminAuditResult.FAILED,
                reason = "Registration mode change failed.",
            ),
        )
    }

    private fun auditEvent(
        operatorPrincipalId: UUID,
        operatorRoles: Set<PlatformRole>,
        result: AdminAuditResult,
        reason: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) = AdminAuditEvent(
        eventId = UUID.randomUUID(),
        occurredAt = clock.instant(),
        operatorPrincipalId = operatorPrincipalId,
        operatorPlatformRoles = operatorRoles,
        action = AdminAuditAction.CONFIGURATION_CHANGED,
        targetType = "CONFIGURATION",
        targetId = "registration.mode",
        result = result,
        reason = reason,
        metadata = metadata,
    )
}
