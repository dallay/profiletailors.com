package com.profiletailors.smp.audit.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuditFactsTest {

    @Test
    fun `AuthorizationDecisionAuditFact accepts valid fact`() {
        assertDoesNotThrow {
            AuthorizationDecisionAuditFact(
                requestName = "POST /api/publish",
                requestPath = "/api/publish",
                permission = "publishing:write",
                principalId = "user-1",
                workspaceId = "workspace-1",
                decision = "ALLOWED",
                reasonCode = "PERMIT",
                roleKeys = listOf("admin"),
            )
        }
    }

    @Test
    fun `AuthorizationDecisionAuditFact rejects blank permission`() {
        assertThrows<IllegalArgumentException> {
            AuthorizationDecisionAuditFact(
                requestName = "POST /api/publish",
                requestPath = "/api/publish",
                permission = "   ",
                principalId = "user-1",
                workspaceId = "workspace-1",
                decision = "ALLOWED",
                reasonCode = "PERMIT",
            )
        }
    }

    @Test
    fun `AuthorizationDecisionAuditFact rejects blank principalId`() {
        assertThrows<IllegalArgumentException> {
            AuthorizationDecisionAuditFact(
                requestName = "POST /api/publish",
                requestPath = "/api/publish",
                permission = "publishing:write",
                principalId = "",
                workspaceId = "workspace-1",
                decision = "ALLOWED",
                reasonCode = "PERMIT",
            )
        }
    }

    @Test
    fun `MutationAuditFact accepts valid fact`() {
        assertDoesNotThrow {
            MutationAuditFact(
                action = "PUBLISH_POST",
                targetType = "Publication",
                targetId = "pub-1",
                actorPrincipalId = "user-1",
                workspaceId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf("provider" to "LINKEDIN"),
            )
        }
    }

    @Test
    fun `MutationAuditFact rejects blank action`() {
        assertThrows<IllegalArgumentException> {
            MutationAuditFact(
                action = "  ",
                targetType = "Publication",
                targetId = "pub-1",
                actorPrincipalId = "user-1",
                workspaceId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
            )
        }
    }

    @Test
    fun `MutationAuditFact rejects blank actorPrincipalId`() {
        assertThrows<IllegalArgumentException> {
            MutationAuditFact(
                action = "PUBLISH_POST",
                targetType = "Publication",
                targetId = "pub-1",
                actorPrincipalId = "",
                workspaceId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
            )
        }
    }
}
