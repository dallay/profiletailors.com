package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.publishing.application.CalendarResponse
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.InitiateLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.LinkedInConnectionInitiationResult
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.application.ConnectedChannelsResponse
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.ReschedulePublicationCommand
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.application.SocialConnectionResult
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.infrastructure.events.ChannelEventStreamRegistry
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/linkedin/connections"])
@Tag(name = "Publishing Connections", description = "Social publishing connection endpoints")
class PublishingConnectionController(
    private val mediator: Mediator,
) {
    @Operation(summary = "Initiate LinkedIn profile connection")
    @PostMapping("/initiate", consumes = ["application/json"], version = "1")
    suspend fun initiateLinkedInConnection(
        @Valid @RequestBody request: LinkedInConnectionInitiationRequest,
    ): LinkedInConnectionInitiationResult = mediator.send(
        InitiateLinkedInConnectionCommand(
            redirectUri = request.redirectUri,
        ),
    )

    @Operation(summary = "Complete LinkedIn profile connection")
    @PostMapping("/complete", consumes = ["application/json"], version = "1")
    suspend fun completeLinkedInConnection(
        @Valid @RequestBody request: LinkedInConnectionCompletionRequest,
    ): SocialConnectionResult = mediator.send(
        CompleteLinkedInConnectionCommand(
            authorizationCode = request.authorizationCode,
            redirectUri = request.redirectUri,
            state = request.state,
        ),
    )
}

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/channels"])
@Tag(name = "Publishing Channels", description = "Workspace-scoped connected channel endpoints")
class PublishingChannelController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
    private val channelEventStreamRegistry: ChannelEventStreamRegistry,
) {
    @Operation(summary = "List connected publishing channels")
    @GetMapping(version = "1")
    suspend fun listChannels(
        @RequestParam(required = false) status: SocialConnectionStatus? = null,
    ): ConnectedChannelsResponse = mediator.send(ListConnectedChannelsQuery(status = status))

    @Operation(summary = "Stream connected channel change notifications")
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): Flux<ServerSentEvent<ChannelEventResponse>> {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val channelEvents = channelEventStreamRegistry.stream()
            .filter { it.workspaceId == workspaceId }
            .map { event ->
                ServerSentEvent.builder(event.toResponse())
                    .event(event.type.eventName())
                    .build()
            }
        val heartbeats = Flux.interval(Duration.ofSeconds(20))
            .map {
                ServerSentEvent.builder<ChannelEventResponse>()
                    .event("heartbeat")
                    .build()
            }
        return Flux.merge(channelEvents, heartbeats)
    }
}

data class ChannelEventResponse(
    val type: String,
    val workspaceId: String,
    val socialAccountId: String?,
    val occurredAt: Instant,
)

private fun ChannelEvent.toResponse(): ChannelEventResponse = ChannelEventResponse(
    type = type.eventName(),
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    occurredAt = occurredAt,
)

