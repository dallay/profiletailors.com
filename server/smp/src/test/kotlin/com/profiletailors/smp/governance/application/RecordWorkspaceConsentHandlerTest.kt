package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class RecordWorkspaceConsentHandlerTest {

    private val resourceContextProvider: ResourceContextProvider = mockk()
    private val recordConsentHandler: RecordConsentHandler = mockk()
    private val authorizationService: GovernanceAuthorizationService = mockk()

    private val handler = RecordWorkspaceConsentHandler(
        resourceContextProvider = resourceContextProvider,
        recordConsentHandler = recordConsentHandler,
        authorizationService = authorizationService,
    )

    @Test
    fun `records consent when authorized`() = runTest {
        val command = RecordWorkspaceConsentCommand(
            subjectKind = SubjectKind.USER,
            subjectValue = "user-123",
            consentType = ConsentType.CONSENT,
            purpose = "marketing.emails",
            policyVersion = "2026-07-01",
            source = "settings",
            locale = "en",
        )

        coEvery { authorizationService.authorizeConsentWrite() } returns Unit
        coEvery { resourceContextProvider.require() } returns
            ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = "ws-001")
        coEvery { recordConsentHandler.handle(any()) } returns RecordConsentOutcome(
            created = true,
            record = ConsentRecord(
                id = ConsentRecordId("cs-001"),
                workspaceId = "ws-001",
                subjectReference = SubjectReference.user("user-123"),
                consentType = ConsentType.CONSENT,
                purpose = "marketing.emails",
                policyVersion = "2026-07-01",
                source = "settings",
                locale = "en",
                givenAt = Instant.parse("2026-07-20T10:00:00Z"),
                status = ConsentStatus.ACTIVE,
            ),
        )

        val result = handler.handle(command)

        result.created shouldBe true
        result.record.status shouldBe ConsentStatus.ACTIVE

        coVerify { authorizationService.authorizeConsentWrite() }
    }

    @Test
    fun `throws AuthorizationDeniedException when authorization fails`() = runTest {
        val command = RecordWorkspaceConsentCommand(
            subjectKind = SubjectKind.USER,
            subjectValue = "user-123",
            consentType = ConsentType.CONSENT,
            purpose = "marketing.emails",
            policyVersion = "2026-07-01",
            source = "settings",
            locale = "en",
        )

        coEvery { authorizationService.authorizeConsentWrite() } throws AuthorizationDeniedException("Denied")

        shouldThrow<AuthorizationDeniedException> { handler.handle(command) }
    }
}
