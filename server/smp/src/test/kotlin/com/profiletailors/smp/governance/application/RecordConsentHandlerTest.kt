package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal class RecordConsentHandlerTest {

    private val repository: ConsentRepository = mockk()
    private val workspaceScope = ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val waitlistScope = ConsentScope.Waitlist(workspaceScope.id)
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
            scope = workspaceScope,
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.findActive(workspaceScope, command.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns null
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        val result = handler.handle(command)

        result.created shouldBe true
        result.record.id.value shouldBe java.util.UUID.fromString(result.record.id.value).toString()
        result.record.status shouldBe ConsentStatus.ACTIVE
        result.record.consentType shouldBe ConsentType.CONSENT
        result.record.purpose shouldBe "waitlist.early_access"
        result.record.policyVersion shouldBe "2026-07-01"
        result.record.givenAt shouldBe fixedClock.instant()

        val saved = slot<ConsentRecord>()
        coVerify { repository.recordActiveReturning(capture(saved)) }
        result.record shouldBe saved.captured
    }

    @Test
    fun `idempotent submission returns existing record without saving a duplicate`() = runTest {
        val existing = ConsentRecord(
            id = ConsentRecordId("cs-existing"),
            scope = workspaceScope,
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
            givenAt = Instant.parse("2026-07-15T10:00:00Z"),
        )
        val command = RecordConsentCommand(
            scope = workspaceScope,
            subjectReference = existing.subjectReference,
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.findActive(workspaceScope, existing.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns existing

        val result = handler.handle(command)

        result.created shouldBe false
        result.record shouldBe existing
        coVerify(exactly = 0) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `different policy version is not a duplicate and is recorded`() = runTest {
        val command = RecordConsentCommand(
            scope = workspaceScope,
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-08-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        coEvery {
            repository.findActive(workspaceScope, command.subjectReference, "waitlist.early_access", "2026-08-01")
        } returns null
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        val result = handler.handle(command)

        result.record.policyVersion shouldBe "2026-08-01"
        coVerify(exactly = 1) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `different subject is not a duplicate and is recorded`() = runTest {
        val command = RecordConsentCommand(
            scope = workspaceScope,
            subjectReference = SubjectReference.user("user-002"),
            consentType = ConsentType.CONSENT,
            purpose = "waitlist.early_access",
            policyVersion = "2026-07-01",
            source = "settings_update",
            locale = "es-ES",
        )

        coEvery {
            repository.findActive(workspaceScope, command.subjectReference, "waitlist.early_access", "2026-07-01")
        } returns null
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        handler.handle(command)
        coVerify(exactly = 1) { repository.recordActiveReturning(any()) }
    }

    @Test
    fun `idempotency lookup preserves waitlist scope from the command`() = runTest {
        val command = RecordConsentCommand(
            scope = waitlistScope,
            subjectReference = SubjectReference.workspace("ws-isolated"),
            consentType = ConsentType.CONTRACT_ACCEPTANCE,
            purpose = "terms.v1",
            policyVersion = "2026-07-01",
            source = "registration",
            locale = "es-ES",
        )

        coEvery {
            repository.findActive(waitlistScope, command.subjectReference, "terms.v1", "2026-07-01")
        } returns null
        coEvery { repository.recordActiveReturning(any()) } answers { true to firstArg() }

        handler.handle(command)

        coVerify {
            repository.findActive(waitlistScope, command.subjectReference, "terms.v1", "2026-07-01")
        }
        coVerify {
            repository.recordActiveReturning(match { it.scope == waitlistScope })
        }
    }

    @Test
    fun `workspace scope rejects blank identifiers at construction`() {
        shouldThrow<IllegalArgumentException> { ConsentScope.Workspace(UUID.fromString("")) }
    }

    @Test
    fun `handler rejects blank purpose`() = runTest {
        val command = RecordConsentCommand(
            scope = workspaceScope,
            subjectReference = SubjectReference.workspace("ws-001"),
            consentType = ConsentType.CONSENT,
            purpose = "",
            policyVersion = "2026-07-01",
            source = "waitlist_join",
            locale = "es-ES",
        )

        val error = shouldThrow<IllegalArgumentException> {
            handler.handle(command)
        }
        error.message shouldBe "purpose must not be blank"
    }
}
