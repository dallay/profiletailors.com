package com.profiletailors.smp.administrative.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AdministrativeAuditEventTest {

    @Test
    fun `construction with valid required fields and null optional succeeds`() {
        val event = AdministrativeAuditEvent(
            id = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            actorType = "USER",
            action = "workspace.update",
            targetId = "workspace-1",
            targetType = "WORKSPACE",
            correlationId = null,
            metadata = emptyMap(),
            occurredAt = Instant.now(),
        )
        assertThat(event.actorType).isEqualTo("USER")
        assertThat(event.action).isEqualTo("workspace.update")
    }

    @Test
    fun `construction with all fields including optional succeeds`() {
        val id = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val now = Instant.now()
        val event = AdministrativeAuditEvent(
            id = id,
            actorId = actorId,
            actorType = "USER",
            action = "role.assign",
            targetId = "role-1",
            targetType = "PLATFORM_ROLE",
            correlationId = "corr-123",
            metadata = mapOf("roleName" to "admin"),
            occurredAt = now,
        )
        assertThat(event.id).isEqualTo(id)
        assertThat(event.actorId).isEqualTo(actorId)
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.metadata).containsEntry("roleName", "admin")
    }

    @Test
    fun `construction with blank actorType throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "   ",
                action = "workspace.update",
                targetId = "workspace-1",
                targetType = "WORKSPACE",
                correlationId = null,
                metadata = emptyMap(),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with blank action throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "USER",
                action = "",
                targetId = "workspace-1",
                targetType = "WORKSPACE",
                correlationId = null,
                metadata = emptyMap(),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with blank targetId throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "USER",
                action = "workspace.update",
                targetId = "  ",
                targetType = "WORKSPACE",
                correlationId = null,
                metadata = emptyMap(),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with blank targetType throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "USER",
                action = "workspace.update",
                targetId = "workspace-1",
                targetType = "",
                correlationId = null,
                metadata = emptyMap(),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with sensitive key in metadata throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "USER",
                action = "workspace.update",
                targetId = "workspace-1",
                targetType = "WORKSPACE",
                correlationId = null,
                metadata = mapOf("password" to "secret123"),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with token substring in metadata key throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeAuditEvent(
                id = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                actorType = "USER",
                action = "workspace.update",
                targetId = "workspace-1",
                targetType = "WORKSPACE",
                correlationId = null,
                metadata = mapOf("userToken" to "tok-abc"),
                occurredAt = Instant.now(),
            )
        }
    }

    @Test
    fun `construction with safe metadata succeeds`() {
        val event = AdministrativeAuditEvent(
            id = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            actorType = "USER",
            action = "workspace.update",
            targetId = "workspace-1",
            targetType = "WORKSPACE",
            correlationId = null,
            metadata = mapOf("workspaceName" to "My Workspace", "targetType" to "PREMIUM"),
            occurredAt = Instant.now(),
        )
        assertThat(event.metadata).containsEntry("workspaceName", "My Workspace")
        assertThat(event.metadata).containsEntry("targetType", "PREMIUM")
    }
}
