package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.RetryNotificationCommand
import com.profiletailors.smp.platformadmin.application.contracts.NotificationAdminQuery
import com.profiletailors.smp.platformadmin.application.handler.RetryNotificationHandler
import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.NotificationFilters
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.platformadmin.infrastructure.persistence.ADMIN_PAGE_MAX_SIZE
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin/notifications")
@Tag(
    name = "Platform Admin - Notifications",
    description = "Platform operator notification management endpoints",
)
@SecurityRequirement(name = "bearerAuth")
class AdminNotificationController(
    private val notificationQuery: NotificationAdminQuery,
    private val retryHandler: RetryNotificationHandler,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
) {

    @Operation(
        summary = "List notifications",
        description = "Returns a paginated list of notifications with optional filters " +
            "for status, channel, template, and recipient.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Notifications retrieved successfully",
                content = [
                    Content(
                        mediaType = "application/vnd.api.v1+json",
                        schema = Schema(implementation = NotificationListResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid authentication",
                content = [Content(mediaType = "application/problem+json")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - operator lacks required platform permissions",
                content = [Content(mediaType = "application/problem+json")],
            ),
        ],
    )
    @GetMapping
    suspend fun listNotifications(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Filter by notification status (e.g., PENDING, SENT, FAILED)")
        @RequestParam(required = false) status: String?,
        @Parameter(description = "Filter by notification channel (e.g., EMAIL, SMS, PUSH)")
        @RequestParam(required = false) channel: String?,
        @Parameter(description = "Filter by template identifier")
        @RequestParam(required = false) templateId: String?,
        @Parameter(description = "Filter by recipient email or user ID")
        @RequestParam(required = false) recipient: String?,
        @Parameter(description = "Return notifications created on or after this timestamp (ISO-8601)")
        @RequestParam(required = false) createdFrom: String?,
        @Parameter(description = "Return notifications created on or before this timestamp (ISO-8601)")
        @RequestParam(required = false) createdTo: String?,
    ): ResponseEntity<NotificationListResponse> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.NOTIFICATIONS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.NOTIFICATIONS_READ)
        }

        val effectiveSize = size.coerceIn(1, ADMIN_PAGE_MAX_SIZE)
        val filters = NotificationFilters(
            status = status,
            channel = channel,
            templateId = templateId,
            recipient = recipient,
            createdFrom = createdFrom?.let { Instant.parse(it) },
            createdTo = createdTo?.let { Instant.parse(it) },
        )
        val result = notificationQuery.list(filters, page, effectiveSize)

        return ResponseEntity.ok(NotificationListResponse(result))
    }

    @Operation(
        summary = "Get notification by ID",
        description = "Returns a single notification summary by its unique identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Notification found",
                content = [
                    Content(
                        mediaType = "application/vnd.api.v1+json",
                        schema = Schema(implementation = NotificationDetailResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Notification not found",
                content = [Content(mediaType = "application/problem+json")],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid authentication",
                content = [Content(mediaType = "application/problem+json")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - operator lacks required platform permissions",
                content = [Content(mediaType = "application/problem+json")],
            ),
        ],
    )
    @GetMapping("/{notificationId}")
    suspend fun getNotification(@PathVariable notificationId: UUID): ResponseEntity<NotificationDetailResponse> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.NOTIFICATIONS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.NOTIFICATIONS_READ)
        }

        val summary = notificationQuery.findById(NotificationId(notificationId.toString()))
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(NotificationDetailResponse(summary))
    }

    @Operation(
        summary = "Retry failed notification",
        description = "Retries a previously failed notification. " +
            "Only password-recovery notifications are eligible for admin-initiated retry. " +
            "Invitation notifications must use their dedicated resend mechanism.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Notification retry initiated successfully",
                content = [
                    Content(
                        mediaType = "application/vnd.api.v1+json",
                        schema = Schema(implementation = NotificationDetailResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Notification cannot be retried (e.g., invitation template, not found)",
                content = [Content(mediaType = "application/problem+json")],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid authentication",
                content = [Content(mediaType = "application/problem+json")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - operator lacks NOTIFICATIONS_MANAGE permission",
                content = [Content(mediaType = "application/problem+json")],
            ),
        ],
    )
    @PostMapping("/{notificationId}/retry")
    suspend fun retryNotification(
        @PathVariable notificationId: UUID,
        @RequestHeader(value = "X-Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<NotificationDetailResponse> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.NOTIFICATIONS_MANAGE !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.NOTIFICATIONS_MANAGE)
        }

        val command = RetryNotificationCommand(
            notificationId = NotificationId(notificationId.toString()),
            operatorPrincipalId = operator.principalId,
            operatorRoles = operator.roles,
            idempotencyKey = idempotencyKey,
        )
        val result = retryHandler.handle(command)

        return ResponseEntity.ok(NotificationDetailResponse(result))
    }

    private suspend fun resolveOperator(): OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }

    data class NotificationListResponse(val data: List<NotificationDetailResponse.NotificationData>, val meta: Meta) {
        constructor(paged: PagedResult<NotificationSummary>) : this(
            data = paged.items.map { NotificationDetailResponse.NotificationData(it) },
            meta = Meta(
                currentPage = paged.page,
                pageSize = paged.size,
                totalElements = paged.totalElements,
                totalPages = paged.totalPages,
            ),
        )

        data class Meta(val currentPage: Int, val pageSize: Int, val totalElements: Long, val totalPages: Int)
    }

    data class NotificationDetailResponse(val data: NotificationData) {
        constructor(summary: NotificationSummary) : this(NotificationData(summary))

        data class NotificationData(
            val id: String,
            val channel: String,
            val templateId: String,
            val recipient: String,
            val status: String,
            val errorMessage: String?,
            val createdAt: String,
            val sentAt: String?,
            val failedAt: String?,
            val redactedPayload: Map<String, Any?>,
        ) {
            constructor(summary: NotificationSummary) : this(
                id = summary.id,
                channel = summary.channel,
                templateId = summary.templateId,
                recipient = summary.recipient,
                status = summary.status,
                errorMessage = summary.errorMessage,
                createdAt = summary.createdAt.toString(),
                sentAt = summary.sentAt?.toString(),
                failedAt = summary.failedAt?.toString(),
                redactedPayload = summary.redactedPayload,
            )
        }
    }
}
