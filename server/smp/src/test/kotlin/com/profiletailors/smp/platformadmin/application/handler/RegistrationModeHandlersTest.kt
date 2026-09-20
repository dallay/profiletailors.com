package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.RegistrationModeChange
import com.profiletailors.smp.identity.application.RegistrationModeGateway
import com.profiletailors.smp.identity.domain.RegistrationMode
import com.profiletailors.smp.platformadmin.application.command.ChangeRegistrationModeCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.InvalidRegistrationModeException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RegistrationModeHandlersTest {
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `read returns current mode for any caller holding configuration read`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.OPEN)
        val handlers = handlers(gateway, RecordingAuditPublisher())

        val mode = handlers.currentMode(setOf(PlatformRole.PLATFORM_OPERATOR))

        assertEquals(RegistrationMode.OPEN, mode)
    }

    @Test
    fun `read is denied without configuration read permission`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.OPEN)
        val handlers = handlers(gateway, RecordingAuditPublisher())

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.currentMode(setOf(PlatformRole.SUPPORT_AGENT))
            }
        }
    }

    @Test
    fun `write is denied for callers without configuration manage and publishes rejected`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.OPEN)
        val audit = RecordingAuditPublisher()
        val handlers = handlers(gateway, audit)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.changeMode(
                    ChangeRegistrationModeCommand(operatorId, setOf(PlatformRole.PLATFORM_OPERATOR), "CLOSED"),
                )
            }
        }

        assertEquals(0, gateway.changeCalls)
        assertEquals(1, audit.events.size)
        val event = audit.events.single()
        assertEquals("CONFIGURATION_CHANGED", event.action.name)
        assertEquals("REJECTED", event.result.name)
        assertEquals("CONFIGURATION", event.targetType)
        assertEquals("registration.mode", event.targetId)
    }

    @Test
    fun `write with an invalid mode value publishes failed and changes no state`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.OPEN)
        val audit = RecordingAuditPublisher()
        val handlers = handlers(gateway, audit)

        assertThrows(InvalidRegistrationModeException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.changeMode(
                    ChangeRegistrationModeCommand(operatorId, setOf(PlatformRole.PLATFORM_OWNER), "BOGUS"),
                )
            }
        }

        assertEquals(0, gateway.changeCalls)
        assertEquals(RegistrationMode.OPEN, gateway.currentMode())
        val event = audit.events.single()
        assertEquals("CONFIGURATION_CHANGED", event.action.name)
        assertEquals("FAILED", event.result.name)
        assertEquals("CONFIGURATION", event.targetType)
        assertEquals("registration.mode", event.targetId)
    }

    @Test
    fun `successful write runs atomically and publishes succeeded with the previous and new mode`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.OPEN)
        val audit = RecordingAuditPublisher()
        val runner = RecordingTransactionRunner()
        val handlers = handlers(gateway, audit, runner)

        val change = handlers.changeMode(
            ChangeRegistrationModeCommand(operatorId, setOf(PlatformRole.PLATFORM_OWNER), "CLOSED"),
        )

        assertEquals(RegistrationMode.OPEN, change.previousMode)
        assertEquals(RegistrationMode.CLOSED, change.newMode)
        assertEquals(1, gateway.changeCalls)
        assertEquals(1, runner.invocations)
        val event = audit.events.single()
        assertEquals("CONFIGURATION_CHANGED", event.action.name)
        assertEquals("SUCCEEDED", event.result.name)
        assertEquals("CONFIGURATION", event.targetType)
        assertEquals("registration.mode", event.targetId)
        assertEquals(mapOf("previousMode" to "OPEN", "newMode" to "CLOSED"), event.metadata)
    }

    private fun handlers(
        gateway: RegistrationModeGateway,
        audit: RecordingAuditPublisher,
        runner: AtomicTransactionRunner = RecordingTransactionRunner(),
    ) = RegistrationModeHandlers(
        registrationModeGateway = gateway,
        auditPublisher = audit,
        transactionRunner = runner,
        clock = clock,
    )

    private class FakeRegistrationModeGateway(private var current: RegistrationMode) : RegistrationModeGateway {
        var changeCalls = 0
            private set

        override suspend fun currentMode(): RegistrationMode = current

        override suspend fun changeMode(newMode: RegistrationMode): RegistrationModeChange {
            changeCalls += 1
            val previous = current
            current = newMode
            return RegistrationModeChange(previous, newMode)
        }
    }

    private class RecordingTransactionRunner : AtomicTransactionRunner {
        var invocations = 0
            private set

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocations += 1
            return block()
        }
    }

    private class RecordingAuditPublisher : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()

        override suspend fun publish(event: AdminAuditEvent) {
            events += event
        }
    }
}
