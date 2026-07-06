package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class RenameWorkspaceHandlerTest {
    private val resourceContextProvider = mockk<ResourceContextProvider>()
    private val workspaceMutationRepository = mockk<WorkspaceMutationRepository>()
    private val workspaceAuthorizationDecider = mockk<WorkspaceAuthorizationDecider>()
    private val handler = RenameWorkspaceHandler(
        resourceContextProvider,
        workspaceMutationRepository,
        workspaceAuthorizationDecider,
    )

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should rename workspace when authorized`() = runTest {
        val workspaceId = "ws-1"
        val newName = "New Studio Name"

        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.require() } returns ctx
        every { resourceContextProvider.current() } returns ctx

        coEvery { workspaceMutationRepository.rename(workspaceId, any()) } returns true

        val result = handler.handle(RenameWorkspaceCommand(newName))

        result.workspaceId shouldBe workspaceId
        result.name shouldBe newName
    }

    @Test
    fun `should throw exception when authorization is denied`() = runTest {
        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.DENY, AuthorizationReasonCode.MISSING_PERMISSION)

        shouldThrow<AuthorizationDeniedException> {
            handler.handle(RenameWorkspaceCommand("New Name"))
        }
    }

    @Test
    fun `should throw exception when repository update fails`() = runTest {
        val workspaceId = "ws-1"
        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.require() } returns ctx
        every { resourceContextProvider.current() } returns ctx

        coEvery { workspaceMutationRepository.rename(workspaceId, any()) } returns false

        shouldThrow<IllegalStateException> {
            handler.handle(RenameWorkspaceCommand("New Name"))
        }
    }

    @Test
    fun `should throw exception when workspace name is blank`() = runTest {
        val workspaceId = "ws-1"
        coEvery { workspaceAuthorizationDecider.decideDetailed(any(), any(), any()) } returns
            AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.require() } returns ctx
        every { resourceContextProvider.current() } returns ctx

        shouldThrow<IllegalArgumentException> {
            handler.handle(RenameWorkspaceCommand("   "))
        }
    }
}
