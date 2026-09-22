package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.governance.application.AdminTakedownReport
import com.profiletailors.smp.governance.application.AdminTakedownReportDetail
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyService
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.ApproveAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.command.RejectAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.handler.AdminTakedownHandlers
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.infrastructure.persistence.validatePagination
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/takedown-reports")
class AdminTakedownController(
    private val handlers: AdminTakedownHandlers,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
    private val configurationIdempotencyService: ConfigurationIdempotencyService,
) {
    @GetMapping
    suspend fun listReports(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) workspaceId: String?,
    ): ResponseEntity<PagedResult<AdminTakedownReport>> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        validatePagination(page, size)
        val result = handlers.list(operator.roles, status, workspaceId, page, size)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{reportId}")
    suspend fun getReport(@PathVariable reportId: String): ResponseEntity<AdminTakedownReportDetail> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(handlers.get(operator.roles, reportId))
    }

    @PostMapping("/{reportId}/approve")
    suspend fun approveReport(
        @PathVariable reportId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<AdminTakedownReport> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = configurationIdempotencyService.execute(
            operator.principalId,
            "$APPROVE_OPERATION:$reportId",
            idempotencyKey,
            AdminTakedownReport::class.java,
        ) {
            handlers.approve(ApproveAdminTakedownCommand(operator.principalId, operator.roles, reportId))
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{reportId}/reject")
    suspend fun rejectReport(
        @PathVariable reportId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: RejectAdminTakedownRequest,
    ): ResponseEntity<AdminTakedownReport> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = configurationIdempotencyService.execute(
            operator.principalId,
            "$REJECT_OPERATION:$reportId",
            idempotencyKey,
            AdminTakedownReport::class.java,
        ) {
            handlers.reject(
                RejectAdminTakedownCommand(operator.principalId, operator.roles, reportId, request.rejectionReason),
            )
        }
        return ResponseEntity.ok(result)
    }

    private fun requireIdempotencyKey(value: String) {
        require(
            value.length in IDEMPOTENCY_KEY_MIN_LENGTH..IDEMPOTENCY_KEY_MAX_LENGTH &&
                value.all { it.isLetterOrDigit() || it in "._:-" },
        )
    }

    private suspend fun resolveOperator(): OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }

    private companion object {
        const val APPROVE_OPERATION = "approve_takedown"
        const val REJECT_OPERATION = "reject_takedown"
        const val IDEMPOTENCY_KEY_MIN_LENGTH = 1
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 128
    }
}

data class RejectAdminTakedownRequest(val rejectionReason: String)
