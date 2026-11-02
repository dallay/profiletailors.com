package com.profiletailors.smp.platformadmin.infrastructure.cli

import com.profiletailors.smp.platformadmin.application.BootstrapInvitationIssuer
import com.profiletailors.smp.platformadmin.application.BootstrapInvitationLink
import com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationCommand
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class InteractivePlatformAdminBootstrapCliTest {
    @Test
    fun `confirms normalized email and prints the one-use link once`() = runTest {
        val console = FakeBootstrapConsole(
            inputs = listOf(" Owner@Example.Test ", "owner@example.test", "REPLACE"),
        )
        var issuedCommand: IssueBootstrapInvitationCommand? = null
        val issuer = BootstrapInvitationIssuer { command ->
            issuedCommand = command
            BootstrapInvitationLink(
                email = "owner@example.test",
                link = "https://admin.example.test/bootstrap#secret-one-use-token",
                expiresAt = Instant.parse("2026-11-02T10:30:00Z"),
            )
        }

        InteractivePlatformAdminBootstrapCli(issuer, console).run()

        assertEquals(" Owner@Example.Test ", issuedCommand?.email)
        assertEquals("owner@example.test", issuedCommand?.confirmedEmail)
        assertTrue(issuedCommand?.replacementConfirmed == true)
        assertEquals(1, console.output.count { it.contains("#secret-one-use-token") })
        assertTrue(console.output.none { it.contains("password") })
    }

    @Test
    fun `end of input stops before invitation issuance`() = runTest {
        val console = FakeBootstrapConsole(inputs = listOf("owner@example.test", null))
        var issued = false
        val issuer = BootstrapInvitationIssuer {
            issued = true
            error("Issuance must not run after input ends")
        }

        runCatching { InteractivePlatformAdminBootstrapCli(issuer, console).run() }

        assertEquals(2, console.reads)
        assertTrue(!issued)
    }

    private class FakeBootstrapConsole(val inputs: List<String?>) : BootstrapConsole {
        val output = mutableListOf<String>()
        private var position = 0
        var reads = 0
            private set

        override fun readLine(prompt: String): String? {
            reads += 1
            output += prompt
            return inputs.getOrNull(position++)
        }

        override fun writeLine(value: String) {
            output += value
        }
    }
}
