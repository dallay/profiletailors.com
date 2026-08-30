@file:Suppress("MaxLineLength", "SwallowedException")

package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.application.BulkJobResult
import com.profiletailors.smp.publishing.application.BulkTemplateCsvQuery
import com.profiletailors.smp.publishing.application.BulkTemplateCsvResult
import com.profiletailors.smp.publishing.application.BulkTemplatesQuery
import com.profiletailors.smp.publishing.application.BulkTemplatesResult
import com.profiletailors.smp.publishing.application.BulkWorkspaceMismatchException
import com.profiletailors.smp.publishing.application.DuplicateBulkImportException
import com.profiletailors.smp.publishing.application.GetBulkJobQuery
import com.profiletailors.smp.publishing.application.ScheduleBulkCommand
import com.profiletailors.smp.publishing.application.ScheduleBulkResult
import com.profiletailors.smp.publishing.application.ValidateBulkCommand
import com.profiletailors.smp.publishing.application.ValidateBulkResult
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/bulk")
@Tag(name = "Bulk Publishing", description = "Bulk CSV scheduling endpoints")
class BulkPublishingController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
) {
    @Operation(summary = "Validate bulk CSV")
    @PostMapping("/validate", consumes = [MediaType.APPLICATION_JSON_VALUE], version = "1")
    suspend fun validate(
        @PathVariable workspaceId: String,
        @Valid @RequestBody request: BulkValidateRequest,
    ): ValidateBulkResult {
        requireWorkspacePath(workspaceId)
        return mediator.send(ValidateBulkCommand(workspaceId = workspaceId, csvText = request.csvText))
    }

    @Operation(summary = "Schedule bulk CSV")
    @PostMapping("/schedule", consumes = [MediaType.APPLICATION_JSON_VALUE], version = "1")
    suspend fun schedule(
        @PathVariable workspaceId: String,
        @Valid @RequestBody request: BulkScheduleRequest,
    ): ResponseEntity<ScheduleBulkResult> {
        requireWorkspacePath(workspaceId)
        val csvText = request.csvText
        val csvHash = request.csvHash ?: csvText
        return try {
            val result = mediator.send(
                ScheduleBulkCommand(workspaceId = workspaceId, csvText = csvText, csvHash = csvHash),
            )
            val status = if (result.failedCount > 0 &&
                result.scheduledCount > 0
            ) {
                HttpStatus.MULTI_STATUS
            } else {
                HttpStatus.OK
            }
            ResponseEntity.status(status).body(result)
        } catch (ex: DuplicateBulkImportException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ScheduleBulkResult(
                    jobId = ex.jobId,
                    totalRows = 0,
                    scheduledCount = 0,
                    failedCount = 0,
                    rows = emptyList(),
                ),
            )
        }
    }

    @Operation(summary = "Get bulk job")
    @GetMapping("/jobs/{jobId}", version = "1")
    suspend fun getJob(@PathVariable workspaceId: String, @PathVariable jobId: String): BulkJobResult {
        requireWorkspacePath(workspaceId)
        return mediator.send(GetBulkJobQuery(workspaceId = workspaceId, jobId = jobId))
    }

    @Operation(summary = "List bulk templates")
    @GetMapping("/templates", version = "1")
    suspend fun listTemplates(@PathVariable workspaceId: String): BulkTemplatesResult {
        requireWorkspacePath(workspaceId)
        return mediator.send(BulkTemplatesQuery(workspaceId = workspaceId))
    }

    @Operation(summary = "Get bulk template CSV")
    @GetMapping("/templates/{templateId}/csv", version = "1")
    suspend fun getTemplateCsv(
        @PathVariable workspaceId: String,
        @PathVariable templateId: String,
    ): ResponseEntity<String> {
        requireWorkspacePath(workspaceId)
        val result: BulkTemplateCsvResult = mediator.send(
            BulkTemplateCsvQuery(workspaceId = workspaceId, templateId = templateId),
        )
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(result.csv)
    }

    private fun requireWorkspacePath(pathWorkspaceId: String) {
        val ctx = try {
            resourceContextProvider.requireWorkspaceContext()
        } catch (ex: IllegalStateException) {
            throw BulkWorkspaceMismatchException(ex.message ?: "Workspace context required.")
        } catch (ex: IllegalArgumentException) {
            throw BulkWorkspaceMismatchException(ex.message ?: "Workspace context required.")
        }
        val contextWorkspaceId = ctx.workspaceId
        if (pathWorkspaceId != contextWorkspaceId) {
            throw BulkWorkspaceMismatchException("Workspace path does not match the authenticated workspace.")
        }
    }
}

data class BulkValidateRequest(val csvText: String)
data class BulkScheduleRequest(val csvText: String, val csvHash: String? = null)
