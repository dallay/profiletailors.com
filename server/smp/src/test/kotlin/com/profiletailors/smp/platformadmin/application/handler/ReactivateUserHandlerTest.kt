package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.identity.application.PrincipalLifecycle
import com.profiletailors.smp.identity.application.PrincipalStatusTransition
import com.profiletailors.smp.platformadmin.application.command.ReactivateUserCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ReactivateUserHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principalId = "user-xyz-789"

    private val principalLifecycleService = mockk<PrincipalLifecycle>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val handler = ReactivateUserHandler(
        principalLifecycleService = principalLifecycleService,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)
    private val supportRoles = setOf(PlatformRole.SUPPORT_AGENT)

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks reactivate permission`() = runTest {
        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command(roles = supportRoles))
        }
    }

    @Test
    fun `audits on successful transition`() = runTest {
        coEvery { principalLifecycleService.reactivate(principalId, 1) } returns PrincipalStatusTransition.TRANSITIONED

        handler.handle(command())

        coVerify { auditPublisher.publish(match { it.action.name == "USER_ENABLED" }) }
    }

    @Test
    fun `skips audit when already active`() = runTest {
        coEvery { principalLifecycleService.reactivate(principalId, 1) } returns
            PrincipalStatusTransition.ALREADY_IN_TARGET_STATE

        handler.handle(command())

        coVerify(exactly = 0) { auditPublisher.publish(any()) }
    }

    private fun command(roles: Set<PlatformRole> = operatorRoles, expectedVersion: Long = 1) = ReactivateUserCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        principalId = principalId,
        expectedVersion = expectedVersion,
    )
}
