package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.CreateLinkCommand
import com.profiletailors.smp.shortlinks.application.DeleteLinkCommand
import com.profiletailors.smp.shortlinks.application.DisableLinkCommand
import com.profiletailors.smp.shortlinks.application.EnableLinkCommand
import com.profiletailors.smp.shortlinks.application.GetLinkQuery
import com.profiletailors.smp.shortlinks.application.LinkResult
import com.profiletailors.smp.shortlinks.application.UpdateLinkCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Short Links", description = "Link management endpoints")
class LinkManagementController(private val mediator: Mediator) {

    @Operation(summary = "Create a short link")
    @PostMapping(consumes = ["application/json"])
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createLink(
        @Valid @RequestBody request: CreateLinkRequest,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
    ): LinkResult = mediator.send(
        CreateLinkCommand(
            destinationUrl = request.destinationUrl,
            customAlias = request.customAlias,
            expiresAt = request.expiresAt,
            idempotencyKey = idempotencyKey,
        ),
    )

    @Operation(summary = "Get link by ID")
    @GetMapping("/{linkId}")
    suspend fun getLink(@PathVariable linkId: UUID): LinkResult = mediator.send(GetLinkQuery(linkId))

    @Operation(summary = "Update link destination or expiry")
    @PatchMapping("/{linkId}", consumes = ["application/json"])
    suspend fun updateLink(
        @PathVariable linkId: UUID,
        @RequestHeader("If-Match") ifMatch: Long,
        @Valid @RequestBody request: UpdateLinkRequest,
    ): LinkResult = mediator.send(
        UpdateLinkCommand(
            linkId = linkId,
            destinationUrl = request.destinationUrl,
            expiresAt = request.expiresAt,
            expectedVersion = ifMatch,
        ),
    )

    @Operation(summary = "Disable link")
    @PostMapping("/{linkId}/disable")
    suspend fun disableLink(@PathVariable linkId: UUID): LinkResult = mediator.send(DisableLinkCommand(linkId))

    @Operation(summary = "Enable link")
    @PostMapping("/{linkId}/enable")
    suspend fun enableLink(@PathVariable linkId: UUID): LinkResult = mediator.send(EnableLinkCommand(linkId))

    @Operation(summary = "Delete link")
    @DeleteMapping("/{linkId}")
    suspend fun deleteLink(@PathVariable linkId: UUID): LinkResult = mediator.send(DeleteLinkCommand(linkId))
}

data class CreateLinkRequest(
    @field:NotBlank
    val destinationUrl: String,
    val customAlias: String? = null,
    val expiresAt: Instant? = null,
)

data class UpdateLinkRequest(val destinationUrl: String? = null, val expiresAt: Instant? = null)
