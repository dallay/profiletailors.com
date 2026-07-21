package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.ApproveTakedownCommand
import com.profiletailors.smp.governance.application.ListTakedownReportsQuery
import com.profiletailors.smp.governance.application.RejectTakedownCommand
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/governance/takedown")
class TakedownController(private val mediator: Mediator) {

    private val validStatuses: Set<String> = TakedownReportStatus.entries.map { it.name }.toSet()

    /**
     * Reports a media asset for copyright/DMCA takedown.
     */
    @PostMapping("/reports")
    suspend fun report(@Valid @RequestBody request: ReportTakedownRequest): ResponseEntity<TakedownReportResponse> {
        val report = mediator.send(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(report.toResponse())
    }

    /**
     * Approves a pending takedown report.
     */
    @PostMapping("/reports/{reportId}/approve")
    suspend fun approve(@PathVariable reportId: String): TakedownReportResponse =
        mediator.send(ApproveTakedownCommand(reportId)).toResponse()

    /**
     * Rejects/dismisses a pending takedown report.
     */
    @PostMapping("/reports/{reportId}/reject")
    suspend fun reject(
        @PathVariable reportId: String,
        @Valid @RequestBody request: ReviewTakedownRequest,
    ): TakedownReportResponse = mediator.send(RejectTakedownCommand(reportId, request.rejectionReason)).toResponse()

    /**
     * Lists takedown reports for the current workspace, optionally filtered by status.
     */
    @GetMapping("/reports")
    suspend fun list(@RequestParam(required = false) status: String?): Flow<TakedownReportResponse> {
        status?.let { validateStatus(it) }
        val statusFilter = status?.let(TakedownReportStatus::valueOf)
        return mediator.send(ListTakedownReportsQuery(statusFilter))
            .map { it.toResponse() }
    }

    private fun validateStatus(value: String) {
        if (value !in validStatuses) {
            throw EnumValidationException("status", value, validStatuses)
        }
    }
}
