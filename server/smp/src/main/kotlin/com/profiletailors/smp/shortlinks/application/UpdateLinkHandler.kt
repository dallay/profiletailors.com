package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.LinkVersionConflictException
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock

@Service
internal class UpdateLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val linkCachePort: LinkCachePort,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : CommandWithResultHandler<UpdateLinkCommand, LinkResult> {

    private suspend fun findExpectedLink(linkId: LinkId, ownerId: OwnerId, expectedVersion: Long) =
        linkRepository.findById(linkId, ownerId)?.also { existing ->
            if (existing.version != expectedVersion) {
                throw LinkVersionConflictException(linkId, expectedVersion, existing.version)
            }
        } ?: throw LinkNotFoundApplicationException(linkId.value.toString())

    private fun parseDestinationUrl(value: String): DestinationUrl = try {
        DestinationUrl(value)
    } catch (e: IllegalArgumentException) {
        throw InvalidDestinationUrlException(value, e.message ?: "Invalid URL", e)
    }

    override suspend fun handle(command: UpdateLinkCommand): LinkResult {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        val linkId = LinkId(command.linkId)

        val existing = findExpectedLink(linkId, ownerId, command.expectedVersion)
        val newDestination = command.destinationUrl?.let(::parseDestinationUrl) ?: existing.destinationUrl
        val newExpiresAt = if (command.destinationUrl != null || command.expiresAt != null) {
            command.expiresAt
        } else {
            existing.expiresAt
        }
        val updated = existing.updateDestination(
            newDestinationUrl = newDestination,
            newExpiresAt = newExpiresAt,
            now = clock.instant(),
        )

        val success = transactionRunner.runAtomically {
            linkRepository.updateWithVersion(updated, command.expectedVersion, ownerId)
        }

        if (!success) {
            throw LinkVersionConflictException(linkId, command.expectedVersion, existing.version)
        }

        linkCachePort.evict(shortLinksProperties.publicHost, existing.shortCode.value)
        return updated.toResult(shortLinksProperties.shortUrlBase)
    }
}
