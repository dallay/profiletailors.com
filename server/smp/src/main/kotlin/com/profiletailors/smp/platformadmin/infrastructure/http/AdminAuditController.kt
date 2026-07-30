package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.model.AdminAuditEventSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminAuditQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.query.ListAdminAuditEventsQuery
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin/audit-events")
class AdminAuditController(
    private val auditQuery: AdminAuditQuery,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping
    suspend fun listEvents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam operatorPrincipalId: UUID? = null,
        @RequestParam action: String? = null,
        @RequestParam targetType: String? = null,
        @RequestParam targetId: String? = null,
        @RequestParam result: String? = null,
        @RequestParam occurredFrom: Instant? = null,
        @RequestParam occurredTo: Instant? = null,
        @RequestParam correlationId: String? = null,
    ): ResponseEntity<PagedResult<AdminAuditEventSummary>> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.AUDIT_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val query = ListAdminAuditEventsQuery(
            page = page,
            size = size,
            operatorPrincipalId = operatorPrincipalId,
            action = action,
            targetType = targetType,
            targetId = targetId,
            result = result,
            occurredFrom = occurredFrom,
            occurredTo = occurredTo,
            correlationId = correlationId,
        )
        return ResponseEntity.ok(auditQuery.list(query))
    }

    @GetMapping("/{eventId}")
    suspend fun getEvent(@PathVariable eventId: UUID): ResponseEntity<AdminAuditEventSummary> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.AUDIT_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val event = auditQuery.findById(eventId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(event)
    }

    private suspend fun resolveOperator(): Pair<UUID, Set<PlatformRole>>? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        return operatorId to assignments.map { it.role }.toSet()
    }
}
