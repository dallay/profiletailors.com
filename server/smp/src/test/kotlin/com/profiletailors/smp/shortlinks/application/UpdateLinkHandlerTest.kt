package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
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

internal class UpdateLinkHandlerTest {
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
    fun `updates destination and evicts cache`() = runTest {
        val existing = link(expiresAt = null)
        coEvery { linkRepository.findById(linkId, ownerId) } returns existing
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = handler().handle(UpdateLinkCommand(linkId.value, "https://example.com/new", null, 1))

        assertEquals("https://example.com/new", result.destinationUrl)
        assertEquals(2, result.version)
        coVerify(exactly = 1) { linkCachePort.evict("short.example", "AbC123") }
    }

    @Test
    fun `preserves existing expiry when only destination changes`() = runTest {
        val existing = link(expiresAt = now.plusSeconds(3600))
        coEvery { linkRepository.findById(linkId, ownerId) } returns existing
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = handler().handle(UpdateLinkCommand(linkId.value, "https://example.com/new", null, 1))

        assertEquals(now.plusSeconds(3600), result.expiresAt)
    }

    @Test
    fun `sets new expiry when provided`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(expiresAt = null)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns true

        val result = handler().handle(
            UpdateLinkCommand(linkId.value, "https://example.com/new", now.plusSeconds(60), 1),
        )

        assertEquals(now.plusSeconds(60), result.expiresAt)
    }

    @Test
    fun `rejects stale expected version`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(expiresAt = null)

        shouldThrow<LinkVersionConflictException> {
            handler().handle(UpdateLinkCommand(linkId.value, "https://example.com/new", null, 0))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
        coVerify(exactly = 0) { linkCachePort.evict(any(), any()) }
    }

    @Test
    fun `rejects unknown link`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns null

        shouldThrow<LinkNotFoundApplicationException> {
            handler().handle(UpdateLinkCommand(linkId.value, "https://example.com/new", null, 1))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
    }

    @Test
    fun `rejects invalid destination`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(expiresAt = null)

        shouldThrow<InvalidDestinationUrlException> {
            handler().handle(UpdateLinkCommand(linkId.value, "not a url", null, 1))
        }
        coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), ownerId) }
    }

    @Test
    fun `rejects lost update without evicting`() = runTest {
        coEvery { linkRepository.findById(linkId, ownerId) } returns link(expiresAt = null)
        coEvery { linkRepository.updateWithVersion(any(), 1, ownerId) } returns false

        shouldThrow<LinkVersionConflictException> {
            handler().handle(UpdateLinkCommand(linkId.value, "https://example.com/new", null, 1))
        }
        coVerify(exactly = 0) { linkCachePort.evict(any(), any()) }
    }

    private fun link(expiresAt: Instant?) = Link.create(
        id = linkId,
        ownerId = ownerId,
        domainId = DomainId.fromHost("short.example"),
        shortCode = ShortCode("AbC123"),
        destinationUrl = DestinationUrl("https://example.com/path"),
        expiresAt = expiresAt,
        now = now,
    )

    private fun handler() = UpdateLinkHandler(
        contextProvider,
        linkRepository,
        linkCachePort,
        transactionRunner,
        clock,
        properties,
    )
}
