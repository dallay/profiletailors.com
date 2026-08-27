package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.application.CalendarResponse
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.ConnectedChannelsResponse
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.CreateRecurringScheduleCommand
import com.profiletailors.smp.publishing.application.DeletePublicationCommand
import com.profiletailors.smp.publishing.application.DeleteRecurringScheduleCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.InitiateLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.LinkedInConnectionInitiationResult
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.application.ListProviderCatalogQuery
import com.profiletailors.smp.publishing.application.ListPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsResponse
import com.profiletailors.smp.publishing.application.ListRecurringSchedulesQuery
import com.profiletailors.smp.publishing.application.ProviderCatalogResponse
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.RecurringScheduleResult
import com.profiletailors.smp.publishing.application.RecurringSchedulesResponse
import com.profiletailors.smp.publishing.application.ReschedulePublicationCommand
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.application.SocialConnectionResult
import com.profiletailors.smp.publishing.application.SocialContentCalendarResponse
import com.profiletailors.smp.publishing.application.SocialContentPostQuery
import com.profiletailors.smp.publishing.application.SocialContentSyncCommand
import com.profiletailors.smp.publishing.application.SocialContentSyncResult
import com.profiletailors.smp.publishing.application.UpdateRecurringScheduleCommand
import com.profiletailors.smp.publishing.application.WorkspaceSocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.RecurrenceFrequency
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.infrastructure.events.ChannelEventStreamRegistry
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/social-content"])
@Tag(name = "Publishing Social Content", description = "Workspace social-content synchronization endpoints")
class SocialContentController(private val mediator: Mediator) {
    /**
         * Synchronizes social content for the requested actor.
         *
         * @param request The request containing the actor identifier.
         * @return The result of the social content synchronization.
         */
        @Operation(summary = "Synchronize workspace social content")
    @PostMapping("/sync", consumes = ["application/json"], version = "1")
    suspend fun sync(@Valid @RequestBody request: SocialContentSyncRequest): SocialContentSyncResult =
        mediator.send(SocialContentSyncCommand(actorId = request.actorId))

    /**
         * Retrieves an imported social content post by its external identifier.
         *
         * @param externalPostId The external identifier of the post.
         * @return The imported social content post.
         */
        @Operation(summary = "Get an imported workspace social content post")
    @GetMapping("/posts/{externalPostId}", version = "1")
    suspend fun post(@PathVariable externalPostId: String): SocialPost =
        mediator.send(SocialContentPostQuery(externalPostId))

    /**
     * Lists imported social content within a specified date range.
     *
     * @param from The start of the date range.
     * @param to The end of the date range.
     * @param actorId The optional actor identifier used to filter content.
     * @param lifecycle The optional post lifecycle used to filter content.
     * @param cursor The optional pagination cursor.
     * @param limit The maximum number of content items to return, from 1 to 100.
     * @return The imported social content matching the specified filters.
     * @throws PublicationValidationException If `limit` is outside the range 1 to 100.
     */
    @Operation(summary = "List imported workspace social content for a date range")
    @GetMapping("/calendar", version = "1")
    suspend fun calendar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @RequestParam(required = false) actorId: String? = null,
        @RequestParam(required = false) lifecycle: PostLifecycle? = null,
        @RequestParam(required = false) cursor: String? = null,
        @RequestParam(required = false, defaultValue = "50") limit: Int = 50,
    ): SocialContentCalendarResponse {
        if (limit !in MIN_LIMIT..MAX_LIMIT) {
            throw PublicationValidationException("limit must be between $MIN_LIMIT and $MAX_LIMIT, got $limit")
        }
        return mediator.send(
            WorkspaceSocialContentCalendarQuery(
                from = from,
                to = to,
                actorId = actorId,
                lifecycle = lifecycle,
                cursor = cursor?.let(::PageCursor),
                limit = limit,
            ),
        )
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}

