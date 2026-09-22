package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperatorAccessResolverTest {

    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val resolver = OperatorAccessResolver(roleAssignmentRepository)

    @Test
    fun `resolves prefixed user principal ids against their platform role assignments`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OWNER,
                assignedAt = Instant.parse("2026-08-29T10:00:00Z"),
                assignedBy = principalId,
            ),
        )

        val access = resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "yunielacosta738@gmail.com",
            ),
        )

        assertEquals(principalId, access.principalId)
        assertEquals(setOf(PlatformRole.PLATFORM_OWNER), access.roles)
    }

    @Test
    fun `returns empty roles when no role assignment exists`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns emptyList()

        val access = resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "unassigned@example.com",
            ),
        )

        assertEquals(principalId, access.principalId)
        assertEquals(emptySet(), access.roles)
    }

    @Test
    fun `resolves multiple role assignments to a combined role set`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OWNER,
                assignedAt = Instant.parse("2026-08-29T10:00:00Z"),
                assignedBy = principalId,
            ),
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.AUDITOR,
                assignedAt = Instant.parse("2026-08-29T10:00:00Z"),
                assignedBy = principalId,
            ),
        )

        val access = resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "multi-role@example.com",
            ),
        )

        assertEquals(
            setOf(PlatformRole.PLATFORM_OWNER, PlatformRole.AUDITOR),
            access.roles,
        )
    }

    @Test
    fun `PLATFORM_OPERATOR has notifications read and manage permissions`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OPERATOR,
                assignedAt = Instant.parse("2026-08-29T10:00:00Z"),
                assignedBy = principalId,
            ),
        )
        val access = resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "operator@profiletailors.com",
            ),
        )
        val permissions = access.roles.effectivePermissions()
        assertTrue(permissions.contains(PlatformPermission.NOTIFICATIONS_READ))
        assertTrue(permissions.contains(PlatformPermission.NOTIFICATIONS_MANAGE))
    }

    @Test
    fun `AUDITOR has notifications read but not manage permission`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000006")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.AUDITOR,
                assignedAt = Instant.parse("2026-08-29T10:00:00Z"),
                assignedBy = principalId,
            ),
        )
        val access = resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "auditor@profiletailors.com",
            ),
        )
        val permissions = access.roles.effectivePermissions()
        assertTrue(permissions.contains(PlatformPermission.NOTIFICATIONS_READ))
        assertFalse(permissions.contains(PlatformPermission.NOTIFICATIONS_MANAGE))
    }
}
