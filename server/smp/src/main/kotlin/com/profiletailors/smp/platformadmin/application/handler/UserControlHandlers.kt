package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.identity.application.AccountStateGateway
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.PlatformPrincipalIds
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

private data class AuditContext(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
    val targetPrincipalId: String,
    val action: AdminAuditAction,
)

private data class StateTransition(val expected: UserAccountState, val replacement: UserAccountState)

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
        requireNoSelfTarget(
            command.operatorRoles,
            command.operatorPrincipalId,
            command.targetPrincipalId,
            AdminAuditAction.USER_DISABLED,
        )
        return executeStateChange(
            operation = "disable",
            context = AuditContext(
                operatorPrincipalId = command.operatorPrincipalId,
                operatorRoles = command.operatorRoles,
                targetPrincipalId = command.targetPrincipalId,
                action = AdminAuditAction.USER_DISABLED,
            ),
            transition = StateTransition(
                expected = UserAccountState.ACTIVE,
                replacement = UserAccountState.DISABLED,
            ),
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
        requireNoSelfTarget(
            command.operatorRoles,
            command.operatorPrincipalId,
            command.targetPrincipalId,
            AdminAuditAction.USER_ENABLED,
        )
        return executeStateChange(
            operation = "enable",
            context = AuditContext(
                operatorPrincipalId = command.operatorPrincipalId,
                operatorRoles = command.operatorRoles,
                targetPrincipalId = command.targetPrincipalId,
                action = AdminAuditAction.USER_ENABLED,
            ),
            transition = StateTransition(
                expected = UserAccountState.DISABLED,
                replacement = UserAccountState.ACTIVE,
            ),
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
        requireNoSelfTarget(
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
                context = AuditContext(
                    operatorPrincipalId = command.operatorPrincipalId,
                    operatorRoles = command.operatorRoles,
                    targetPrincipalId = command.targetPrincipalId,
                    action = AdminAuditAction.USER_SESSIONS_REVOKED,
                ),
                metadata = mapOf("revokedSessionCount" to count.toString()),
            )
            telemetry.record("sessions_revoke", "success")
            UserSessionsRevokeResult(command.targetPrincipalId, count)
        } catch (error: Throwable) {
            telemetry.record("sessions_revoke", "failure")
            publishFailure(
                context = AuditContext(
                    operatorPrincipalId = command.operatorPrincipalId,
                    operatorRoles = command.operatorRoles,
                    targetPrincipalId = command.targetPrincipalId,
                    action = AdminAuditAction.USER_SESSIONS_REVOKED,
                ),
            )
            throw error
        }
    }

    private suspend fun executeStateChange(
        operation: String,
        context: AuditContext,
        transition: StateTransition,
        disable: Boolean,
    ): UserControlResult = try {
        val count = transactionRunner.runAtomically {
            requireUser(context.targetPrincipalId)
            changeStateOrConfirm(context.targetPrincipalId, transition.expected, transition.replacement)
            revokeSessionsIfDisabled(disable, context.targetPrincipalId)
        }
        publishSuccess(
            context = context,
            metadata = mapOf("revokedSessionCount" to count.toString()),
        )
        telemetry.record(operation, "success")
        UserControlResult(context.targetPrincipalId, transition.replacement, count)
    } catch (error: Throwable) {
        telemetry.record(operation, "failure")
        publishFailure(context = context)
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
                    context = AuditContext(
                        operatorPrincipalId = operatorPrincipalId,
                        operatorRoles = roles,
                        targetPrincipalId = targetPrincipalId,
                        action = action,
                    ),
                    result = AdminAuditResult.REJECTED,
                    reason = "Platform user-management permission required.",
                ),
            )
            throw PlatformAccessDeniedException(PlatformPermission.USERS_MANAGE)
        }
    }

    private suspend fun requireNoSelfTarget(
        roles: Set<com.profiletailors.smp.platformadmin.domain.PlatformRole>,
        operatorPrincipalId: UUID,
        targetPrincipalId: String,
        action: AdminAuditAction,
    ) {
        val targetUuid = runCatching { PlatformPrincipalIds.toUuid(targetPrincipalId) }.getOrNull()
        if (targetUuid != null && operatorPrincipalId == targetUuid) {
            telemetry.recordAuthorizationRejected(action.metricOperation)
            auditPublisher.publish(
                auditEvent(
                    AuditContext(
                        operatorPrincipalId = operatorPrincipalId,
                        operatorRoles = roles,
                        targetPrincipalId = targetPrincipalId,
                        action = action,
                    ),
                    AdminAuditResult.REJECTED,
                    reason = "Self-targeted user control operation is not allowed.",
                ),
            )
            throw PlatformAccessDeniedException(PlatformPermission.USERS_MANAGE)
        }
    }

    private suspend fun publishSuccess(context: AuditContext, metadata: Map<String, String>) {
        auditPublisher.publish(
            auditEvent(
                context,
                AdminAuditResult.SUCCEEDED,
                metadata = metadata,
            ),
        )
    }

    private suspend fun publishFailure(context: AuditContext) {
        auditPublisher.publish(
            auditEvent(
                context,
                AdminAuditResult.FAILED,
                reason = "User control operation failed.",
            ),
        )
    }

    private fun auditEvent(
        context: AuditContext,
        result: AdminAuditResult,
        reason: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) = AdminAuditEvent(
        eventId = UUID.randomUUID(),
        occurredAt = clock.instant(),
        operatorPrincipalId = context.operatorPrincipalId,
        operatorPlatformRoles = context.operatorRoles,
        action = context.action,
        targetType = "Principal",
        targetId = context.targetPrincipalId,
        result = result,
        reason = reason,
        metadata = metadata,
    )
}

class UserControlStateConflictException(principalId: String) :
    RuntimeException("User account state transition failed: $principalId")
