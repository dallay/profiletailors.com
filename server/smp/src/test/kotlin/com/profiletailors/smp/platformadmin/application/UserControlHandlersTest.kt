package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.identity.application.AccountStateGateway
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.command.DisableUserCommand
import com.profiletailors.smp.platformadmin.application.command.EnableUserCommand
import com.profiletailors.smp.platformadmin.application.command.RevokeUserSessionsCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.UserControlTelemetry
import com.profiletailors.smp.platformadmin.application.handler.UserControlHandlers
import com.profiletailors.smp.platformadmin.application.handler.UserControlStateConflictException
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.UserNotFoundException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UserControlHandlersTest {
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userId = "user-1"
    private val clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `disables user and revokes sessions atomically`() = runTest {
        val state = FakeAccountStateGateway(UserAccountState.ACTIVE)
        val sessions = FakeRefreshSessionLifecycleService(3)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(state, sessions, audit, telemetry)

        val result = handlers.disable(
            DisableUserCommand(
                operatorId,
                setOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER),
                userId,
            ),
        )

        assertEquals(UserAccountState.DISABLED, result.accountState)
        assertEquals(3, result.revokedSessionCount)
        assertEquals(listOf("DISABLED"), state.updates)
        assertEquals(listOf(userId), sessions.revoked)
        assertEquals(1, audit.events.size)
        assertEquals("USER_DISABLED", audit.events.single().action.name)
        assertEquals("SUCCEEDED", audit.events.single().result.name)
        assertEquals(listOf("disable:success"), telemetry.records)
    }

    @Test
    fun `fails without reporting disabled when session revocation fails`() = runTest {
        val state = FakeAccountStateGateway(UserAccountState.ACTIVE)
        val sessions = FakeRefreshSessionLifecycleService(failure = IllegalStateException("failed"))
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(state, sessions, audit, telemetry)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER),
                        userId,
                    ),
                )
            }
        }

        assertEquals(listOf("DISABLED"), state.updates)
        assertEquals(1, audit.events.size)
        assertEquals("FAILED", audit.events.single().result.name)
        assertEquals(listOf("disable:failure"), telemetry.records)
    }

    @Test
    fun `records rejected audit when operator lacks manage permission`() = runTest {
        val audit = RecordingAuditPublisher()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(),
            audit,
            RecordingUserControlTelemetry(),
        )

        assertThrows(com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.SUPPORT_AGENT),
                        userId,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_DISABLED", audit.events.single().action.name)
        assertEquals(0, audit.events.single().metadata.size)
    }

    @Test
    fun `records rejected audit when permission check has no authenticated operator`() = runTest {
        val audit = RecordingAuditPublisher()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(),
            audit,
            RecordingUserControlTelemetry(),
        )

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        UUID(0, 0),
                        emptySet<PlatformRole>(),
                        userId,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals(UUID(0, 0), audit.events.single().operatorPrincipalId)
        assertEquals(userId, audit.events.single().targetId)
    }

    @Test
    fun `records failed audit with a generic reason when operation fails`() = runTest {
        val audit = RecordingAuditPublisher()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(failure = IllegalStateException("token secret leaked")),
            audit,
            RecordingUserControlTelemetry(),
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        userId,
                    ),
                )
            }
        }

        assertEquals("FAILED", audit.events.single().result.name)
        assertEquals("User control operation failed.", audit.events.single().reason)
    }

    @Test
    fun `records failed audit for a missing target`() = runTest {
        val audit = RecordingAuditPublisher()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(),
            audit,
            RecordingUserControlTelemetry(),
        )

        assertThrows(UserNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        "missing-secret-user",
                    ),
                )
            }
        }

        assertEquals("FAILED", audit.events.single().result.name)
        assertEquals("User control operation failed.", audit.events.single().reason)
    }

    @Test
    fun `enables a disabled user`() = runTest {
        val state = FakeAccountStateGateway(UserAccountState.DISABLED)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(state, FakeRefreshSessionLifecycleService(0), audit, telemetry)

        val result = handlers.enable(
            EnableUserCommand(
                operatorId,
                setOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER),
                userId,
            ),
        )

        assertEquals(UserAccountState.ACTIVE, result.accountState)
        assertEquals(0, result.revokedSessionCount)
        assertEquals("USER_ENABLED", audit.events.single().action.name)
    }

    @Test
    fun `revokes sessions and returns affected count`() = runTest {
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(2),
            audit,
            telemetry,
        )

        val result = handlers.revokeSessions(
            RevokeUserSessionsCommand(
                operatorId,
                setOf(com.profiletailors.smp.platformadmin.domain.PlatformRole.PLATFORM_OWNER),
                userId,
            ),
        )

        assertEquals(2, result.revokedSessionCount)
        assertEquals("USER_SESSIONS_REVOKED", audit.events.single().action.name)
    }

    @Test
    fun `should reject enable audit when manage permission is missing`() = runTest {
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.DISABLED),
            FakeRefreshSessionLifecycleService(),
            audit,
            telemetry,
        )

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.enable(
                    EnableUserCommand(
                        operatorId,
                        setOf(PlatformRole.SUPPORT_AGENT),
                        userId,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_ENABLED", audit.events.single().action.name)
        assertEquals(listOf("enable:rejected"), telemetry.records)
    }

    @Test
    fun `should reject revoke audit when manage permission is missing`() = runTest {
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(),
            audit,
            telemetry,
        )

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.revokeSessions(
                    RevokeUserSessionsCommand(
                        operatorId,
                        setOf(PlatformRole.SUPPORT_AGENT),
                        userId,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_SESSIONS_REVOKED", audit.events.single().action.name)
        assertEquals(listOf("sessions_revoke:rejected"), telemetry.records)
    }

    @Test
    fun `should record failed audit when session revocation fails`() = runTest {
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(
            FakeAccountStateGateway(UserAccountState.ACTIVE),
            FakeRefreshSessionLifecycleService(failure = IllegalStateException("revocation failed")),
            audit,
            telemetry,
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.revokeSessions(
                    RevokeUserSessionsCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        userId,
                    ),
                )
            }
        }

        assertEquals("FAILED", audit.events.single().result.name)
        assertEquals(listOf("sessions_revoke:failure"), telemetry.records)
    }

    @Test
    fun `should confirm disable as no-op when the user is already disabled`() = runTest {
        val state = FakeAccountStateGateway(UserAccountState.DISABLED)
        val sessions = FakeRefreshSessionLifecycleService(2)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(state, sessions, audit, telemetry)

        val result = handlers.disable(
            DisableUserCommand(
                operatorId,
                setOf(PlatformRole.PLATFORM_OWNER),
                userId,
            ),
        )

        assertEquals(UserAccountState.DISABLED, result.accountState)
        assertEquals(2, result.revokedSessionCount)
        assertEquals(emptyList<String>(), state.updates)
        assertEquals(listOf("disable:success"), telemetry.records)
    }

    @Test
    fun `should throw state conflict when the transition cannot apply`() = runTest {
        val gateway = mockk<AccountStateGateway>()
        coEvery { gateway.findAccountState(any()) } returns UserAccountState.ACTIVE
        coEvery { gateway.changeAccountState(any(), any(), any()) } returns false
        val handlers = handlers(
            gateway,
            FakeRefreshSessionLifecycleService(),
            RecordingAuditPublisher(),
            RecordingUserControlTelemetry(),
        )

        assertThrows(UserControlStateConflictException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        userId,
                    ),
                )
            }
        }
    }

    @Test
    fun `rejects self-disable without touching account state or sessions`() = runTest {
        val selfTarget = operatorId.toString()
        val gateway = mockk<AccountStateGateway>()
        coEvery { gateway.findAccountState(selfTarget) } returns UserAccountState.ACTIVE
        coEvery { gateway.changeAccountState(any(), any(), any()) } returns true
        val sessions = FakeRefreshSessionLifecycleService(3)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(gateway, sessions, audit, telemetry)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.disable(
                    DisableUserCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        selfTarget,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_DISABLED", audit.events.single().action.name)
        assertEquals(selfTarget, audit.events.single().targetId)
        assertEquals(listOf("disable:rejected"), telemetry.records)
        assertEquals(emptyList<String>(), sessions.revoked)
        coVerify(exactly = 0) { gateway.changeAccountState(any(), any(), any()) }
    }

    @Test
    fun `rejects self-revoke without revoking sessions`() = runTest {
        val selfTarget = operatorId.toString()
        val gateway = mockk<AccountStateGateway>()
        coEvery { gateway.findAccountState(selfTarget) } returns UserAccountState.ACTIVE
        val sessions = FakeRefreshSessionLifecycleService(2)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(gateway, sessions, audit, telemetry)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.revokeSessions(
                    RevokeUserSessionsCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        selfTarget,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_SESSIONS_REVOKED", audit.events.single().action.name)
        assertEquals(selfTarget, audit.events.single().targetId)
        assertEquals(listOf("sessions_revoke:rejected"), telemetry.records)
        assertEquals(emptyList<String>(), sessions.revoked)
    }

    @Test
    fun `rejects self-enable without touching account state`() = runTest {
        val selfTarget = operatorId.toString()
        val gateway = mockk<AccountStateGateway>()
        coEvery { gateway.findAccountState(selfTarget) } returns UserAccountState.DISABLED
        coEvery { gateway.changeAccountState(any(), any(), any()) } returns true
        val sessions = FakeRefreshSessionLifecycleService(0)
        val audit = RecordingAuditPublisher()
        val telemetry = RecordingUserControlTelemetry()
        val handlers = handlers(gateway, sessions, audit, telemetry)

        assertThrows(PlatformAccessDeniedException::class.java) {
            kotlinx.coroutines.runBlocking {
                handlers.enable(
                    EnableUserCommand(
                        operatorId,
                        setOf(PlatformRole.PLATFORM_OWNER),
                        selfTarget,
                    ),
                )
            }
        }

        assertEquals("REJECTED", audit.events.single().result.name)
        assertEquals("USER_ENABLED", audit.events.single().action.name)
        assertEquals(selfTarget, audit.events.single().targetId)
        assertEquals(listOf("enable:rejected"), telemetry.records)
        coVerify(exactly = 0) { gateway.changeAccountState(any(), any(), any()) }
    }

    private fun handlers(
        state: AccountStateGateway,
        sessions: FakeRefreshSessionLifecycleService,
        audit: RecordingAuditPublisher,
        telemetry: RecordingUserControlTelemetry,
    ) = UserControlHandlers(
        accountStateGateway = state,
        refreshSessionLifecycleService = sessions,
        auditPublisher = audit,
        transactionRunner = object : AtomicTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
        },
        clock = clock,
        telemetry = telemetry,
    )

    private class FakeAccountStateGateway(private var current: UserAccountState) : AccountStateGateway {
        val updates = mutableListOf<String>()

        override suspend fun findAccountState(principalId: String): UserAccountState? =
            if (principalId == "user-1") current else null

        override suspend fun changeAccountState(
            principalId: String,
            expected: UserAccountState,
            replacement: UserAccountState,
        ): Boolean {
            if (principalId != "user-1" || current != expected) return false
            current = replacement
            updates += replacement.name
            return true
        }
    }

    private class FakeRefreshSessionLifecycleService(
        private val count: Int = 0,
        private val failure: Throwable? = null,
    ) : RefreshSessionLifecycleService(
        refreshSessionGateway = TestRefreshSessionGateway,
        refreshSessionTokenService = com.profiletailors.smp.credentials.application.RefreshSessionTokenService(),
        properties = com.profiletailors.smp.credentials.application.RefreshSessionProperties(
            cookieName = "test-refresh",
            cookiePath = "/",
            sameSite = "Lax",
            secure = false,
            ttlSeconds = 3600,
        ),
        clock = Clock.systemUTC(),
    ) {
        val revoked = mutableListOf<String>()

        override suspend fun revokeAllForPrincipal(principalId: String): Int {
            failure?.let { throw it }
            revoked += principalId
            return count
        }
    }

    private object TestRefreshSessionGateway : com.profiletailors.smp.credentials.application.RefreshSessionGateway {
        override suspend fun create(
            principalId: String,
            refreshToken: com.profiletailors.smp.credentials.application.RefreshSessionToken,
            expiresAt: Instant,
        ): com.profiletailors.smp.credentials.application.CreatedRefreshSession = error("not used")

        override suspend fun requireActive(
            refreshToken: com.profiletailors.smp.credentials.application.RefreshSessionToken,
            now: Instant,
        ): com.profiletailors.smp.credentials.application.ActiveRefreshSession = error("not used")

        override suspend fun rotate(
            currentSessionId: String,
            replacementToken: com.profiletailors.smp.credentials.application.RefreshSessionToken,
            expiresAt: Instant,
            now: Instant,
        ): com.profiletailors.smp.credentials.application.CreatedRefreshSession = error("not used")

        override suspend fun revoke(currentSessionId: String, now: Instant) = Unit
    }

    private class RecordingAuditPublisher : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()

        override suspend fun publish(event: AdminAuditEvent) {
            events += event
        }
    }

    private class RecordingUserControlTelemetry : UserControlTelemetry {
        val records = mutableListOf<String>()

        override fun record(operation: String, outcome: String) {
            records += "$operation:$outcome"
        }
    }
}
