package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

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
            repository.findActive("ws-001", existing.subjectReference, existing.purpose, existing.policyVersion)
        } returns existing
        coEvery { repository.save(any()) } answers { firstArg() }

        val result = handler.handle(command)

        assertEquals(ConsentStatus.WITHDRAWN, result.status)
        assertEquals(fixedClock.instant(), result.withdrawnAt)
        assertEquals("user_request", result.withdrawalReason)
        assertEquals(existing.id, result.id)
        assertEquals(existing.subjectReference, result.subjectReference)
        assertEquals(existing.purpose, result.purpose)
        assertEquals(existing.givenAt, result.givenAt)

        val saved = slot<ConsentRecord>()
        coVerify { repository.save(capture(saved)) }
        assertEquals(result, saved.captured)
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
            repository.findActive("ws-isolated", existing.subjectReference, existing.purpose, existing.policyVersion)
        } returns existing
        coEvery { repository.save(any()) } answers { firstArg() }

        handler.handle(command)

        coVerify {
            repository.findActive("ws-isolated", existing.subjectReference, existing.purpose, existing.policyVersion)
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

        coEvery { repository.findActive("ws-001", subject, "marketing_emails", "2026-07-01") } returns null

        assertThrows<ConsentRecordNotFoundException> {
            handler.handle(command)
        }

        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `rejects blank workspaceId`() = runTest {
        val command = WithdrawConsentCommand(
            workspaceId = "",
            subjectReference = SubjectReference.workspace("ws-001"),
            purpose = "marketing_emails",
            policyVersion = "2026-07-01",
        )

        val error = assertThrows<IllegalArgumentException> { handler.handle(command) }
        assertEquals("workspaceId must not be blank", error.message)
    }

    @Test
    fun `rejects blank purpose`() = runTest {
        val command = WithdrawConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            purpose = "",
            policyVersion = "2026-07-01",
        )

        val error = assertThrows<IllegalArgumentException> { handler.handle(command) }
        assertEquals("purpose must not be blank", error.message)
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
