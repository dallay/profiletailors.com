package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
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
import kotlin.test.assertTrue

class R2dbcAdminOperatorQueryTest {

    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val principalIdentityLookup = mockk<PrincipalIdentityLookup>()
    private val query = R2dbcAdminOperatorQuery(roleAssignmentRepository, principalIdentityLookup)

    @Test
    fun `listAllActive returns empty when no assignments exist`() = runTest {
        coEvery { roleAssignmentRepository.findAllActive() } returns emptyList()

        val result = query.listAllActive()

        assertEquals(0, result.size)
    }

    @Test
    fun `listAllActive groups assignments by principal`() = runTest {
        val principal1 = UUID.randomUUID()
        val principal2 = UUID.randomUUID()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val later = Instant.parse("2026-01-02T00:00:00Z")
        val assignments = listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principal1,
                role = PlatformRole.PLATFORM_OPERATOR,
                assignedAt = now,
                assignedBy = UUID.randomUUID(),
            ),
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principal1,
                role = PlatformRole.AUDITOR,
                assignedAt = later,
                assignedBy = UUID.randomUUID(),
            ),
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principal2,
                role = PlatformRole.SUPPORT_AGENT,
                assignedAt = now,
                assignedBy = UUID.randomUUID(),
            ),
        )
        coEvery { roleAssignmentRepository.findAllActive() } returns assignments
        coEvery { principalIdentityLookup.findByPrincipalId(principal1.toString()) } returns PrincipalIdentityFacts(
            principalId = principal1.toString(),
            principalType = PrincipalType.USER,
            subject = "subject-1",
            provider = null,
            displayIdentity = "Alice",
            email = "alice@example.com",
            username = "alice",
            emailStatus = EmailStatus.VERIFIED,
        )
        coEvery { principalIdentityLookup.findByPrincipalId(principal2.toString()) } returns PrincipalIdentityFacts(
            principalId = principal2.toString(),
            principalType = PrincipalType.USER,
            subject = "subject-2",
            provider = null,
            displayIdentity = "Bob",
            email = "bob@example.com",
            username = "bob",
            emailStatus = EmailStatus.VERIFIED,
        )

        val result = query.listAllActive()

        assertEquals(2, result.size)
        val operator1 = result.find { it.principalId == principal1 }!!
        assertEquals("alice@example.com", operator1.email)
        assertEquals("Alice", operator1.displayName)
        assertEquals(listOf("PLATFORM_OPERATOR", "AUDITOR"), operator1.platformRoles)
        assertEquals(now, operator1.assignedAt)
        val operator2 = result.find { it.principalId == principal2 }!!
        assertEquals("bob@example.com", operator2.email)
        assertEquals(listOf("SUPPORT_AGENT"), operator2.platformRoles)
    }

    @Test
    fun `listAllActive uses empty string when identity lookup returns null`() = runTest {
        val principalId = UUID.randomUUID()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        coEvery { roleAssignmentRepository.findAllActive() } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OWNER,
                assignedAt = now,
                assignedBy = UUID.randomUUID(),
            ),
        )
        coEvery { principalIdentityLookup.findByPrincipalId(principalId.toString()) } returns null
        coEvery { principalIdentityLookup.findByPrincipalId("user-$principalId") } returns null

        val result = query.listAllActive()

        assertEquals(1, result.size)
        assertEquals("", result[0].email)
        assertEquals(null, result[0].displayName)
    }

    @Test
    fun `should resolve a prefixed user identity when stored uuid has no direct match`() = runTest {
        val principalId = UUID.randomUUID()
        val identity = PrincipalIdentityFacts(
            principalId = "user-$principalId",
            principalType = PrincipalType.USER,
            subject = "subject-1",
            provider = null,
            displayIdentity = "Alice",
            email = "alice@example.com",
            username = "alice",
            emailStatus = EmailStatus.VERIFIED,
        )
        coEvery { roleAssignmentRepository.findAllActive() } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OWNER,
                assignedAt = Instant.parse("2026-01-01T00:00:00Z"),
                assignedBy = UUID.randomUUID(),
            ),
        )
        coEvery { principalIdentityLookup.findByPrincipalId(principalId.toString()) } returns null
        coEvery { principalIdentityLookup.findByPrincipalId("user-$principalId") } returns identity

        val result = query.listAllActive()

        assertEquals("alice@example.com", result.single().email)
        assertEquals("Alice", result.single().displayName)
    }

    @Test
    fun `listAllActive uses earliest assignment timestamp per principal`() = runTest {
        val principalId = UUID.randomUUID()
        val earliest = Instant.parse("2026-01-01T00:00:00Z")
        val later = Instant.parse("2026-01-15T00:00:00Z")
        coEvery { roleAssignmentRepository.findAllActive() } returns listOf(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.AUDITOR,
                assignedAt = later,
                assignedBy = UUID.randomUUID(),
            ),
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = principalId,
                role = PlatformRole.PLATFORM_OPERATOR,
                assignedAt = earliest,
                assignedBy = UUID.randomUUID(),
            ),
        )
        coEvery { principalIdentityLookup.findByPrincipalId(principalId.toString()) } returns null
        coEvery { principalIdentityLookup.findByPrincipalId("user-$principalId") } returns null

        val result = query.listAllActive()

        assertEquals(earliest, result[0].assignedAt)
        assertTrue(result[0].platformRoles.contains("AUDITOR"))
        assertTrue(result[0].platformRoles.contains("PLATFORM_OPERATOR"))
    }
}
