package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.RevokePlatformRoleCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
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

class RevokePlatformRoleHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")

    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val handler = RevokePlatformRoleHandler(
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
    fun `does nothing when no active assignment matches the role`() = runTest {
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(targetId) } returns emptyList()

        handler.handle(command())

        coVerify(exactly = 0) { roleAssignmentRepository.update(any()) }
        coVerify(exactly = 0) { auditPublisher.publish(any()) }
    }

    @Test
    fun `revokes matching assignment with audit timestamp and publisher`() = runTest {
        val matching = assignment(PlatformRole.SUPPORT_AGENT)
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(targetId) } returns listOf(matching)
        coEvery { roleAssignmentRepository.update(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify(exactly = 1) {
            roleAssignmentRepository.update(
                match { assignment ->
                    assignment.role == PlatformRole.SUPPORT_AGENT &&
                        assignment.revokedAt == clock.instant() &&
                        assignment.revokedBy == operatorId &&
                        !assignment.isActive
                },
            )
        }
        coVerify {
            auditPublisher.publish(
                match { event ->
                    event.action == AdminAuditAction.PLATFORM_ROLE_REVOKED &&
                        event.targetId == targetId.toString() &&
                        event.metadata["role"] == PlatformRole.SUPPORT_AGENT.name
                },
            )
        }
    }

    @Test
    fun `only revokes assignments matching the requested role`() = runTest {
        val matching = assignment(PlatformRole.SUPPORT_AGENT)
        val other = assignment(PlatformRole.AUDITOR)
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(targetId) } returns listOf(matching, other)
        coEvery { roleAssignmentRepository.update(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify(exactly = 1) { roleAssignmentRepository.update(match { it.role == PlatformRole.SUPPORT_AGENT }) }
    }

    private fun command(roles: Set<PlatformRole> = ownerRoles) = RevokePlatformRoleCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        targetPrincipalId = targetId,
        role = PlatformRole.SUPPORT_AGENT,
    )

    private fun assignment(role: PlatformRole) = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId.generate(),
        principalId = targetId,
        role = role,
        assignedAt = clock.instant().minusSeconds(3600),
        assignedBy = operatorId,
    )
}
