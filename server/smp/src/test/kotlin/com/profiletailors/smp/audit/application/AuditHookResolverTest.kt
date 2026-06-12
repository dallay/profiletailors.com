package com.profiletailors.smp.audit.application

import com.profiletailors.smp.audit.domain.AuditHook
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AuditHookResolverTest {

    @Test
    fun `resolve with auditEnabled calls createR2dbcHook`() {
        val r2dbcHook = FakeAuditHook()
        val noOpHook = FakeAuditHook()
        val supplier = FakeAuditHookSupplier(
            r2dbcHook = r2dbcHook,
            noOpHook = noOpHook,
        )

        val result = AuditHookResolver.resolve(auditEnabled = true, supplier = supplier)

        assertSame(r2dbcHook, result)
    }

    @Test
    fun `resolve with auditEnabled false calls createNoOpHook`() {
        val r2dbcHook = FakeAuditHook()
        val noOpHook = FakeAuditHook()
        val supplier = FakeAuditHookSupplier(
            r2dbcHook = r2dbcHook,
            noOpHook = noOpHook,
        )

        val result = AuditHookResolver.resolve(auditEnabled = false, supplier = supplier)

        assertSame(noOpHook, result)
    }

    private class FakeAuditHook : AuditHook {
        override suspend fun onRequestHandled(
            requestName: String,
            outcome: com.profiletailors.common.domain.observability.RequestOutcome,
        ) = Unit

        override suspend fun onAuthorizationDecision(
            fact: com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact,
        ) = Unit

        override suspend fun onMutation(
            fact: com.profiletailors.smp.audit.domain.MutationAuditFact,
        ) = Unit
    }

    private class FakeAuditHookSupplier(
        private val r2dbcHook: AuditHook,
        private val noOpHook: AuditHook,
    ) : AuditHookSupplier {
        override fun createR2dbcHook(): AuditHook = r2dbcHook
        override fun createNoOpHook(): AuditHook = noOpHook
    }
}
