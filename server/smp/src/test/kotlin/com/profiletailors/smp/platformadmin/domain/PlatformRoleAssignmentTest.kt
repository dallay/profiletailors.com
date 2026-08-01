package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class PlatformRoleAssignmentTest {

    private val now = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun assignment(role: PlatformRole = PlatformRole.PLATFORM_OPERATOR) = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId.generate(),
        principalId = targetId,
        role = role,
        assignedAt = now,
        assignedBy = operatorId,
    )

    @Test
    fun `active assignment grants isActive true`() {
        assertTrue(assignment().isActive)
    }

    @Test
    fun `revoked assignment grants isActive false`() {
        val revoked = assignment().revoke(now.plusSeconds(60), operatorId)
        assertFalse(revoked.isActive)
    }

    @Test
    fun `revoke sets revokedAt and revokedBy`() {
        val revokedAt = now.plusSeconds(120)
        val revoked = assignment().revoke(revokedAt, operatorId)
        assertEquals(revokedAt, revoked.revokedAt)
        assertEquals(operatorId, revoked.revokedBy)
    }

    @Test
    fun `revoking an already-revoked assignment throws`() {
        val revoked = assignment().revoke(now.plusSeconds(1), operatorId)
        assertThrows<PlatformRoleAlreadyRevokedException> { revoked.revoke(now.plusSeconds(2), operatorId) }
    }
}
