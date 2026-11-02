package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AdminAuditEventTest {
    @Test
    fun `bootstrap audit event records a typed system actor without a human identity`() {
        val event = AdminAuditEvent(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.parse("2026-11-02T10:00:00Z"),
            operatorPrincipalId = null,
            operatorPlatformRoles = emptySet(),
            actorKind = AdminActorKind.SYSTEM_BOOTSTRAP,
            action = AdminAuditAction.BOOTSTRAP_INVITATION_ISSUED,
            targetType = "PLATFORM_BOOTSTRAP_INVITATION",
            targetId = "initial-owner",
            result = AdminAuditResult.SUCCEEDED,
        )

        assertNull(event.operatorPrincipalId)
        assertEquals(emptySet<PlatformRole>(), event.operatorPlatformRoles)
        assertEquals(AdminActorKind.SYSTEM_BOOTSTRAP, event.actorKind)
    }
}
