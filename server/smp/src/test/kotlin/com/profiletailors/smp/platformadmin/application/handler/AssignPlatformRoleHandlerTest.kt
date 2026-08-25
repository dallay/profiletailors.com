package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.AssignPlatformRoleCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
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

class AssignPlatformRoleHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")

    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val handler = AssignPlatformRoleHandler(
        roleAssignmentRepository = roleAssignmentRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    private val ownerRoles = setOf(PlatformRole.PLATFORM_OWNER)
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks manage permission`() = runTest {
        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command(roles = operatorRoles))
        }
    }

    @Test
    fun `saves role assignment for target principal with audit timestamp`() = runTest {
        coEvery { roleAssignmentRepository.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            roleAssignmentRepository.save(
                match { assignment ->
                    assignment.principalId == targetId &&
                        assignment.role == PlatformRole.SUPPORT_AGENT &&
                        assignment.assignedAt == clock.instant() &&
                        assignment.assignedBy == operatorId &&
                        assignment.isActive
                },
            )
        }
    }

    @Test
    fun `publishes PLATFORM_ROLE_ASSIGNED audit event with role metadata`() = runTest {
        coEvery { roleAssignmentRepository.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            auditPublisher.publish(
                match { event ->
                    event.action == AdminAuditAction.PLATFORM_ROLE_ASSIGNED &&
                        event.targetType == "Principal" &&
                        event.targetId == targetId.toString() &&
                        event.operatorPrincipalId == operatorId &&
                        event.metadata["role"] == PlatformRole.SUPPORT_AGENT.name
                },
            )
        }
    }

    private fun command(roles: Set<PlatformRole> = ownerRoles) = AssignPlatformRoleCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        targetPrincipalId = targetId,
        role = PlatformRole.SUPPORT_AGENT,
    )
}
