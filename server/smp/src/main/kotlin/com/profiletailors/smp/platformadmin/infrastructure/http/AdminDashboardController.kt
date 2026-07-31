package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.model.AdminDashboardSummary
import com.profiletailors.smp.platformadmin.application.ports.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController(
    private val waitlistQuery: AdminWaitlistQuery,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
    private val clock: Clock,
) {
    @GetMapping
    suspend fun getDashboard(
        @RequestParam(defaultValue = "30") periodDays: Int,
    ): ResponseEntity<AdminDashboardSummary> {
        val ctx = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        val operatorRoles = assignments.map { it.role }.toSet()

        if (PlatformPermission.DASHBOARD_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val statusCounts = waitlistQuery.countByStatus()
        val summary = AdminDashboardSummary(
            pendingCount = statusCounts["PENDING"] ?: 0L,
            invitedCount = statusCounts["INVITED"] ?: 0L,
            convertedCount = statusCounts["CONVERTED"] ?: 0L,
            cancelledCount = statusCounts["CANCELLED"] ?: 0L,
            activeInvitationCount = 0L,
            invitationsExpiringIn24h = 0L,
            invitationsExpiringIn7d = 0L,
            failedDeliveryCount = 0L,
            registrationsInPeriod = statusCounts["CONVERTED"] ?: 0L,
            periodDays = periodDays,
        )
        return ResponseEntity.ok(summary)
    }
}
