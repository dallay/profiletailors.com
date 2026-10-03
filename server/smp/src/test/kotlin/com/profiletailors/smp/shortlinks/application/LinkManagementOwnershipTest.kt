package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class LinkManagementOwnershipTest {
    private val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
    private val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000002")
    private val ownerId = OwnerId.from(workspaceId)
    private val resourceContextProvider = mockk<ResourceContextProvider> {
        every { require() } returns ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }
    private val linkRepository = mockk<LinkRepository>()
    private val linkCachePort = mockk<LinkCachePort>(relaxed = true)
    private val idempotencyPort = mockk<com.profiletailors.smp.shortlinks.domain.IdempotencyPort>(relaxed = true)
    private val transactionRunner = mockk<AtomicTransactionRunner> {
        coEvery { runAtomically<Boolean>(any()) } coAnswers {
            firstArg<suspend () -> Boolean>().invoke()
        }
    }
    private val clock = Clock.systemUTC()
    private val properties = mockk<ShortLinksConfigProperties> {
        every { shortUrlBase } returns "https://short.example"
        every { publicHost } returns "short.example"
    }

    @Test
    fun `get does not look up or update a link in another workspace`() = runBlocking {
        assertMissing {
            GetLinkHandler(resourceContextProvider, linkRepository, properties).handle(GetLinkQuery(linkId))
        }
    }

    @Test
    fun `update does not look up or update a link in another workspace`() = runBlocking {
        assertMissing {
            UpdateLinkHandler(
                resourceContextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(UpdateLinkCommand(linkId, "https://example.com", null, 1))
        }
    }

    @Test
    fun `disable does not look up or update a link in another workspace`() = runBlocking {
        assertMissing {
            DisableLinkHandler(
                resourceContextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(DisableLinkCommand(linkId))
        }
    }

    @Test
    fun `enable does not look up or update a link in another workspace`() = runBlocking {
        assertMissing {
            EnableLinkHandler(
                resourceContextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(EnableLinkCommand(linkId))
        }
    }

    @Test
    fun `delete does not look up or update a link in another workspace`() = runBlocking {
        assertMissing {
            DeleteLinkHandler(
                resourceContextProvider,
                linkRepository,
                linkCachePort,
                transactionRunner,
                clock,
                properties,
            ).handle(DeleteLinkCommand(linkId))
        }
    }

    private suspend fun assertMissing(action: suspend () -> LinkResult) {
        coEvery { linkRepository.findById(LinkId(linkId), ownerId) } returns null

        try {
            action()
            throw AssertionError("Expected link not found")
        } catch (_: LinkNotFoundApplicationException) {
            coVerify(exactly = 1) { linkRepository.findById(LinkId(linkId), ownerId) }
            coVerify(exactly = 0) { linkRepository.updateWithVersion(any(), any(), any()) }
        }
    }
}
