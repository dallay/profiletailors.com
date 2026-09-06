package com.profiletailors.smp.audit.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AuditFactsTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `authorization decision rejects a blank permission`(permission: String) {
        shouldThrow<IllegalArgumentException> {
            authorizationDecision(permission = permission)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `authorization decision rejects a blank principal id`(principalId: String) {
        shouldThrow<IllegalArgumentException> {
            authorizationDecision(principalId = principalId)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `mutation rejects a blank action`(action: String) {
        shouldThrow<IllegalArgumentException> {
            mutation(action = action)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `mutation rejects a blank actor principal id`(actorPrincipalId: String) {
        shouldThrow<IllegalArgumentException> {
            mutation(actorPrincipalId = actorPrincipalId)
        }
    }

    @Test
    fun `audit facts retain valid required identifiers`() {
        authorizationDecision().permission shouldBe "workspace.read"
        mutation().actorPrincipalId shouldBe "principal-1"
    }

    private fun authorizationDecision(permission: String = "workspace.read", principalId: String = "principal-1") =
        AuthorizationDecisionAuditFact(
            requestName = "get-workspace",
            requestPath = "/api/workspaces/1",
            permission = permission,
            principalId = principalId,
            workspaceId = "workspace-1",
            decision = "allowed",
            reasonCode = "role_granted",
        )

    private fun mutation(action: String = "workspace.updated", actorPrincipalId: String = "principal-1") =
        MutationAuditFact(
            action = action,
            targetType = "workspace",
            targetId = "workspace-1",
            actorPrincipalId = actorPrincipalId,
            workspaceId = "workspace-1",
            outcome = MutationAuditOutcome.SUCCESS,
        )
}
