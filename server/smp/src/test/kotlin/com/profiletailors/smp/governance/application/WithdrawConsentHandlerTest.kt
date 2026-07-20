package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class WithdrawConsentHandlerTest {

    private val repository: ConsentRepository = mockk()
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-08-01T10:00:00Z"),
        ZoneId.of("UTC"),
    )

    private val handler = WithdrawConsentHandler(
        repository = repository,
        clock = fixedClock,
    )

    @Test
    fun `withdraws active consent without destroying historical identity`() = runTest {
        val existing = activeConsent()
        val command = WithdrawConsentCommand(
            workspaceId = "ws-001",
            subjectReference = existing.subjectReference,
            purpose = existing.purpose,
            policyVersion = existing.policyVersion,
            reason = "user_request",
        )

        coEvery {
            repository.withdrawActiveReturning(
                workspaceId = "ws-001",
                subjectReference = existing.subjectReference,
                purpose = existing.purpose,
                policyVersion = existing.policyVersion,
                withdrawnAt = fixedClock.instant(),
                reason = "user_request",
            )
        } returns existing.withdraw(fixedClock.instant(), "user_request")

        val result = handler.handle(command)

        result.status shouldBe ConsentStatus.WITHDRAWN
        result.withdrawnAt shouldBe fixedClock.instant()
        result.withdrawalReason shouldBe "user_request"
        result.id shouldBe existing.id
        result.subjectReference shouldBe existing.subjectReference
        result.purpose shouldBe existing.purpose
        result.givenAt shouldBe existing.givenAt

        coVerify {
            repository.withdrawActiveReturning(
                workspaceId = "ws-001",
                subjectReference = existing.subjectReference,
                purpose = existing.purpose,
                policyVersion = existing.policyVersion,
                withdrawnAt = fixedClock.instant(),
                reason = "user_request",
            )
        }
    }

    @Test
    fun `withdrawal lookup is scoped by workspace`() = runTest {
        val existing = activeConsent(workspaceId = "ws-isolated")
        val command = WithdrawConsentCommand(
            workspaceId = "ws-isolated",
            subjectReference = existing.subjectReference,
            purpose = existing.purpose,
            policyVersion = existing.policyVersion,
        )

        coEvery {
            repository.withdrawActiveReturning(
                "ws-isolated",
                existing.subjectReference,
                existing.purpose,
                existing.policyVersion,
                fixedClock.instant(),
                null,
            )
        } returns existing.withdraw(fixedClock.instant())

        handler.handle(command)

        coVerify {
            repository.withdrawActiveReturning(
                "ws-isolated",
                existing.subjectReference,
                existing.purpose,
                existing.policyVersion,
                fixedClock.instant(),
                null,
            )
        }
    }

    @Test
    fun `throws when no active consent exists`() = runTest {
        val subject = SubjectReference.workspace("ws-001")
        val command = WithdrawConsentCommand(
            workspaceId = "ws-001",
            subjectReference = subject,
            purpose = "marketing_emails",
            policyVersion = "2026-07-01",
        )

        coEvery {
            repository.withdrawActiveReturning(
                "ws-001",
                subject,
                "marketing_emails",
                "2026-07-01",
                fixedClock.instant(),
                null,
            )
        } returns null

        shouldThrow<ConsentRecordNotFoundException> {
            handler.handle(command)
        }

        coVerify(exactly = 1) {
            repository.withdrawActiveReturning(
                "ws-001",
                subject,
                "marketing_emails",
                "2026-07-01",
                fixedClock.instant(),
                null,
            )
        }
    }

    @Test
    fun `rejects blank workspaceId`() = runTest {
        val command = WithdrawConsentCommand(
            workspaceId = "",
            subjectReference = SubjectReference.workspace("ws-001"),
            purpose = "marketing_emails",
            policyVersion = "2026-07-01",
        )

        val error = shouldThrow<IllegalArgumentException> { handler.handle(command) }
        error.message shouldBe "workspaceId must not be blank"
    }

    @Test
    fun `rejects blank purpose`() = runTest {
        val command = WithdrawConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            purpose = "",
            policyVersion = "2026-07-01",
        )

        val error = shouldThrow<IllegalArgumentException> { handler.handle(command) }
        error.message shouldBe "purpose must not be blank"
    }

    private fun activeConsent(workspaceId: String = "ws-001"): ConsentRecord = ConsentRecord(
        id = ConsentRecordId("cs-001"),
        workspaceId = workspaceId,
        subjectReference = SubjectReference.workspace(workspaceId),
        consentType = ConsentType.CONSENT,
        purpose = "marketing_emails",
        policyVersion = "2026-07-01",
        source = "settings_update",
        locale = "es-ES",
        givenAt = Instant.parse("2026-07-17T10:00:00Z"),
    )
}
