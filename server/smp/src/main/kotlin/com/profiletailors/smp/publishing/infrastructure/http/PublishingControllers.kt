package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.ReschedulePublicationCommand
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.application.SocialConnectionResult
import com.profiletailors.smp.publishing.domain.ScheduleMode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Validated
@RestController
@RequestMapping(value = ["/api/publishing/linkedin/connections"])
@Tag(name = "Publishing Connections", description = "Social publishing connection endpoints")
class PublishingConnectionController(
    private val mediator: Mediator,
) {
    @Operation(summary = "Complete LinkedIn profile connection")
    @PostMapping("/complete", consumes = ["application/json"], version = "1")
    suspend fun completeLinkedInConnection(
        @Valid @RequestBody request: LinkedInConnectionCompletionRequest,
    ): SocialConnectionResult = mediator.send(
        CompleteLinkedInConnectionCommand(
            authorizationCode = request.authorizationCode,
            redirectUri = request.redirectUri,
        ),
    )
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

    @Operation(summary = "Reschedule an editable publication")
    @PostMapping("/{publicationId}/reschedule", consumes = ["application/json"], version = "1")
    suspend fun reschedulePublication(
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

@Schema(description = "LinkedIn connection completion request")
data class LinkedInConnectionCompletionRequest(
    @field:NotBlank
    val authorizationCode: String,
    @field:NotBlank
    val redirectUri: String,
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
