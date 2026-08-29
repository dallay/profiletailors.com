package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import io.mockk.coEvery
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
}
