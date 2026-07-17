package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceControlRepository
import com.profiletailors.smp.governance.domain.ComplianceControlStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RegisterComplianceControlHandlerTest {

    private val repository: ComplianceControlRepository = mockk()
    private val handler = RegisterComplianceControlHandler(repository)

    @Test
    fun `creates control with custom id and saves`() = runTest {
        val command = RegisterComplianceControlCommand(
            id = ComplianceControlId("ctrl-custom-001"),
            controlKey = "PRIVACY.DATA_RETENTION",
            name = "Data retention",
            description = "Ensure data is retained per policy",
            owner = "security-team",
            category = "PRIVACY",
            status = ComplianceControlStatus.ACTIVE,
        )

        coEvery { repository.save(any()) } answers { firstArg() }

        handler.handle(command)

        coVerify(exactly = 1) { repository.save(match { it.id == command.id }) }
    }

    @Test
    fun `generates id when command id is null`() = runTest {
        val command = RegisterComplianceControlCommand(
            id = null,
            controlKey = "ACCESS.MFA_ENABLED",
            name = "MFA required",
            owner = "security-team",
            category = "ACCESS",
        )

        val savedSlot = slot<ComplianceControl>()
        coEvery { repository.save(capture(savedSlot)) } answers { firstArg() }

        handler.handle(command)

        coVerify(exactly = 1) { repository.save(any()) }
        val saved = savedSlot.captured
        assertTrue(saved.id.value.startsWith("ctrl-"))
        assertEquals(command.controlKey, saved.controlKey)
        assertEquals(command.name, saved.name)
        assertEquals(command.owner, saved.owner)
        assertEquals(command.category, saved.category)
        assertEquals(command.status, saved.status)
    }
}
