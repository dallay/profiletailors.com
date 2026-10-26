package com.profiletailors.smp.leadcapture.infrastructure.governance

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistConsentRecordRequest
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.smp.governance.application.RecordConsentCommand
import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GovernanceWaitlistConsentRecorderTest {

    private val handler: RecordConsentHandler = mockk(relaxed = true)
    private val recorder = GovernanceWaitlistConsentRecorder(handler)

    @Test
    fun `records early access consent with anonymous hashed subject`() = runTest {
        coEvery { handler.handle(any()) } returns mockk(relaxed = true)

        recorder.record(
            WaitlistConsentRecordRequest(
                waitlistId = WaitlistId("ea2c112c-d7f8-5e73-a4a8-ee4c372ed124"),
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                entryId = WaitlistEntryId("entry-001"),
                normalizedEmail = NormalizedEmail.from(EmailAddress("USER@example.com")),
                consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-07-17"),
                locale = CaptureLocale("es"),
                source = CaptureSource("marketing-homepage"),
            ),
        )

        coVerify(exactly = 1) {
            handler.handle(
                match<RecordConsentCommand> {
                    it.scope == ConsentScope.Waitlist(UUID.fromString("ea2c112c-d7f8-5e73-a4a8-ee4c372ed124")) &&
                        it.subjectReference.kind == SubjectKind.ANONYMOUS &&
                        it.subjectReference.value.length == 64 &&
                        it.consentType == ConsentType.CONSENT &&
                        it.purpose == "waitlist.early_access" &&
                        it.policyVersion == "2026-07-17" &&
                        it.source == "marketing-homepage" &&
                        it.locale == "es"
                },
            )
        }
    }

    @Test
    fun `records marketing consent only when explicitly selected`() = runTest {
        coEvery { handler.handle(any()) } returns mockk(relaxed = true)

        recorder.record(
            WaitlistConsentRecordRequest(
                waitlistId = WaitlistId("ea2c112c-d7f8-5e73-a4a8-ee4c372ed124"),
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                entryId = WaitlistEntryId("entry-001"),
                normalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
                consent = WaitlistConsent(earlyAccess = true, marketing = true, version = "2026-07-17"),
                locale = null,
                source = CaptureSource("marketing-homepage"),
            ),
        )

        coVerify(exactly = 1) {
            handler.handle(match<RecordConsentCommand> { it.purpose == "waitlist.early_access" })
        }
        coVerify(exactly = 1) {
            handler.handle(match<RecordConsentCommand> { it.purpose == "marketing.emails" })
        }
    }

    @Test
    fun `uses default locale when waitlist request has no locale`() = runTest {
        val captured = mutableListOf<RecordConsentCommand>()
        coEvery { handler.handle(capture(captured)) } returns mockk(relaxed = true)

        recorder.record(
            WaitlistConsentRecordRequest(
                waitlistId = WaitlistId("ea2c112c-d7f8-5e73-a4a8-ee4c372ed124"),
                waitlistKey = WaitlistKey("profile-tailors-launch"),
                entryId = WaitlistEntryId("entry-001"),
                normalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
                consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-07-17"),
                locale = null,
                source = CaptureSource("marketing-homepage"),
            ),
        )

        assertEquals("en", captured.single().locale)
    }

    @Test
    fun `hashes normalized email deterministically without exposing raw email`() {
        val first = GovernanceWaitlistConsentRecorder.anonymousSubjectHash(
            NormalizedEmail.from(EmailAddress("USER@example.com")),
        )
        val second = GovernanceWaitlistConsentRecorder.anonymousSubjectHash(
            NormalizedEmail.from(EmailAddress("user@example.com")),
        )

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue("user@example.com" !in first)
    }

    @Test
    fun `list identities rather than semantic keys determine consent scope`() = runTest {
        val captured = mutableListOf<RecordConsentCommand>()
        coEvery { handler.handle(capture(captured)) } returns mockk(relaxed = true)
        val firstId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val first = WaitlistConsentRecordRequest(
            waitlistId = WaitlistId(firstId.toString()),
            waitlistKey = WaitlistKey("first-list"),
            entryId = WaitlistEntryId(UUID.randomUUID().toString()),
            normalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
            consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "2026-10-26"),
            locale = CaptureLocale("en"),
            source = CaptureSource("public-waitlist"),
        )

        recorder.record(first)
        recorder.record(
            first.copy(waitlistId = WaitlistId(secondId.toString()), waitlistKey = WaitlistKey("second-list")),
        )

        assertEquals(listOf(ConsentScope.Waitlist(firstId), ConsentScope.Waitlist(secondId)), captured.map { it.scope })
        assertEquals(captured[0].subjectReference, captured[1].subjectReference)
    }
}