private fun ChannelEventType.eventName(): String = when (this) {
    ChannelEventType.CONNECTED_CHANNEL_UPDATED -> "connected-channel.updated"
    ChannelEventType.CONNECTED_CHANNEL_REMOVED -> "connected-channel.removed"
}

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/publications"])
@Tag(name = "Publishing Publications", description = "Social publication lifecycle endpoints")
class PublishingPublicationController(
    private val mediator: Mediator,
) {
    @Operation(summary = "Create a publication")
    @PostMapping(consumes = ["application/json"], version = "1")
    suspend fun createPublication(
        @Valid @RequestBody request: PublicationUpsertRequest,
    ): PublicationResult = mediator.send(
        CreatePublicationCommand(
            socialAccountId = request.socialAccountId,
            title = request.title,
            bodyText = request.bodyText,
            assetIds = request.assetIds,
            scheduleMode = request.toScheduleMode(),
            scheduledFor = request.scheduledFor,
            nextSlotAfter = request.nextSlotAfter,
            priority = request.priority,
        ),
    )

    @Operation(summary = "Edit a publication before delivery")
    @PatchMapping("/{publicationId}", consumes = ["application/json"], version = "1")
    suspend fun editPublication(
        @Parameter(description = "Publication id", example = "pub_123")
        @PathVariable publicationId: String,
        @Valid @RequestBody request: PublicationUpsertRequest,
    ): PublicationResult = mediator.send(
        EditPublicationCommand(
            publicationId = publicationId,
            title = request.title,
            bodyText = request.bodyText,
            assetIds = request.assetIds,
            scheduleMode = request.toScheduleMode(),
            scheduledFor = request.scheduledFor,
            nextSlotAfter = request.nextSlotAfter,
            priority = request.priority,
        ),
    )

    @Operation(summary = "Cancel a queued or scheduled publication")
    @PostMapping("/{publicationId}/cancel", version = "1")
    suspend fun cancelPublication(
        @PathVariable publicationId: String,
    ): PublicationResult = mediator.send(CancelPublicationCommand(publicationId))

    @Operation(summary = "Retry a failed publication")
    @PostMapping("/{publicationId}/retry", consumes = ["application/json"], version = "1")
    suspend fun retryPublication(
        @PathVariable publicationId: String,
        @Valid @RequestBody request: PublicationRescheduleRequest,
    ): PublicationResult = mediator.send(
        RetryPublicationCommand(
            publicationId = publicationId,
            scheduleMode = request.scheduleMode?.let(ScheduleMode::valueOf),
            scheduledFor = request.scheduledFor,
            nextSlotAfter = request.nextSlotAfter,
            priority = request.priority,
        ),
    )

    @Operation(summary = "Get calendar publications within a date range")
    @GetMapping("/calendar", version = "1")
    suspend fun getCalendar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @RequestParam(required = false) status: PublicationStatus? = null,
        @RequestParam(required = false) socialAccountId: String? = null,
        @RequestParam(required = false, defaultValue = "UTC") timezone: String = "UTC",
    ): CalendarResponse {
        val validZoneId = try {
            ZoneId.of(timezone)
        } catch (e: DateTimeException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid timezone: '$timezone'",
                e,
            )
        }
        return mediator.send(
            GetCalendarPublicationsQuery(
                from = from,
                to = to,
                status = status,
                socialAccountId = socialAccountId,
                timezone = validZoneId.id,
            ),
        )
    }

    @Operation(summary = "Quick-create a scheduled publication")
    @PostMapping("/quick-create", consumes = ["application/json"], version = "1")
    suspend fun quickCreatePublication(
        @Valid @RequestBody request: QuickCreateRequest,
    ): PublicationResult = mediator.send(
        CreatePublicationCommand(
            socialAccountId = request.socialAccountId,
            title = request.title,
            bodyText = request.bodyText,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = request.scheduledFor,
            assetIds = emptyList(),
            priority = request.priority,
        ),
    )

    @Operation(summary = "Reschedule an editable publication (PATCH)")
    @PatchMapping("/{publicationId}/reschedule", consumes = ["application/json"], version = "1")
    suspend fun patchReschedulePublication(
        @PathVariable publicationId: String,
        @Valid @RequestBody request: PublicationRescheduleRequest,
    ): PublicationResult = mediator.send(
        ReschedulePublicationCommand(
            publicationId = publicationId,
            scheduleMode = request.requiredScheduleMode(),
            scheduledFor = request.scheduledFor,
            nextSlotAfter = request.nextSlotAfter,
            priority = request.priority,
        ),
    )

    @Operation(summary = "Placeholder list publications endpoint")
    @GetMapping(version = "1")
    suspend fun listPlaceholder(): Map<String, String> = mapOf("status" to "not-yet-implemented")
}

@Schema(description = "LinkedIn connection initiation request")
data class LinkedInConnectionInitiationRequest(
    @field:NotBlank
    val redirectUri: String,
)

@Schema(description = "LinkedIn connection completion request")
data class LinkedInConnectionCompletionRequest(
    @field:NotBlank
    val authorizationCode: String,
    @field:NotBlank
    val redirectUri: String,
    @field:NotBlank
    val state: String,
)

@Schema(description = "Publication create or edit request")
data class PublicationUpsertRequest(
    @field:NotBlank
    val socialAccountId: String,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String> = emptyList(),
    val scheduleMode: String,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val scheduledFor: Instant? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) {
    fun toScheduleMode(): ScheduleMode = ScheduleMode.valueOf(scheduleMode)
}

@Schema(description = "Quick-create publication request (scheduled with empty assets)")
data class QuickCreateRequest(
    @field:NotBlank
    val socialAccountId: String,
    val title: String? = null,
    val bodyText: String? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val scheduledFor: Instant,
    val priority: Boolean = false,
)

@Schema(description = "Publication retry or reschedule request")
data class PublicationRescheduleRequest(
    val scheduleMode: String? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val scheduledFor: Instant? = null,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val nextSlotAfter: Instant? = null,
    val priority: Boolean? = null,
) {
    fun requiredScheduleMode(): ScheduleMode =
        scheduleMode?.let(ScheduleMode::valueOf)
            ?: throw IllegalArgumentException("scheduleMode is required for reschedule.")
}
