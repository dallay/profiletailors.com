package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

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
    fun `returns empty roles when no role assignment exists (default-deny)`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns emptyList()

        val access = resolver.resolve(
            PrincipalContext(
                principalId = principalId.toString(),
                principalType = PrincipalType.USER,
                subject = "no-role@example.com",
            ),
        )

        assertEquals(principalId, access.principalId)
        assertEquals(emptySet<PlatformRole>(), access.roles)
    }

    @Test
    fun `returns multiple roles when principal has multiple active assignments`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(principalId) } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OPERATOR,
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
                principalId = principalId.toString(),
                principalType = PrincipalType.USER,
                subject = "multi-role@example.com",
            ),
        )

        assertEquals(principalId, access.principalId)
        assertEquals(setOf(PlatformRole.PLATFORM_OPERATOR, PlatformRole.AUDITOR), access.roles)
    }

    @Test
    fun `strips user- prefix before performing repository lookup`() = runTest {
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

        resolver.resolve(
            PrincipalContext(
                principalId = "user-$principalId",
                principalType = PrincipalType.USER,
                subject = "prefixed@example.com",
            ),
        )

        coVerify { roleAssignmentRepository.findActiveByPrincipalId(principalId) }
    }
}
