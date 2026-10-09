package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkClickMetricsRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.WorkspaceLinkClickMetrics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class GetLinkMetricsHandlerTest {
    private val workspaceId = "0199b1ca-0000-7000-8000-000000000001"
    private val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000002")
    private val ownerId = OwnerId(UUID.fromString(workspaceId))
    private val contexts = mockk<ResourceContextProvider> {
        every { require() } returns ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }
    private val metricsRepository = mockk<LinkClickMetricsRepository>()
    private val properties = mockk<ShortLinksConfigProperties> {
        every { shortUrlBase } returns "https://short.example"
    }
    private val handler = GetLinkMetricsHandler(contexts, metricsRepository)
    private val listHandler = ListWorkspaceLinkMetricsHandler(contexts, metricsRepository, properties)

    @Test
    fun `workspace metrics query uses authenticated workspace and returns zero counts`() = runBlocking {
        val link = Link(
            id = LinkId(linkId),
            ownerId = ownerId,
            domainId = DomainId.fromHost("short.example"),
            shortCode = ShortCode("AbC123"),
            destinationUrl = DestinationUrl("https://destination.example"),
            status = LinkStatus.ACTIVE,
            expiresAt = null,
            createdAt = Instant.parse("2026-10-01T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T09:00:00Z"),
            deletedAt = null,
            version = 1,
        )
        coEvery {
            metricsRepository.findWorkspaceLinksWithMetrics(ownerId, null, 21)
        } returns listOf(WorkspaceLinkClickMetrics(link, 0))

        val page = listHandler.handle(ListWorkspaceLinkMetricsQuery())

        assertEquals(0L, page.links.single().recordedRedirects)
        assertEquals("https://short.example/AbC123", page.links.single().shortUrl)
        coVerify(exactly = 1) { metricsRepository.findWorkspaceLinksWithMetrics(ownerId, null, 21) }
    }

    @Test
    fun `returns stored click count for the authenticated workspace link`() = runBlocking {
        coEvery { metricsRepository.countByLinkIdAndOwner(LinkId(linkId), ownerId) } returns 4

        assertEquals(4, handler.handle(GetLinkMetricsQuery(linkId)))
        coVerify(exactly = 1) { metricsRepository.countByLinkIdAndOwner(LinkId(linkId), ownerId) }
    }

    @Test
    fun `returns zero for an owned link without recorded clicks`() = runBlocking {
        coEvery { metricsRepository.countByLinkIdAndOwner(LinkId(linkId), ownerId) } returns 0

        assertEquals(0, handler.handle(GetLinkMetricsQuery(linkId)))
    }

    @Test
    fun `returns not found for an unknown or foreign link`() = runBlocking {
        coEvery { metricsRepository.countByLinkIdAndOwner(LinkId(linkId), ownerId) } returns null

        assertThrows(LinkNotFoundApplicationException::class.java) {
            runBlocking { handler.handle(GetLinkMetricsQuery(linkId)) }
        }
    }
}
