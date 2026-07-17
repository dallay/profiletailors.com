package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RegisterComplianceEvidenceHandlerTest {

    private val repository: ComplianceEvidenceRepository = mockk()
    private val handler = RegisterComplianceEvidenceHandler(repository)

    @Test
    fun `creates evidence with generated id and saves`() = runTest {
        val command = RegisterComplianceEvidenceCommand(
            evidenceType = "screenshot",
            title = "Login page GDPR consent",
            description = "Screenshot showing the consent checkbox",
            referenceUrl = "https://example.com/login",
            immutableReference = "sha256:abc123",
            checksum = "def456",
            metadataJson = """{"source":"manual"}""",
            submittedBy = "auditor@example.com",
        )

        val savedSlot = slot<ComplianceEvidence>()
        coEvery { repository.save(capture(savedSlot)) } answers { firstArg() }

        handler.handle(command)

        coVerify(exactly = 1) { repository.save(any()) }
        val saved = savedSlot.captured
        assertTrue(saved.id.value.startsWith("ev-"))
        assertEquals(command.evidenceType, saved.evidenceType)
        assertEquals(command.title, saved.title)
        assertEquals(command.description, saved.description)
        assertEquals(command.referenceUrl, saved.referenceUrl)
        assertEquals(command.immutableReference, saved.immutableReference)
        assertEquals(command.checksum, saved.checksum)
        assertEquals(command.metadataJson, saved.metadataJson)
        assertEquals(command.submittedBy, saved.submittedBy)
    }
}
