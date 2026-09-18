package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.identity.application.AccountStateGateway
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.command.DisableUserCommand
import com.profiletailors.smp.platformadmin.application.command.EnableUserCommand
import com.profiletailors.smp.platformadmin.application.command.RevokeUserSessionsCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.UserControlTelemetry
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import com.profiletailors.smp.platformadmin.application.model.UserSessionsRevokeResult
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.UserNotFoundException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class UserControlHandlers(
    private val accountStateGateway: AccountStateGateway,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val telemetry: UserControlTelemetry,
) {
    private val AdminAuditAction.metricOperation: String
        get() = when (this) {
            AdminAuditAction.USER_DISABLED -> "disable"
            AdminAuditAction.USER_ENABLED -> "enable"
            AdminAuditAction.USER_SESSIONS_REVOKED -> "sessions_revoke"
            else -> name.lowercase()
        }

    suspend fun disable(command: DisableUserCommand): UserControlResult {
        requirePermission(
            command.operatorRoles,
            command.operatorPrincipalId,
            command.targetPrincipalId,
            AdminAuditAction.USER_DISABLED,
        )
        return executeStateChange(
            operation = "disable",
            operatorPrincipalId = command.operatorPrincipalId,
            operatorRoles = command.operatorRoles,
            targetPrincipalId = command.targetPrincipalId,
            expected = UserAccountState.ACTIVE,
            replacement = UserAccountState.DISABLED,
            action = AdminAuditAction.USER_DISABLED,
            disable = true,
        )
    }

    suspend fun enable(command: EnableUserCommand): UserControlResult {
        requirePermission(
            command.operatorRoles,
            command.operatorPrincipalId,
            command.targetPrincipalId,
            AdminAuditAction.USER_ENABLED,
        )
        return executeStateChange(
            operation = "enable",
            operatorPrincipalId = command.operatorPrincipalId,
            operatorRoles = command.operatorRoles,
            targetPrincipalId = command.targetPrincipalId,
            expected = UserAccountState.DISABLED,
            replacement = UserAccountState.ACTIVE,
            action = AdminAuditAction.USER_ENABLED,
            disable = false,
        )
    }

    suspend fun revokeSessions(command: RevokeUserSessionsCommand): UserSessionsRevokeResult {
        requirePermission(
            command.operatorRoles,
            command.operatorPrincipalId,
            command.targetPrincipalId,
            AdminAuditAction.USER_SESSIONS_REVOKED,
        )
        return try {
            val count = transactionRunner.runAtomically {
                requireUser(command.targetPrincipalId)
                refreshSessionLifecycleService.revokeAllForPrincipal(command.targetPrincipalId)
            }
            publishSuccess(
                operatorPrincipalId = command.operatorPrincipalId,
                operatorRoles = command.operatorRoles,
                targetPrincipalId = command.targetPrincipalId,
                action = AdminAuditAction.USER_SESSIONS_REVOKED,
                metadata = mapOf("revokedSessionCount" to count.toString()),
            )
            telemetry.record("sessions_revoke", "success")
            UserSessionsRevokeResult(command.targetPrincipalId, count)
        } catch (error: Throwable) {
            telemetry.record("sessions_revoke", "failure")
            publishFailure(
                command.operatorPrincipalId,
                command.operatorRoles,
                command.targetPrincipalId,
                AdminAuditAction.USER_SESSIONS_REVOKED,
            )
            throw error
        }
    }

    private suspend fun executeStateChange(
        operation: String,
        operatorPrincipalId: UUID,
        operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        targetPrincipalId: String,
        expected: UserAccountState,
        replacement: UserAccountState,
        action: AdminAuditAction,
        disable: Boolean,
    ): UserControlResult = try {
        val count = transactionRunner.runAtomically {
            requireUser(targetPrincipalId)
            changeStateOrConfirm(targetPrincipalId, expected, replacement)
            revokeSessionsIfDisabled(disable, targetPrincipalId)
        }
        publishSuccess(
            operatorPrincipalId,
            operatorRoles,
            targetPrincipalId,
            action,
            mapOf("revokedSessionCount" to count.toString()),
        )
        telemetry.record(operation, "success")
        UserControlResult(targetPrincipalId, replacement, count)
    } catch (error: Throwable) {
        telemetry.record(operation, "failure")
        publishFailure(operatorPrincipalId, operatorRoles, targetPrincipalId, action)
        throw error
    }

    private suspend fun changeStateOrConfirm(
        principalId: String,
        expected: UserAccountState,
        replacement: UserAccountState,
    ) {
        if (accountStateGateway.changeAccountState(principalId, expected, replacement)) return
        handleUnchangedState(principalId, replacement)
    }

    private suspend fun revokeSessionsIfDisabled(disable: Boolean, principalId: String): Int =
        if (disable) refreshSessionLifecycleService.revokeAllForPrincipal(principalId) else 0

    private suspend fun handleUnchangedState(principalId: String, replacement: UserAccountState): Int {
        if (accountStateGateway.findAccountState(principalId) == replacement) return 0
        throw UserControlStateConflictException(principalId)
    }

    private suspend fun requireUser(principalId: String): UserAccountState =
        accountStateGateway.findAccountState(principalId) ?: throw UserNotFoundException(principalId)

    private suspend fun requirePermission(
        roles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        operatorPrincipalId: UUID,
        targetPrincipalId: String,
        action: AdminAuditAction,
    ) {
        if (PlatformPermission.USERS_MANAGE !in roles.effectivePermissions()) {
            telemetry.recordAuthorizationRejected(action.metricOperation)
            auditPublisher.publish(
                auditEvent(
                    operatorPrincipalId,
                    roles,
                    targetPrincipalId,
                    action,
                    AdminAuditResult.REJECTED,
                    reason = "Platform user-management permission required.",
                ),
            )
            throw PlatformAccessDeniedException(PlatformPermission.USERS_MANAGE)
        }
    }

    private suspend fun publishSuccess(
        operatorPrincipalId: UUID,
        operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        targetPrincipalId: String,
        action: AdminAuditAction,
        metadata: Map<String, String>,
    ) {
        auditPublisher.publish(
            auditEvent(
                operatorPrincipalId,
                operatorRoles,
                targetPrincipalId,
                action,
                AdminAuditResult.SUCCEEDED,
                metadata = metadata,
            ),
        )
    }

    private suspend fun publishFailure(
        operatorPrincipalId: UUID,
        operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        targetPrincipalId: String,
        action: AdminAuditAction,
    ) {
        auditPublisher.publish(
            auditEvent(
                operatorPrincipalId,
                operatorRoles,
                targetPrincipalId,
                action,
                AdminAuditResult.FAILED,
                reason = "User control operation failed.",
            ),
        )
    }

    private fun auditEvent(
        operatorPrincipalId: UUID,
        operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        targetPrincipalId: String,
        action: AdminAuditAction,
        result: AdminAuditResult,
        reason: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) = AdminAuditEvent(
        eventId = UUID.randomUUID(),
        occurredAt = clock.instant(),
        operatorPrincipalId = operatorPrincipalId,
        operatorPlatformRoles = operatorRoles,
        action = action,
        targetType = "Principal",
        targetId = targetPrincipalId,
        result = result,
        reason = reason,
        metadata = metadata,
    )
}

class UserControlStateConflictException(principalId: String) :
    RuntimeException("User account state transition failed: $principalId")
