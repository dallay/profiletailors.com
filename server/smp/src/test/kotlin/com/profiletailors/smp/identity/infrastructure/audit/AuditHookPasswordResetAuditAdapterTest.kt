package com.profiletailors.smp.identity.infrastructure.audit

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.identity.application.PasswordResetAuditEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditHookPasswordResetAuditAdapterTest {

    @Test
    fun `maps completed reset to a strictly redacted identity mutation fact`() = runTest {
        val hook = CapturingAuditHook()
        val adapter = AuditHookPasswordResetAuditAdapter(hook)
        val event = PasswordResetAuditEvent(
            principalId = "principal-123",
            occurredAt = Instant.parse("2026-07-29T12:34:56Z"),
        )

        adapter.recordCompleted(event)

        val fact = hook.mutationFacts.single()
        assertEquals("PASSWORD_RESET_COMPLETED", fact.action)
        assertEquals("IDENTITY_PRINCIPAL", fact.targetType)
        assertEquals("principal-123", fact.targetId)
        assertEquals("principal-123", fact.actorPrincipalId)
        assertEquals(null, fact.workspaceId)
        assertEquals(mapOf("occurredAt" to "2026-07-29T12:34:56Z"), fact.details)

        val serialized = fact.toString().lowercase()
        listOf(
            "raw-token-sensitive",
            "newpassword123!",
            "password-hash-sensitive",
            "person@example.com",
            "203.0.113.42",
        ).forEach { secret -> assertFalse(serialized.contains(secret.lowercase())) }
    }

    private class CapturingAuditHook : AuditHook {
        val mutationFacts = mutableListOf<MutationAuditFact>()

        override suspend fun onRequestHandled(
            requestName: String,
            outcome: com.profiletailors.common.domain.observability.RequestOutcome,
        ) = Unit

        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit

        override suspend fun onMutation(fact: MutationAuditFact) {
            mutationFacts += fact
        }
    }
}
