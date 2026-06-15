package com.profiletailors.smp.audit.infrastructure

import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NoOpAuditHookTest {

    private val hook = NoOpAuditHook()

    @Test
    fun `onRequestHandled does not throw`() = runTest {
        hook.onRequestHandled(
            requestName = "test-request",
            outcome = RequestOutcome.SUCCESS,
        )
    }

    @Test
    fun `onAuthorizationDecision does not throw`() = runTest {
        hook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "test-request",
                requestPath = "/api/test",
                permission = "test:read",
                principalId = "user-1",
                workspaceId = "workspace-1",
                decision = "ALLOW",
                reasonCode = "role_permission",
                roleKeys = emptyList(),
            ),
        )
    }

    @Test
    fun `onMutation does not throw`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.update",
                actorPrincipalId = "user-1",
                workspaceId = "workspace-1",
                targetType = "WORKSPACE",
                targetId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
                details = emptyMap(),
            ),
        )
    }
}
