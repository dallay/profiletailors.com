package com.profiletailors.smp.privacy.infrastructure.audit

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditFact
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditOutcome
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditPort
import com.profiletailors.smp.privacy.application.PrivacyMutationAuditor
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PrivacyMutationAuditorTest {

    private val auditPort = mockk<PrivacyMutationAuditPort>(relaxed = true)
    private val principalContextProvider = object : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "test-principal",
            principalType = PrincipalType.USER,
            subject = "test@example.com",
        )
    }
    private val auditor = PrivacyMutationAuditor(principalContextProvider, auditPort)

    @Test
    fun `records dsar submitted event with sentinel workspace`() = runTest {
        auditor.recordSuccess(
            action = "dsar.submitted",
            requestId = "dsr-123",
            details = mapOf("type" to "ACCESS"),
        )

        coVerify {
            auditPort.onMutation(
                PrivacyMutationAuditFact(
                    action = "dsar.submitted",
                    targetType = "DATA_SUBJECT_REQUEST",
                    targetId = "dsr-123",
                    actorPrincipalId = "test-principal",
                    workspaceId = "__DSAR__",
                    outcome = PrivacyMutationAuditOutcome.SUCCESS,
                    details = mapOf("type" to "ACCESS"),
                ),
            )
        }
    }

    @Test
    fun `records dsar status changed event`() = runTest {
        auditor.recordSuccess(
            action = "dsar.status_changed",
            requestId = "dsr-123",
            details = mapOf("from" to "PENDING", "to" to "COMPLETED"),
        )

        coVerify {
            auditPort.onMutation(
                PrivacyMutationAuditFact(
                    action = "dsar.status_changed",
                    targetType = "DATA_SUBJECT_REQUEST",
                    targetId = "dsr-123",
                    actorPrincipalId = "test-principal",
                    workspaceId = "__DSAR__",
                    outcome = PrivacyMutationAuditOutcome.SUCCESS,
                    details = mapOf("from" to "PENDING", "to" to "COMPLETED"),
                ),
            )
        }
    }

    @Test
    fun `records dsar completed event`() = runTest {
        auditor.recordSuccess(
            action = "dsar.completed",
            requestId = "dsr-123",
            details = mapOf("type" to "EXPORT"),
        )

        coVerify {
            auditPort.onMutation(
                PrivacyMutationAuditFact(
                    action = "dsar.completed",
                    targetType = "DATA_SUBJECT_REQUEST",
                    targetId = "dsr-123",
                    actorPrincipalId = "test-principal",
                    workspaceId = "__DSAR__",
                    outcome = PrivacyMutationAuditOutcome.SUCCESS,
                    details = mapOf("type" to "EXPORT"),
                ),
            )
        }
    }

    @Test
    fun `records rejected event`() = runTest {
        auditor.recordRejected(
            action = "dsar.submitted",
            requestId = "dsr-123",
            reason = "rate_limit_exceeded",
            details = mapOf("type" to "ACCESS"),
        )

        coVerify {
            auditPort.onMutation(
                PrivacyMutationAuditFact(
                    action = "dsar.submitted",
                    targetType = "DATA_SUBJECT_REQUEST",
                    targetId = "dsr-123",
                    actorPrincipalId = "test-principal",
                    workspaceId = "__DSAR__",
                    outcome = PrivacyMutationAuditOutcome.REJECTED,
                    details = mapOf("type" to "ACCESS", "reason" to "rate_limit_exceeded"),
                ),
            )
        }
    }
}
