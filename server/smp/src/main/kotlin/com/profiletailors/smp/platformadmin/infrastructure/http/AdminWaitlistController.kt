package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntryDetail
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntrySummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.query.ListAdminWaitlistEntriesQuery
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.platformadmin.infrastructure.persistence.ADMIN_PAGE_MAX_SIZE
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin/waitlist-entries")
class AdminWaitlistController(
    private val waitlistQuery: AdminWaitlistQuery,
    private val inviteHandler: InviteWaitlistEntryHandler,
    private val cancelHandler: CancelWaitlistEntryHandler,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
) {

    @GetMapping
    suspend fun listEntries(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "joinedAt") sort: String,
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam status: String? = null,
        @RequestParam waitlistId: String? = null,
        @RequestParam waitlistKey: String? = null,
        @RequestParam email: String? = null,
        @RequestParam joinedFrom: Instant? = null,
        @RequestParam joinedTo: Instant? = null,
        @RequestParam invitedFrom: Instant? = null,
        @RequestParam invitedTo: Instant? = null,
    ): ResponseEntity<PagedResult<AdminWaitlistEntrySummary>> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.WAITLIST_READ !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_READ)
        }
        if (size > ADMIN_PAGE_MAX_SIZE) return ResponseEntity.badRequest().build()

        val query = ListAdminWaitlistEntriesQuery(
            page = page,
            size = size,
            sortField = sort,
            sortDirection = direction,
            status = status,
            waitlistId = waitlistId,
            waitlistKey = waitlistKey,
            email = email,
            joinedFrom = joinedFrom,
            joinedTo = joinedTo,
            invitedFrom = invitedFrom,
            invitedTo = invitedTo,
        )
        return ResponseEntity.ok(waitlistQuery.list(query))
    }

    @GetMapping("/{entryId}")
    suspend fun getEntry(@PathVariable entryId: String): ResponseEntity<AdminWaitlistEntryDetail> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.WAITLIST_READ !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_READ)
        }
        val detail = waitlistQuery.findById(entryId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    @PostMapping("/{entryId}/invitations")
    @Transactional
    suspend fun invite(@PathVariable entryId: String): ResponseEntity<AdminInvitationSummary> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = inviteHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = entryId,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{entryId}/cancel")
    @Transactional
    suspend fun cancel(
        @PathVariable entryId: String,
        @RequestBody request: CancelRequest,
    ): ResponseEntity<Map<String, String>> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        cancelHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.CancelWaitlistEntryCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                waitlistEntryId = entryId,
                reason = request.reason,
            ),
        )
        return ResponseEntity.ok(mapOf("status" to "cancelled"))
    }

    private suspend fun resolveOperator(): Pair<UUID, Set<PlatformRole>>? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        return operatorId to assignments.map { it.role }.toSet()
    }

    data class CancelRequest(val reason: String)

}
