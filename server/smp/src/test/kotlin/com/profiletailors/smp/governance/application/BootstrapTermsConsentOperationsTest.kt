package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BootstrapTermsConsentOperationsTest {
    private val consentRepository = mockk<ConsentRepository>()

    @Test
    fun `records bootstrap terms using governance platform scope and supplied occurrence time`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val occurredAt = Instant.parse("2026-11-02T10:00:00Z")
        val record = slot<ConsentRecord>()
        coEvery { consentRepository.recordActiveReturning(capture(record)) } answers { true to firstArg() }

        RecordBootstrapTermsConsentHandler(consentRepository).handle(
            RecordBootstrapTermsConsentCommand(principalId, "terms-v1.0.0", occurredAt),
        )

        assertEquals(ConsentScope.Platform, record.captured.scope)
        assertEquals(SubjectReference.user(principalId.toString()), record.captured.subjectReference)
        assertEquals(ConsentType.CONTRACT_ACCEPTANCE, record.captured.consentType)
        assertEquals("terms.acceptance", record.captured.purpose)
        assertEquals("terms-v1.0.0", record.captured.policyVersion)
        assertEquals("bootstrap", record.captured.source)
        assertEquals("en", record.captured.locale)
        assertEquals(occurredAt, record.captured.givenAt)
    }

    @Test
    fun `checks current bootstrap terms in governance platform scope`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery {
            consentRepository.existsActive(
                ConsentScope.Platform,
                SubjectReference.user(principalId.toString()),
                "terms.acceptance",
                "terms-v1.0.0",
            )
        } returns true

        val result = CheckBootstrapTermsConsentHandler(consentRepository).handle(
            CheckBootstrapTermsConsentQuery(principalId, "terms-v1.0.0"),
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            consentRepository.existsActive(
                ConsentScope.Platform,
                SubjectReference.user(principalId.toString()),
                "terms.acceptance",
                "terms-v1.0.0",
            )
        }
    }
}
