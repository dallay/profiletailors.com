package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.LinkVersionConflictException
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock

@Service
internal class DisableLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val linkCachePort: LinkCachePort,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : CommandWithResultHandler<DisableLinkCommand, LinkResult> {

    override suspend fun handle(command: DisableLinkCommand): LinkResult {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        val linkId = LinkId(command.linkId)

        val existing = linkRepository.findById(linkId, ownerId)
            ?: throw LinkNotFoundApplicationException(command.linkId.toString())

        val disabled = existing.disable(clock.instant())

        val disabledUpdated = transactionRunner.runAtomically {
            linkRepository.updateWithVersion(disabled, existing.version, ownerId)
        }
        if (!disabledUpdated) {
            throw LinkVersionConflictException(linkId, existing.version, existing.version)
        }

        linkCachePort.evict(shortLinksProperties.publicHost, existing.shortCode.value)
        return disabled.toResult(shortLinksProperties.shortUrlBase)
    }
}

@Service
internal class EnableLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val linkCachePort: LinkCachePort,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : CommandWithResultHandler<EnableLinkCommand, LinkResult> {

    override suspend fun handle(command: EnableLinkCommand): LinkResult {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        val linkId = LinkId(command.linkId)

        val existing = linkRepository.findById(linkId, ownerId)
            ?: throw LinkNotFoundApplicationException(command.linkId.toString())

        val enabled = existing.enable(clock.instant())

        val enabledUpdated = transactionRunner.runAtomically {
            linkRepository.updateWithVersion(enabled, existing.version, ownerId)
        }
        if (!enabledUpdated) {
            throw LinkVersionConflictException(linkId, existing.version, existing.version)
        }

        linkCachePort.evict(shortLinksProperties.publicHost, existing.shortCode.value)
        return enabled.toResult(shortLinksProperties.shortUrlBase)
    }
}

@Service
internal class DeleteLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val linkCachePort: LinkCachePort,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : CommandWithResultHandler<DeleteLinkCommand, LinkResult> {

    override suspend fun handle(command: DeleteLinkCommand): LinkResult {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        val linkId = LinkId(command.linkId)

        val existing = linkRepository.findById(linkId, ownerId)
            ?: throw LinkNotFoundApplicationException(command.linkId.toString())

        val deleted = existing.softDelete(clock.instant())

        val deletedUpdated = transactionRunner.runAtomically {
            linkRepository.updateWithVersion(deleted, existing.version, ownerId)
        }
        if (!deletedUpdated) {
            throw LinkVersionConflictException(linkId, existing.version, existing.version)
        }

        linkCachePort.evict(shortLinksProperties.publicHost, existing.shortCode.value)
        return deleted.toResult(shortLinksProperties.shortUrlBase)
    }
}
