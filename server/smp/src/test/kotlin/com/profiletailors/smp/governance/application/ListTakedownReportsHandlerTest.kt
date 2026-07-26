package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ListTakedownReportsHandlerTest {

    private val repository: TakedownReportRepository = mockk()
    private val resourceContextProvider: ResourceContextProvider = mockk()
    private val authorizationService: GovernanceAuthorizationService = mockk()
    private val handler = ListTakedownReportsHandler(
        repository = repository,
        resourceContextProvider = resourceContextProvider,
        authorizationService = authorizationService,
    )

    @Test
    fun `lists reports when authorized`() = runTest {
        val reports = listOf(
            TakedownReport(
                reportId = "report-001",
                workspaceId = "ws-001",
                assetId = "asset-001",
                reportedById = "user-001",
                reason = "Copyright",
                status = TakedownReportStatus.REPORTED,
                reporterEmail = "reporter@example.com",
                createdAt = Instant.parse("2026-07-21T09:00:00Z"),
                updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
            ),
        )

        coEvery { authorizationService.authorizeMediaRead() } returns Unit
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { repository.findByWorkspace("ws-001", null) } returns flowOf(reports[0])

        val result = handler.handle(ListTakedownReportsQuery()).toList()

        result.size shouldBe 1
        result[0].reportId shouldBe "report-001"
    }

    @Test
    fun `filters by status`() = runTest {
        coEvery { authorizationService.authorizeMediaRead() } returns Unit
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { repository.findByWorkspace("ws-001", TakedownReportStatus.REPORTED) } returns flowOf()

        val result = handler.handle(ListTakedownReportsQuery(TakedownReportStatus.REPORTED)).toList()

        result.size shouldBe 0
    }

    @Test
    fun `throws AuthorizationDeniedException when not authorized`() = runTest {
        coEvery { authorizationService.authorizeMediaRead() } throws AuthorizationDeniedException("Denied")

        shouldThrow<AuthorizationDeniedException> {
            handler.handle(ListTakedownReportsQuery())
        }
    }
}
