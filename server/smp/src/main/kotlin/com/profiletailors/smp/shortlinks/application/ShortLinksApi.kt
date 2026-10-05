package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import java.time.Instant
import java.util.UUID

data class LinkResult(
    val id: UUID,
    val shortCode: String,
    val shortUrl: String,
    val destinationUrl: String,
    val status: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val version: Long,
)

data class CreateLinkCommand(
    val destinationUrl: String,
    val customAlias: String?,
    val expiresAt: Instant?,
    val idempotencyKey: String?,
) : CommandWithResult<LinkResult>

data class UpdateLinkCommand(
    val linkId: UUID,
    val destinationUrl: String?,
    val expiresAt: Instant?,
    val expectedVersion: Long,
) : CommandWithResult<LinkResult>

data class DisableLinkCommand(val linkId: UUID) : CommandWithResult<LinkResult>

data class EnableLinkCommand(val linkId: UUID) : CommandWithResult<LinkResult>

data class DeleteLinkCommand(val linkId: UUID) : CommandWithResult<LinkResult>

data class GetLinkQuery(val linkId: UUID) : Query<LinkResult>

data class ListLinksQuery(val limit: Int = 20, val afterCreatedAt: Instant? = null) : Query<ListLinksResponse>

data class ListLinksResponse(val links: List<LinkResult>)

data class ResolveLinkQuery(val domain: String, val shortCode: String) : Query<ResolveResult>

data class ResolveResult(val destinationUrl: String, val status: String, val linkId: UUID)
