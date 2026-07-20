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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

internal class RecordConsentHandlerTest {

    private val repository: ConsentRepository = mockk()
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-07-17T12:00:00Z"),
        ZoneId.of("UTC"),
    )

    private val handler = RecordConsentHandler(
        repository = repository,
        clock = fixedClock,
    )

    @Test
    fun `records a new consent record when no active duplicate exists`() = runTest {
        val command = RecordConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.existsActive("ws-001", command.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns false
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        val result = handler.handle(command)

        assertEquals(true, result.created)
        assertEquals(ConsentStatus.ACTIVE, result.record.status)
        assertEquals(ConsentType.CONSENT, result.record.consentType)
        assertEquals("waitlist.early_access", result.record.purpose)
        assertEquals("2026-07-01", result.record.policyVersion)
        assertEquals(fixedClock.instant(), result.record.givenAt)

        val saved = slot<ConsentRecord>()
        coVerify { repository.recordActiveReturning(capture(saved)) }
        assertEquals(result.record, saved.captured)
    }

    @Test
    fun `idempotent submission returns existing record without saving a duplicate`() = runTest {
        val existing = ConsentRecord(
            id = ConsentRecordId("cs-existing"),
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = Instant.parse("2026-07-15T10:00:00Z"),
        )
        val command = RecordConsentCommand(
            workspaceId = "ws-001",
            subjectReference = existing.subjectReference,
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.existsActive("ws-001", existing.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns true
        coEvery {
            repository.findActive("ws-001", existing.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns existing

        val result = handler.handle(command)

        assertEquals(false, result.created)
        assertEquals(existing, result.record)
        coVerify(exactly = 0) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `different policy version is not a duplicate and is recorded`() = runTest {
        val command = RecordConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-08-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.existsActive("ws-001", command.subjectReference, "waitlist.early_access", "2026-08-01")
        } returns false
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        val result = handler.handle(command)

        assertEquals("2026-08-01", result.record.policyVersion)
        coVerify(exactly = 1) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `different subject is not a duplicate and is recorded`() = runTest {
        val command = RecordConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.user("user-002"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "settings_update",
            locale = "es-ES",
        )

        coEvery {
            repository.existsActive("ws-001", command.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns false
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        handler.handle(command)
        coVerify(exactly = 1) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `idempotency lookup uses workspaceId from the command for cross-workspace safety`() = runTest {
        val command = RecordConsentCommand(
            workspaceId = "ws-isolated",
            subjectReference = SubjectReference.workspace("ws-isolated"),
            consentType = ConsentType.CONTRACT_ACCEPTANCE,
            purpose = "terms.v1",
            policyVersion = "2026-07-01",
            source = "registration",
            locale = "es-ES",
        )

        coEvery {
            repository.existsActive("ws-isolated", command.subjectReference, "terms.v1", "2026-07-01")
        } returns false
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        handler.handle(command)

        coVerify {
            repository.existsActive("ws-isolated", command.subjectReference, "terms.v1", "2026-07-01")
        }
        coVerify {
            repository.recordActiveReturning(match { it.workspaceId == "ws-isolated" })
        }
    }

    @Test
    fun `handler rejects blank workspaceId`() {
        val command = RecordConsentCommand(
            workspaceId = "",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        runTest {
            try {
                handler.handle(command)
                error("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                assertEquals("workspaceId must not be blank", expected.message)
            }
        }
    }

    @Test
    fun `handler rejects blank purpose`() {
        val command = RecordConsentCommand(
            workspaceId = "ws-001",
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        runTest {
            try {
                handler.handle(command)
                error("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                assertEquals("purpose must not be blank", expected.message)
            }
        }
    }
}