data class SocialContentSyncRequest(@field:NotBlank val actorId: String)

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/linkedin/connections"])
@Tag(name = "Publishing Connections", description = "Social publishing connection endpoints")
class PublishingConnectionController(private val mediator: Mediator) {
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
    private val linkedInPublishingProperties: LinkedInPublishingProperties,
) {
    @Operation(summary = "List connected publishing channels")
    @GetMapping(version = "1")
    suspend fun listChannels(
        @RequestParam(required = false) status: SocialConnectionStatus? = null,
    ): ConnectedChannelsResponse = mediator.send(ListConnectedChannelsQuery(status = status))

    @Operation(summary = "List workspace-resolved publishing providers")
    @GetMapping("/providers", version = "1")
    suspend fun listConfiguredProviders(): ProviderCatalogHttpResponse =
        mediator.send(ListProviderCatalogQuery).toHttpResponse()

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

data class ProviderCatalogHttpResponse(val providers: List<ProviderCatalogItemHttpResponse>)

data class ProviderCatalogItemHttpResponse(
    val provider: String,
    val accountKinds: Set<String>,
    val state: String,
    val reason: String?,
    val channelLimit: Int?,
    val connectedChannelCount: Int,
    val canConnectMore: Boolean,
)

private fun ProviderCatalogResponse.toHttpResponse(): ProviderCatalogHttpResponse = ProviderCatalogHttpResponse(
    providers = providers.map(ProviderCatalogItem::toHttpResponse),
)

private fun ProviderCatalogItem.toHttpResponse(): ProviderCatalogItemHttpResponse = ProviderCatalogItemHttpResponse(
    provider = provider.name.lowercase(Locale.ROOT),
    accountKinds = accountKinds,
    state = state.name,
    reason = reason?.name,
    channelLimit = channelLimit,
    connectedChannelCount = connectedChannelCount,
    canConnectMore = canConnectMore,
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
class PublishingPublicationController(private val mediator: Mediator) {
    @Operation(summary = "Create a publication")
    @PostMapping(consumes = ["application/json"], version = "1")
    suspend fun createPublication(@Valid @RequestBody request: PublicationUpsertRequest): PublicationResult =
        mediator.send(
            CreatePublicationCommand(
                socialAccountId = request.socialAccountId,
                title = request.title,
                bodyText = request.bodyText,
                assetIds = request.assetIds ?: emptyList(),
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
    suspend fun cancelPublication(@PathVariable publicationId: String): PublicationResult =
        mediator.send(CancelPublicationCommand(publicationId))

    @Operation(summary = "Delete an unpublished publication")
    @DeleteMapping("/{publicationId}", version = "1")
    suspend fun deletePublication(@PathVariable publicationId: String): PublicationResult =
        mediator.send(DeletePublicationCommand(publicationId))

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
    suspend fun quickCreatePublication(@Valid @RequestBody request: QuickCreateRequest): PublicationResult =
        mediator.send(
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

    /**
     * Lists publications with optional status, account, date-range, and pagination filters.
     *
     * @param status The publication status filter.
     * @param socialAccountId The social account identifier filter.
     * @param from The start of the publication date range.
     * @param to The end of the publication date range.
     * @param limit The maximum number of publications to return.
     * @param offset The number of publications to skip.
     * @return The filtered publications.
     * @throws PublicationValidationException If `limit` is outside 1–100 or `offset` is negative.
     */
    @Operation(summary = "List publications with optional filtering")
    @GetMapping(version = "1")
    suspend fun listPublications(
        @RequestParam(required = false) status: PublicationStatus? = null,
        @RequestParam(required = false) socialAccountId: String? = null,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant? = null,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant? = null,
        @RequestParam(required = false, defaultValue = "50") limit: Int = 50,
        @RequestParam(required = false, defaultValue = "0") offset: Int = 0,
    ): ListPublicationsResponse {
        if (limit !in MIN_LIMIT..MAX_LIMIT) {
            throw PublicationValidationException("limit must be between $MIN_LIMIT and $MAX_LIMIT, got $limit")
        }
        if (offset < 0) {
            throw PublicationValidationException("offset must be non-negative, got $offset")
        }
        return mediator.send(
            ListPublicationsQuery(
                status = status,
                socialAccountId = socialAccountId,
                from = from,
                to = to,
                limit = limit,
                offset = offset,
            ),
        )
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
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
    val assetIds: List<String>? = null,
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
    /**
         * Resolves the required schedule mode for rescheduling a publication.
         *
         * @return The configured schedule mode.
         * @throws PublicationValidationException If no schedule mode is configured.
         */
        fun requiredScheduleMode(): ScheduleMode = scheduleMode?.let(ScheduleMode::valueOf)
        ?: throw PublicationValidationException("scheduleMode is required for reschedule.")
}

@Schema(description = "List of configured publishing providers")
data class ConfiguredProvidersResponse(val providers: List<ConfiguredProvider>)

@Schema(description = "A configured publishing provider")
data class ConfiguredProvider(val name: String, val configured: Boolean)

@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recurring")
@Tag(name = "Recurring Posts", description = "Recurring publication schedules")
class RecurringScheduleController(
    private val mediator: Mediator,
    private val resourceContextProvider: ResourceContextProvider,
) {
    @PostMapping(consumes = ["application/json"])
    suspend fun create(
        @PathVariable workspaceId: String,
        @Valid @RequestBody request: RecurringScheduleRequest,
    ): RecurringScheduleResult {
        requireWorkspacePath(workspaceId)
        return mediator.send(request.toCreateCommand())
    }

    @GetMapping
    suspend fun list(@PathVariable workspaceId: String): RecurringSchedulesResponse {
        requireWorkspacePath(workspaceId)
        return mediator.send(ListRecurringSchedulesQuery)
    }

    @PatchMapping("/{id}", consumes = ["application/json"])
    suspend fun update(
        @PathVariable workspaceId: String,
        @PathVariable id: String,
        @Valid @RequestBody request: RecurringSchedulePatchRequest,
    ): RecurringScheduleResult {
        requireWorkspacePath(workspaceId)
        return mediator.send(request.toCommand(id))
    }

    @DeleteMapping("/{id}")
    suspend fun cancel(@PathVariable workspaceId: String, @PathVariable id: String) {
        requireWorkspacePath(workspaceId)
        mediator.send(DeleteRecurringScheduleCommand(id))
    }

    /**
     * Validates that the path workspace matches the authenticated workspace.
     *
     * @param pathWorkspaceId The workspace identifier from the request path.
     * @throws PublicationValidationException If the path workspace differs from the authenticated workspace.
     */
    private fun requireWorkspacePath(pathWorkspaceId: String) {
        val contextWorkspaceId = resourceContextProvider.requireWorkspaceContext().workspaceId
        if (pathWorkspaceId != contextWorkspaceId) {
            throw PublicationValidationException("Workspace path does not match the authenticated workspace.")
        }
    }
}

data class RecurringScheduleRequest(
    val templatePostId: String,
    val frequency: String,
    val interval: Int = 1,
    val daysOfWeek: Set<Int> = emptySet(),
    val dayOfMonth: Int? = null,
    val endDate: java.time.LocalDate? = null,
    val maxOccurrences: Int? = null,
    val startsAt: Instant,
    val timezone: String = "UTC",
) {
    fun toCreateCommand() = CreateRecurringScheduleCommand(
        templatePostId,
        RecurrenceRule(
            RecurrenceFrequency.valueOf(frequency.uppercase()),
            interval,
            daysOfWeek,
            dayOfMonth,
            endDate,
            maxOccurrences,
        ),
        startsAt,
        timezone,
    )
}

data class RecurringSchedulePatchRequest(
    val frequency: String? = null,
    val interval: Int? = null,
    val daysOfWeek: Set<Int>? = null,
    val dayOfMonth: Int? = null,
    val endDate: java.time.LocalDate? = null,
    val maxOccurrences: Int? = null,
    val startsAt: Instant? = null,
    val timezone: String? = null,
    val status: String? = null,
) {
    fun toCommand(id: String) = UpdateRecurringScheduleCommand(
        id,
        frequency?.let {
            RecurrenceRule(
                RecurrenceFrequency.valueOf(it.uppercase()),
                interval ?: 1,
                daysOfWeek ?: emptySet(),
                dayOfMonth,
                endDate,
                maxOccurrences,
            )
        },
        startsAt,
        timezone,
        status?.let { RecurringScheduleStatus.valueOf(it.uppercase()) },
    )
}
