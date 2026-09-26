package com.profiletailors.smp.publishing.domain

import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.VerificationMode
import io.github.anschnapp.mutflow.junit.MutFlowTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MutFlowTest(verificationMode = VerificationMode.LENIENT)
class PublicationRetryMutationBaselineTest {
    private val draft =
        PublicationDraft(
            id = "pub-mutation-baseline",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.DRAFT,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Hello LinkedIn",
        )

    @Test
    fun `retry allowed only from failed state`() {
        MutFlow.underTest {
            PublicationLifecyclePolicy.requireRetryable(draft.copy(status = PublicationStatus.FAILED))
        }
        assertThrows(PublicationRetryNotAllowedException::class.java) {
            MutFlow.underTest {
                PublicationLifecyclePolicy.requireRetryable(draft)
            }
        }
    }
}
