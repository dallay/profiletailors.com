package com.profiletailors.smp.shortlinks.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.shortlinks.application.CreateLinkCommand
import com.profiletailors.smp.shortlinks.application.DeleteLinkCommand
import com.profiletailors.smp.shortlinks.application.DisableLinkCommand
import com.profiletailors.smp.shortlinks.application.EnableLinkCommand
import com.profiletailors.smp.shortlinks.application.GetLinkMetricsQuery
import com.profiletailors.smp.shortlinks.application.GetLinkQuery
import com.profiletailors.smp.shortlinks.application.LinkResult
import com.profiletailors.smp.shortlinks.application.ListWorkspaceLinkMetricsQuery
import com.profiletailors.smp.shortlinks.application.UpdateLinkCommand
import com.profiletailors.smp.shortlinks.application.WorkspaceLinkMetricResult
import com.profiletailors.smp.shortlinks.application.WorkspaceLinkMetricsPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class LinkManagementControllerTest {
    private val mediator = mockk<Mediator>()
    private val controller = LinkManagementController(mediator)
    private val linkId = UUID.fromString("0199b1ca-0000-7000-8000-000000000001")
    private val result = LinkResult(
        id = linkId,
        shortCode = "AbC123",
        shortUrl = "https://short.example/AbC123",
        destinationUrl = "https://example.com/path",
        status = "ACTIVE",
        createdAt = Instant.parse("2026-10-01T09:00:00Z"),
        expiresAt = null,
        version = 1,
    )

    private fun LinkResult.toWorkspaceMetric(recordedRedirects: Long) = WorkspaceLinkMetricResult(
        id, shortCode, shortUrl, destinationUrl, status, createdAt, expiresAt, version, recordedRedirects,
    )

    @Test
    fun `create delegates with idempotency key`() = runTest {
        coEvery { mediator.send(any<CreateLinkCommand>()) } returns result

        val actual = controller.createLink(
            CreateLinkRequest("https://example.com/path", "AbC123", null),
            "key-1",
        )

        assertEquals(result, actual)
        coVerify(exactly = 1) {
            mediator.send(
                match<CreateLinkCommand> {
                    it.destinationUrl == "https://example.com/path" &&
                        it.customAlias == "AbC123" &&
                        it.idempotencyKey == "key-1"
                },
            )
        }
    }

    @Test
    fun `get delegates with link id`() = runTest {
        coEvery { mediator.send(any<GetLinkQuery>()) } returns result

        assertEquals(result, controller.getLink(linkId))
        coVerify(exactly = 1) { mediator.send(GetLinkQuery(linkId)) }
    }

    @Test
    fun `metrics requires the versioned response media type`() {
        val mapping = LinkManagementController::class.java.declaredMethods
            .single { it.name == "getLinkMetrics" }
            .getAnnotation(org.springframework.web.bind.annotation.GetMapping::class.java)

        assertEquals(listOf("application/vnd.api.v1+json"), mapping.produces.toList())
    }

    @Test
    fun `metrics delegates and returns recorded redirects`() = runTest {
        coEvery { mediator.send(any<GetLinkMetricsQuery>()) } returns 3L

        assertEquals(LinkMetricsResult(3L), controller.getLinkMetrics(linkId))
        coVerify(exactly = 1) { mediator.send(any<GetLinkMetricsQuery>()) }
    }

    @Test
    fun `workspace metrics collection forwards opaque cursor and limit`() = runTest {
        val page = WorkspaceLinkMetricsPage(
            links = listOf(
                result.toWorkspaceMetric(0),
                result.copy(id = UUID.randomUUID()).toWorkspaceMetric(4),
            ),
            nextCursor = "opaque-cursor",
        )
        coEvery { mediator.send(any<ListWorkspaceLinkMetricsQuery>()) } returns page

        assertEquals(page, controller.listWorkspaceLinkMetrics(limit = 2, cursor = "opaque-cursor"))
        coVerify(exactly = 1) {
            mediator.send(
                match<ListWorkspaceLinkMetricsQuery> {
                    it.limit == 2 && it.cursor == "opaque-cursor"
                },
            )
        }
    }

    @Test
    fun `update maps if-match to expected version`() = runTest {
        coEvery { mediator.send(any<UpdateLinkCommand>()) } returns result

        assertEquals(result, controller.updateLink(linkId, 3, UpdateLinkRequest("https://example.com/n", null)))
        coVerify(exactly = 1) {
            mediator.send(
                match<UpdateLinkCommand> {
                    it.linkId == linkId &&
                        it.expectedVersion == 3L &&
                        it.destinationUrl == "https://example.com/n"
                },
            )
        }
    }

    @Test
    fun `disable delegates`() = runTest {
        coEvery { mediator.send(any<DisableLinkCommand>()) } returns result

        assertEquals(result, controller.disableLink(linkId))
        coVerify(exactly = 1) { mediator.send(DisableLinkCommand(linkId)) }
    }

    @Test
    fun `enable delegates`() = runTest {
        coEvery { mediator.send(any<EnableLinkCommand>()) } returns result

        assertEquals(result, controller.enableLink(linkId))
        coVerify(exactly = 1) { mediator.send(EnableLinkCommand(linkId)) }
    }

    @Test
    fun `delete delegates`() = runTest {
        coEvery { mediator.send(any<DeleteLinkCommand>()) } returns result

        assertEquals(result, controller.deleteLink(linkId))
        coVerify(exactly = 1) { mediator.send(DeleteLinkCommand(linkId)) }
    }
}
