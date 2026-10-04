package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.LinkStateTransitionException
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.LinkVersionConflictException
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class LinkStateHandlersTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
    private val ownerId = OwnerId.from(workspaceId)
    private val linkId = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000002"))
    private val contextProvider = mockk<ResourceContextProvider> {
        every { require() } returns ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }
    private val linkRepository = mockk<LinkRepository>()
    private val linkCachePort = mockk<LinkCachePort>(relaxed = true)
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
    private val properties = mockk<ShortLinksConfigProperties> {
        every { publicHost } returns "short.example"
        every { shortUrlBase } returns "https://short.example"
    }

    @Test
    fun `disables active link and evicts cache`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(LinkStatus.ACTIVE)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = DisableLinkHandler(
            contextProvider,
            linkRepository,
            linkCachePort,
            transactionRunner,
            clock,
            properties,
        ).handle(DisableLinkCommand(linkId.value))

        assertEquals("DISABLED", result.status)
        coVerify(exactly = 1) { linkCachePort.evict("short.example", "AbC123") }
    }

    @Test
    fun `rejects disabling unknown link`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns null

        shouldThrow<LinkNotFoundApplicationException> {
            DisableLinkHandler(
                contextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(DisableLinkCommand(linkId.value))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
    }

    @Test
    fun `rejects disabling without update and without evicting`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(LinkStatus.ACTIVE)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns false

        shouldThrow<LinkVersionConflictException> {
            DisableLinkHandler(
                contextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(DisableLinkCommand(linkId.value))
        }
        coVerify(exactly = 0) { linkCachePort.evict(any(), any()) }
    }

    @Test
    fun `enables disabled link`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(LinkStatus.DISABLED)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = EnableLinkHandler(
            contextProvider,
            linkRepository,
            linkCachePort,
            transactionRunner,
            clock,
            properties,
        ).handle(EnableLinkCommand(linkId.value))

        assertEquals("ACTIVE", result.status)
        coVerify(exactly = 1) { linkCachePort.evict("short.example", "AbC123") }
    }

    @Test
    fun `rejects enabling expired link`() = runTest {
        val expired = link(LinkStatus.DISABLED).copy(expiresAt = now.minusSeconds(1))
        coEvery { linkRepository.findById(linkId, ownerId) } returns expired

        shouldThrow<LinkStateTransitionException> {
            EnableLinkHandler(
                contextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(EnableLinkCommand(linkId.value))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
    }

    @Test
    fun `deletes link and evicts cache`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(LinkStatus.ACTIVE)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = DeleteLinkHandler(
            contextProvider,
            linkRepository,
            linkCachePort,
            transactionRunner,
            clock,
            properties,
        ).handle(DeleteLinkCommand(linkId.value))

        assertEquals("DELETED", result.status)
        coVerify(exactly = 1) { linkCachePort.evict("short.example", "AbC123") }
    }

    @Test
    fun `rejects deleting twice`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(LinkStatus.DELETED)

        shouldThrow<LinkStateTransitionException> {
            DeleteLinkHandler(
                contextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(DeleteLinkCommand(linkId.value))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
    }

    private fun link(status: LinkStatus) = Link.create(
        id = linkId,
        ownerId = ownerId,
        domainId = DomainId.fromHost("short.example"),
        shortCode = ShortCode("AbC123"),
        destinationUrl = DestinationUrl("https://example.com/path"),
        expiresAt = null,
        now = now,
    ).copy(status = status)
}
