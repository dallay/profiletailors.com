package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import com.profiletailors.smp.governance.domain.event.TakedownReported
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class ReportTakedownHandlerTest {

    private val repository: TakedownReportRepository = mockk()
    private val resourceContextProvider: ResourceContextProvider = mockk()
    private val principalContextProvider: com.profiletailors.common.domain.context.PrincipalContextProvider = mockk()
    private val principalIdentityPort: PrincipalIdentityPort = mockk()
    private val eventPublisher: EventPublisher<DomainEvent> = mockk()

    private val authorizationService: GovernanceAuthorizationService = mockk()
    private val governanceMutationAuditPort: GovernanceMutationAuditPort = mockk()
    private val handler = ReportTakedownHandler(
        repository = repository,
        resourceContextProvider = resourceContextProvider,
        principalContextProvider = principalContextProvider,
        principalIdentityPort = principalIdentityPort,
        authorizationService = authorizationService,
        governanceMutationAuditPort = governanceMutationAuditPort,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `creates takedown report when authorized`() = runTest {
        coEvery { authorizationService.authorizeMediaTakedown() } returns Unit
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { principalContextProvider.require() } returns
            PrincipalContext(
                principalId = "user-001",
                principalType = PrincipalType.USER,
                subject = "user-001",
            )
        coEvery { principalIdentityPort.findEmailByPrincipalId("user-001") } returns "reporter@example.com"
        coEvery { repository.findExisting("ws-001", "asset-001", "user-001") } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { governanceMutationAuditPort.recordSuccess(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val command = ReportTakedownCommand(
            assetId = "asset-001",
            reason = "Copyright infringement",
            mediaReferenceUrl = "https://example.com/original",
        )

        val result = handler.handle(command)

        result.status shouldBe TakedownReportStatus.REPORTED
        result.workspaceId shouldBe "ws-001"
        result.assetId shouldBe "asset-001"
        result.reportedById shouldBe "user-001"
        result.reason shouldBe "Copyright infringement"
        result.reporterEmail shouldBe "reporter@example.com"
        result.mediaReferenceUrl shouldBe "https://example.com/original"

        coVerify {
            governanceMutationAuditPort.recordSuccess(
                action = "MEDIA_TAKEDOWN_REPORTED",
                targetType = "takedown_report",
                targetId = any(),
                actorPrincipalId = "user-001",
                workspaceId = "ws-001",
                details = match { details ->
                    details["assetId"] == "asset-001" &&
                        details["reason"] == "Copyright infringement"
                },
            )
            eventPublisher.publish(
                match<TakedownReported> { event ->
                    event.workspaceId == "ws-001" &&
                        event.assetId == "asset-001" &&
                        event.reporterEmail == "reporter@example.com" &&
                        event.reason == "Copyright infringement"
                },
            )
        }
    }

    @Test
    fun `throws AuthorizationDeniedException when not authorized`() = runTest {
        coEvery { authorizationService.authorizeMediaTakedown() } throws AuthorizationDeniedException("Denied")

        val command = ReportTakedownCommand(
            assetId = "asset-001",
            reason = "Copyright infringement",
        )

        shouldThrow<AuthorizationDeniedException> { handler.handle(command) }
    }
}
