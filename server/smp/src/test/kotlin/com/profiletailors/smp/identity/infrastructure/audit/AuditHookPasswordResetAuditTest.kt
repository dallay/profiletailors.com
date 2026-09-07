package com.profiletailors.smp.identity.infrastructure.audit

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.identity.application.PasswordResetAuditEvent
import com.profiletailors.smp.identity.application.PasswordResetAuditUnavailableException
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AuditHookPasswordResetAuditTest {

    @Test
    fun `maps completed reset to a strictly redacted identity mutation fact`() = runTest {
        val hook = CapturingAuditHook()
        val adapter = AuditHookPasswordResetAudit(hook)
        val event = PasswordResetAuditEvent(
            principalId = "principal-123",
            occurredAt = Instant.parse("2026-07-29T12:34:56Z"),
        )

        adapter.recordCompleted(event)

        val fact = hook.mutationFacts.single()
        fact.action shouldBe "PASSWORD_RESET_COMPLETED"
        fact.targetType shouldBe "IDENTITY_PRINCIPAL"
        fact.targetId shouldBe "principal-123"
        fact.actorPrincipalId shouldBe "principal-123"
        fact.workspaceId.shouldBeNull()
        fact.details shouldContainExactly mapOf("occurredAt" to "2026-07-29T12:34:56Z")
        fact.outcome.name shouldBe "SUCCESS"
    }

    @Test
    fun `translates persistence failure to a pure application exception`() = runTest {
        val cause = DataAccessResourceFailureException("audit unavailable")
        val adapter = AuditHookPasswordResetAudit(FailingAuditHook(cause))

        val thrown = assertFailsWith<PasswordResetAuditUnavailableException> {
            adapter.recordCompleted(
                PasswordResetAuditEvent("principal-123", Instant.parse("2026-07-29T12:34:56Z")),
            )
        }

        assertSame(cause, thrown.cause)
    }

    private open class CapturingAuditHook : AuditHook {
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

    private class FailingAuditHook(private val cause: Throwable) : CapturingAuditHook() {
        override suspend fun onMutation(fact: MutationAuditFact): Unit = throw cause
    }
}
