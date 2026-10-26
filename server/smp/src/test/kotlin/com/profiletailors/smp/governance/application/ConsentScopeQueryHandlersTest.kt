package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ConsentScopeQueryHandlersTest {
    private val repository = mockk<ConsentRepository>()
    private val contextProvider = mockk<ResourceContextProvider>()
    private val authorization = mockk<WorkspaceAuthorizationDecider>()
    private val workspace = ConsentScope.Workspace(UUID.fromString("00000000-0000-0000-0000-000000000101"))

    @Test
    fun `tenant list and history queries always pass the authorized workspace scope`() = runTest {
        coEvery { authorization.decideDetailed(any()) } returns AuthorizationDecisionResult(
            AuthorizationDecision.ALLOW,
            AuthorizationReasonCode.ROLE_PERMISSION,
        )
        coEvery { contextProvider.require() } returns ResourceContext(
            ResourceContextType.WORKSPACE,
            workspaceId = workspace.id.toString(),
        )
        every { repository.findActiveByScope(workspace, SubjectKind.ANONYMOUS, "waitlist.early_access") } returns
            emptyFlow()
        every {
            repository.findHistoricalByIdentity(workspace, SubjectReference.anonymous("hash"), "waitlist.early_access")
        } returns emptyFlow()

        GetWorkspaceConsentRecordsHandler(repository, contextProvider, authorization).handle(
            GetWorkspaceConsentRecordsQuery(SubjectKind.ANONYMOUS, "waitlist.early_access"),
        )
        GetConsentHistoryHandler(repository, contextProvider, authorization).handle(
            GetConsentHistoryQuery(SubjectKind.ANONYMOUS, "hash", "waitlist.early_access"),
        )

        verify(exactly = 1) { repository.findActiveByScope(workspace, SubjectKind.ANONYMOUS, "waitlist.early_access") }
        verify(exactly = 1) {
            repository.findHistoricalByIdentity(workspace, SubjectReference.anonymous("hash"), "waitlist.early_access")
        }
    }

    @Test
    fun `tenant consent query rejects missing permission before resolving or querying scope`() = runTest {
        coEvery { authorization.decideDetailed(any()) } returns AuthorizationDecisionResult(
            AuthorizationDecision.DENY,
            AuthorizationReasonCode.MISSING_PERMISSION,
        )

        assertThrows<AuthorizationDeniedException> {
            GetWorkspaceConsentRecordsHandler(repository, contextProvider, authorization).handle(
                GetWorkspaceConsentRecordsQuery(null, null),
            )
        }

        coVerify(exactly = 0) { contextProvider.require() }
        verify(exactly = 0) { repository.findActiveByScope(any(), any(), any()) }
    }
}
