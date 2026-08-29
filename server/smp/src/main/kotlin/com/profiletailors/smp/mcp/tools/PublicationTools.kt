package com.profiletailors.smp.mcp.tools

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.application.IdempotencyGuard
import com.profiletailors.smp.mcp.infrastructure.ApplicationError
import com.profiletailors.smp.mcp.infrastructure.McpAuditEmitter
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationAuditFact
import com.profiletailors.smp.mcp.infrastructure.McpToolInvocationOutcome
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.DeletePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsQuery
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import kotlinx.coroutines.reactor.mono
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

data class ToolResponse<T>(val isSuccess: Boolean, val data: T? = null, val error: ApplicationError? = null) {
    companion object {
        fun <T> success(data: T): ToolResponse<T> = ToolResponse(isSuccess = true, data = data)
        fun <T> failure(error: ApplicationError): ToolResponse<T> = ToolResponse(isSuccess = false, error = error)
    }
}

@Component
class PublicationTools(
    private val mediator: Mediator,
    private val errorMapper: McpErrorMapper,
    private val idempotencyGuard: IdempotencyGuard? = null,
    private val auditEmitter: McpAuditEmitter? = null,
) {

    @McpTool(
        name = "list_publications",
        description = "List scheduled publications in the authenticated workspace within " +
            "an inclusive date range. Returns the same payload as the REST " +
            "ListPublicationsQuery handler.",
        generateOutputSchema = true,
    )
    suspend fun listPublications(
        @McpToolParam(description = "Start of the inclusive range (ISO-8601 instant, e.g. 2026-01-01T00:00:00Z).")
        from: String,
        @McpToolParam(description = "End of the inclusive range (ISO-8601 instant, e.g. 2026-12-31T23:59:59Z).")
        to: String,
        @McpToolParam(
            description = "Optional publication status filter " +
                "(DRAFT, SCHEDULED, QUEUED, PUBLISHED, FAILED, BLOCKED, CANCELLED).",
            required = false,
        )
        status: String? = null,
        @McpToolParam(description = "Optional social account id (channel) to narrow the query.", required = false)
        channelId: String? = null,
        @McpToolParam(description = "Client timezone hint, defaults to UTC.", required = false)
        @Suppress("UnusedParameter") timezone: String? = null,
    ): Mono<ToolResponse<Any>> {
        val mediatorRef = mediator
        val errorMapperRef = errorMapper
        return mono {
            runCatching {
                val fromInstant = Instant.parse(from)
                val toInstant = Instant.parse(to)
                val pubStatus = status?.let { PublicationStatus.valueOf(it) }

                val query = ListPublicationsQuery(
                    from = fromInstant,
                    to = toInstant,
                    status = pubStatus,
                    socialAccountId = channelId,
                )
                ToolResponse.success(mediatorRef.send(query) as Any)
            }.getOrElse { ex ->
                ToolResponse.failure(errorMapperRef.mapToError(ex))
            }
        }
    }

    @McpTool(
        name = "get_calendar",
        description = "Return the publication calendar view for a workspace within a date range. " +
            "Includes slots, conflicts, and activity.",
        generateOutputSchema = true,
    )
    suspend fun getCalendar(
        @McpToolParam(description = "Start of the inclusive range (ISO-8601 instant).")
        from: String,
        @McpToolParam(description = "End of the inclusive range (ISO-8601 instant).")
        to: String,
        @McpToolParam(
            description = "Optional publication status filter.",
            required = false,
        )
        status: String? = null,
        @McpToolParam(
            description = "Optional social account id (channel) to narrow the query.",
            required = false,
        )
        channelId: String? = null,
        @McpToolParam(
            description = "Timezone used to render slots, defaults to UTC.",
            required = false,
        )
        timezone: String? = null,
    ): Mono<ToolResponse<Any>> {
        val mediatorRef = mediator
        val errorMapperRef = errorMapper
        return mono {
            runCatching {
                val fromInstant = Instant.parse(from)
                val toInstant = Instant.parse(to)
                val pubStatus = status?.let { PublicationStatus.valueOf(it) }

                val query = GetCalendarPublicationsQuery(
                    from = fromInstant,
                    to = toInstant,
                    status = pubStatus,
                    socialAccountId = channelId,
                    timezone = timezone ?: "UTC",
                )
                ToolResponse.success(mediatorRef.send(query) as Any)
            }.getOrElse { ex ->
                ToolResponse.failure(errorMapperRef.mapToError(ex))
            }
        }
    }

    @McpTool(
        name = "create_publication",
        description = "Create a new publication draft, enqueue the publishing job, and return the enqueue " +
            "acknowledgement. Enqueue ack per ADR-0019 §Q1 — the worker is the boundary of the " +
            "asynchronous LinkedIn publish. Supports optional `idempotencyKey` for safe agent retries.",
        generateOutputSchema = true,
    )
    suspend fun createPublication(
        @McpToolParam(description = "Authenticated workspace id (injected from the JWT).")
        workspaceId: String,
        @McpToolParam(description = "Authenticated principal id (injected from the JWT).")
        principalId: String,
        @McpToolParam(description = "OAuth scopes granted by the access token.")
        grantedScopes: Set<String>,
        @McpToolParam(description = "Social account id (channel) that will publish the content.")
        socialAccountId: String,
        @McpToolParam(description = "Optional publication title.", required = false)
        title: String? = null,
        @McpToolParam(description = "Optional publication body text.", required = false)
        bodyText: String? = null,
        @McpToolParam(description = "Optional asset ids to attach.", required = false)
        assetIds: List<String> = emptyList(),
        @McpToolParam(description = "Schedule mode: NOW, SCHEDULED_AT, or NEXT_SLOT.")
        scheduleMode: String,
        @McpToolParam(description = "Required when scheduleMode = SCHEDULED_AT (ISO-8601 instant).", required = false)
        scheduledFor: String? = null,
        @McpToolParam(description = "Optional hint for NEXT_SLOT resolution (ISO-8601 instant).", required = false)
        nextSlotAfter: String? = null,
        @McpToolParam(description = "Mark the publication as priority.", required = false)
        priority: Boolean = false,
        @McpToolParam(
            description = "Optional idempotency key (1-128 chars, opaque). Replay returns cached result.",
            required = false,
        )
        idempotencyKey: String? = null,
    ): Mono<ToolResponse<PublicationResult>> {
        val mediatorRef = mediator
        val idempotencyGuardRef = idempotencyGuard
        val auditEmitterRef = auditEmitter
        val errorMapperRef = errorMapper
        val writeScope = MCP_WRITE_SCOPE
        val correlationIdRef = correlationId()
        return mono {
            runWriteTool(
                toolName = "create_publication",
                workspaceId = workspaceId,
                principalId = principalId,
                grantedScopes = grantedScopes,
                requiredScope = writeScope,
                idempotencyKey = idempotencyKey,
                idempotencyGuardRef = idempotencyGuardRef,
                auditEmitterRef = auditEmitterRef,
                errorMapperRef = errorMapperRef,
                correlationIdRef = correlationIdRef,
            ) {
                val mode = ScheduleMode.valueOf(scheduleMode)
                val scheduledInstant = scheduledFor?.let { Instant.parse(it) }
                val nextSlotInstant = nextSlotAfter?.let { Instant.parse(it) }
                val command = CreatePublicationCommand(
                    socialAccountId = socialAccountId,
                    title = title,
                    bodyText = bodyText,
                    assetIds = assetIds,
                    scheduleMode = mode,
                    scheduledFor = scheduledInstant,
                    nextSlotAfter = nextSlotInstant,
                    priority = priority,
                )
                mediatorRef.send(command)
            }
        }
    }

    @McpTool(
        name = "edit_publication",
        description = "Edit an existing publication in the active workspace. The publication MUST be in a " +
            "pre-delivery state (DRAFT/QUEUED/SCHEDULED). Re-running with the same `idempotencyKey` " +
            "returns the cached result.",
        generateOutputSchema = true,
    )
    suspend fun editPublication(
        @McpToolParam(description = "Authenticated workspace id (injected from the JWT).")
        workspaceId: String,
        @McpToolParam(description = "Authenticated principal id (injected from the JWT).")
        principalId: String,
        @McpToolParam(description = "OAuth scopes granted by the access token.")
        grantedScopes: Set<String>,
        @McpToolParam(description = "Publication id to edit.")
        publicationId: String,
        @McpToolParam(description = "Optional new title.", required = false)
        title: String? = null,
        @McpToolParam(description = "Optional new body text.", required = false)
        bodyText: String? = null,
        @McpToolParam(description = "Optional replacement asset ids.", required = false)
        assetIds: List<String>? = null,
        @McpToolParam(description = "Schedule mode: NOW, SCHEDULED_AT, or NEXT_SLOT.")
        scheduleMode: String,
        @McpToolParam(description = "Required when scheduleMode = SCHEDULED_AT (ISO-8601 instant).", required = false)
        scheduledFor: String? = null,
        @McpToolParam(description = "Optional hint for NEXT_SLOT resolution (ISO-8601 instant).", required = false)
        nextSlotAfter: String? = null,
        @McpToolParam(description = "Mark the publication as priority.", required = false)
        priority: Boolean = false,
        @McpToolParam(description = "Optional idempotency key (1-128 chars, opaque).", required = false)
        idempotencyKey: String? = null,
    ): Mono<ToolResponse<PublicationResult>> {
        val mediatorRef = mediator
        val idempotencyGuardRef = idempotencyGuard
        val auditEmitterRef = auditEmitter
        val errorMapperRef = errorMapper
        val writeScope = MCP_WRITE_SCOPE
        val correlationIdRef = correlationId()
        return mono {
            runWriteTool(
                toolName = "edit_publication",
                workspaceId = workspaceId,
                principalId = principalId,
                grantedScopes = grantedScopes,
                requiredScope = writeScope,
                idempotencyKey = idempotencyKey,
                idempotencyGuardRef = idempotencyGuardRef,
                auditEmitterRef = auditEmitterRef,
                errorMapperRef = errorMapperRef,
                correlationIdRef = correlationIdRef,
            ) {
                val mode = ScheduleMode.valueOf(scheduleMode)
                val scheduledInstant = scheduledFor?.let { Instant.parse(it) }
                val nextSlotInstant = nextSlotAfter?.let { Instant.parse(it) }
                val command = EditPublicationCommand(
                    publicationId = publicationId,
                    title = title,
                    bodyText = bodyText,
                    assetIds = assetIds,
                    scheduleMode = mode,
                    scheduledFor = scheduledInstant,
                    nextSlotAfter = nextSlotInstant,
                    priority = priority,
                )
                mediatorRef.send(command)
            }
        }
    }

    @McpTool(
        name = "delete_publication",
        description = "Delete an unpublished publication. Repeating with the same `idempotencyKey` returns " +
            "the cached result without re-running the handler.",
        generateOutputSchema = true,
    )
    suspend fun deletePublication(
        @McpToolParam(description = "Authenticated workspace id (injected from the JWT).")
        workspaceId: String,
        @McpToolParam(description = "Authenticated principal id (injected from the JWT).")
        principalId: String,
        @McpToolParam(description = "OAuth scopes granted by the access token.")
        grantedScopes: Set<String>,
        @McpToolParam(description = "Publication id to delete.")
        publicationId: String,
        @McpToolParam(description = "Optional idempotency key (1-128 chars, opaque).", required = false)
        idempotencyKey: String? = null,
    ): Mono<ToolResponse<PublicationResult>> {
        val mediatorRef = mediator
        val idempotencyGuardRef = idempotencyGuard
        val auditEmitterRef = auditEmitter
        val errorMapperRef = errorMapper
        val writeScope = MCP_WRITE_SCOPE
        val correlationIdRef = correlationId()
        return mono {
            runWriteTool(
                toolName = "delete_publication",
                workspaceId = workspaceId,
                principalId = principalId,
                grantedScopes = grantedScopes,
                requiredScope = writeScope,
                idempotencyKey = idempotencyKey,
                idempotencyGuardRef = idempotencyGuardRef,
                auditEmitterRef = auditEmitterRef,
                errorMapperRef = errorMapperRef,
                correlationIdRef = correlationIdRef,
            ) {
                val command = DeletePublicationCommand(publicationId = publicationId)
                mediatorRef.send(command)
            }
        }
    }

    @McpTool(
        name = "cancel_publication",
        description = "Cancel a scheduled or queued publication. Idempotent at the lifecycle level — " +
            "calling cancel on an already-cancelled publication returns publication_state_conflict, " +
            "which the agent MUST treat as success per ADR-0019. Repeating with the same `idempotencyKey` " +
            "returns the cached result without re-running the handler.",
        generateOutputSchema = true,
    )
    suspend fun cancelPublication(
        @McpToolParam(description = "Authenticated workspace id (injected from the JWT).")
        workspaceId: String,
        @McpToolParam(description = "Authenticated principal id (injected from the JWT).")
        principalId: String,
        @McpToolParam(description = "OAuth scopes granted by the access token.")
        grantedScopes: Set<String>,
        @McpToolParam(description = "Publication id to cancel.")
        publicationId: String,
        @McpToolParam(description = "Optional idempotency key (1-128 chars, opaque).", required = false)
        idempotencyKey: String? = null,
    ): Mono<ToolResponse<PublicationResult>> {
        val mediatorRef = mediator
        val idempotencyGuardRef = idempotencyGuard
        val auditEmitterRef = auditEmitter
        val errorMapperRef = errorMapper
        val writeScope = MCP_WRITE_SCOPE
        val correlationIdRef = correlationId()
        return mono {
            runWriteTool(
                toolName = "cancel_publication",
                workspaceId = workspaceId,
                principalId = principalId,
                grantedScopes = grantedScopes,
                requiredScope = writeScope,
                idempotencyKey = idempotencyKey,
                idempotencyGuardRef = idempotencyGuardRef,
                auditEmitterRef = auditEmitterRef,
                errorMapperRef = errorMapperRef,
                correlationIdRef = correlationIdRef,
            ) {
                val command = CancelPublicationCommand(publicationId = publicationId)
                mediatorRef.send(command)
            }
        }
    }

    @McpTool(
        name = "retry_publication",
        description = "Retry a FAILED publication. Optional `scheduleMode`, `scheduledFor`, `nextSlotAfter` " +
            "and `priority` overrides the existing values. The publication MUST be in FAILED status; " +
            "calling retry on a non-FAILED publication returns publication_state_conflict per ADR-0019. " +
            "Repeating with the same `idempotencyKey` returns the cached result without re-running the handler.",
        generateOutputSchema = true,
    )
    suspend fun retryPublication(
        @McpToolParam(description = "Authenticated workspace id (injected from the JWT).")
        workspaceId: String,
        @McpToolParam(description = "Authenticated principal id (injected from the JWT).")
        principalId: String,
        @McpToolParam(description = "OAuth scopes granted by the access token.")
        grantedScopes: Set<String>,
        @McpToolParam(description = "Publication id to retry.")
        publicationId: String,
        @McpToolParam(
            description = "Optional override schedule mode (NOW, SCHEDULED_AT, NEXT_SLOT).",
            required = false,
        )
        scheduleMode: String? = null,
        @McpToolParam(description = "Optional override scheduledFor (ISO-8601 instant).", required = false)
        scheduledFor: String? = null,
        @McpToolParam(
            description = "Optional override for NEXT_SLOT resolution (ISO-8601 instant).",
            required = false,
        )
        nextSlotAfter: String? = null,
        @McpToolParam(description = "Optional override priority flag.", required = false)
        priority: Boolean? = null,
        @McpToolParam(description = "Optional idempotency key (1-128 chars, opaque).", required = false)
        idempotencyKey: String? = null,
    ): Mono<ToolResponse<PublicationResult>> {
        val mediatorRef = mediator
        val idempotencyGuardRef = idempotencyGuard
        val auditEmitterRef = auditEmitter
        val errorMapperRef = errorMapper
        val writeScope = MCP_WRITE_SCOPE
        val correlationIdRef = correlationId()
        return mono {
            runWriteTool(
                toolName = "retry_publication",
                workspaceId = workspaceId,
                principalId = principalId,
                grantedScopes = grantedScopes,
                requiredScope = writeScope,
                idempotencyKey = idempotencyKey,
                idempotencyGuardRef = idempotencyGuardRef,
                auditEmitterRef = auditEmitterRef,
                errorMapperRef = errorMapperRef,
                correlationIdRef = correlationIdRef,
            ) {
                val mode = scheduleMode?.let { ScheduleMode.valueOf(it) }
                val scheduledInstant = scheduledFor?.let { Instant.parse(it) }
                val nextSlotInstant = nextSlotAfter?.let { Instant.parse(it) }
                val command = RetryPublicationCommand(
                    publicationId = publicationId,
                    scheduleMode = mode,
                    scheduledFor = scheduledInstant,
                    nextSlotAfter = nextSlotInstant,
                    priority = priority,
                )
                mediatorRef.send(command)
            }
        }
    }

    private suspend fun runWriteTool(
        toolName: String,
        workspaceId: String,
        principalId: String,
        grantedScopes: Set<String>,
        requiredScope: String,
        idempotencyKey: String?,
        idempotencyGuardRef: IdempotencyGuard?,
        auditEmitterRef: McpAuditEmitter?,
        errorMapperRef: McpErrorMapper,
        correlationIdRef: String,
        execute: suspend () -> PublicationResult,
    ): ToolResponse<PublicationResult> {
        if (!grantedScopes.contains(requiredScope)) {
            return deniedResponse(
                toolName,
                workspaceId,
                grantedScopes,
                requiredScope,
                auditEmitterRef,
                errorMapperRef,
                correlationIdRef,
            )
        }
        val invocation: suspend () -> PublicationResult = if (idempotencyGuardRef != null) {
            {
                idempotencyGuardRef.guard(
                    workspaceId = workspaceId,
                    principalId = principalId,
                    toolName = toolName,
                    idempotencyKey = idempotencyKey,
                    type = PublicationResult::class.java,
                    execute = execute,
                )
            }
        } else {
            execute
        }
        return runCatching { invocation() }.fold(
            onSuccess = { result ->
                successResponse(
                    toolName,
                    workspaceId,
                    grantedScopes,
                    requiredScope,
                    auditEmitterRef,
                    correlationIdRef,
                    result,
                )
            },
            onFailure = { ex ->
                errorResponse(
                    toolName,
                    workspaceId,
                    grantedScopes,
                    requiredScope,
                    auditEmitterRef,
                    errorMapperRef,
                    ex,
                )
            },
        )
    }

    private fun deniedResponse(
        toolName: String,
        workspaceId: String,
        grantedScopes: Set<String>,
        requiredScope: String,
        auditEmitterRef: McpAuditEmitter?,
        errorMapperRef: McpErrorMapper,
        correlationIdRef: String,
    ): ToolResponse<PublicationResult> {
        auditEmitterRef?.emit(
            McpToolInvocationAuditFact(
                toolName = toolName,
                scopeChecked = requiredScope,
                grantedScopes = grantedScopes,
                workspaceId = workspaceId,
                correlationId = correlationIdRef,
                outcome = McpToolInvocationOutcome.DENIED,
            ),
        )
        return ToolResponse.failure(
            errorMapperRef.mapToError(
                com.profiletailors.smp.mcp.infrastructure.McpInsufficientScopeException(requiredScope),
            ),
        )
    }

    private fun successResponse(
        toolName: String,
        workspaceId: String,
        grantedScopes: Set<String>,
        requiredScope: String,
        auditEmitterRef: McpAuditEmitter?,
        correlationIdRef: String,
        result: PublicationResult,
    ): ToolResponse<PublicationResult> {
        auditEmitterRef?.emit(
            McpToolInvocationAuditFact(
                toolName = toolName,
                scopeChecked = requiredScope,
                grantedScopes = grantedScopes,
                workspaceId = workspaceId,
                correlationId = correlationIdRef,
                outcome = McpToolInvocationOutcome.SUCCESS,
                publicationId = result.publicationId,
            ),
        )
        return ToolResponse.success(result)
    }

    private fun errorResponse(
        toolName: String,
        workspaceId: String,
        grantedScopes: Set<String>,
        requiredScope: String,
        auditEmitterRef: McpAuditEmitter?,
        errorMapperRef: McpErrorMapper,
        ex: Throwable,
    ): ToolResponse<PublicationResult> {
        val applicationError = errorMapperRef.mapToError(ex)
        auditEmitterRef?.emit(
            McpToolInvocationAuditFact(
                toolName = toolName,
                scopeChecked = requiredScope,
                grantedScopes = grantedScopes,
                workspaceId = workspaceId,
                correlationId = applicationError.correlationId,
                outcome = McpToolInvocationOutcome.ERROR,
            ),
        )
        return ToolResponse.failure(applicationError)
    }

    private fun correlationId(): String = java.util.UUID.randomUUID().toString()

    private companion object {
        const val MCP_WRITE_SCOPE = "mcp:publications:write"
    }
}
