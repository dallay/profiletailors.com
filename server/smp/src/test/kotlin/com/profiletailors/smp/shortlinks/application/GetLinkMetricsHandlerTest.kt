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
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
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

    private fun testLink(id: String, createdAt: Instant) = Link(
        id = LinkId(UUID.fromString(id)),
        ownerId = ownerId,
        domainId = DomainId.fromHost("short.example"),
        shortCode = ShortCode("AbC123"),
        destinationUrl = DestinationUrl("https://destination.example"),
        status = LinkStatus.ACTIVE,
        expiresAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1,
    )

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
    fun `rejects a cursor with an invalid base64 encoding`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { listHandler.handle(ListWorkspaceLinkMetricsQuery(cursor = "%%%")) }
        }

        assertEquals("Invalid cursor", exception.message)
    }

    @Test
    fun `rejects cursor timestamps outside supported database range`() {
        val outOfRangeCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteBuffer.allocate(28)
                .putLong(Instant.parse("+10000-01-01T00:00:00Z").epochSecond)
                .putInt(0)
                .putLong(linkId.mostSignificantBits)
                .putLong(linkId.leastSignificantBits)
                .array(),
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { listHandler.handle(ListWorkspaceLinkMetricsQuery(cursor = outOfRangeCursor)) }
        }

        assertEquals("Invalid cursor", exception.message)
    }

    @Test
    fun `workspace metrics encode and decode cursor using the last item in the page`() = runBlocking {
        val links = listOf(
            testLink("0199b1ca-0000-7000-8000-000000000003", Instant.parse("2026-10-03T00:00:00Z")),
            testLink("0199b1ca-0000-7000-8000-000000000004", Instant.parse("2026-10-02T00:00:00Z")),
        )
        coEvery { metricsRepository.findWorkspaceLinksWithMetrics(ownerId, null, 2) } returns
            links.map { WorkspaceLinkClickMetrics(it, 0) }

        val firstPage = listHandler.handle(ListWorkspaceLinkMetricsQuery(limit = 1))
        val cursor = requireNotNull(firstPage.nextCursor)
        val decoded = Base64.getUrlDecoder().decode(cursor)
        val buffer = ByteBuffer.wrap(decoded)
        val createdAt = Instant.ofEpochSecond(buffer.long, buffer.int.toLong())
        val id = UUID(buffer.long, buffer.long)
        coEvery {
            metricsRepository.findWorkspaceLinksWithMetrics(ownerId, any(), 2)
        } returns listOf(WorkspaceLinkClickMetrics(links[1], 0))

        val secondPage = listHandler.handle(ListWorkspaceLinkMetricsQuery(limit = 1, cursor = cursor))

        assertEquals(links.first().createdAt, createdAt)
        assertEquals(links.first().id.value, id)
        assertEquals(links[1].id.value, secondPage.links.single().id)
        coVerify {
            metricsRepository.findWorkspaceLinksWithMetrics(
                ownerId,
                match { it?.createdAt == links.first().createdAt && it.id == links.first().id },
                2,
            )
        }
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
