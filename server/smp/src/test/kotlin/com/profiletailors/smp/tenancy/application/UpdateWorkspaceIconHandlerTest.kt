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

class UpdateWorkspaceIconHandlerTest {
    private val resourceContextProvider = mockk<ResourceContextProvider>()
    private val workspaceMutationRepository = mockk<WorkspaceMutationRepository>()
    private val workspaceAuthorizationDecider = mockk<WorkspaceAuthorizationDecider>()
    private val handler = UpdateWorkspaceIconHandler(
        resourceContextProvider,
        workspaceMutationRepository,
        workspaceAuthorizationDecider,
    )

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should update icon when authorized`() = runTest {
        val workspaceId = "ws-1"
        val icon = "rocket-ship"

        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.require() } returns ctx
        every { resourceContextProvider.current() } returns ctx

        coEvery { workspaceMutationRepository.updateIcon(workspaceId, any()) } returns true

        val result = handler.handle(UpdateWorkspaceIconCommand(icon))

        result.workspaceId shouldBe workspaceId
        result.icon shouldBe icon
    }

    @Test
    fun `should throw exception when authorization is denied`() = runTest {
        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.DENY, AuthorizationReasonCode.MISSING_PERMISSION)

        shouldThrow<AuthorizationDeniedException> {
            handler.handle(UpdateWorkspaceIconCommand("rocket"))
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

        coEvery { workspaceMutationRepository.updateIcon(workspaceId, any()) } returns false

        shouldThrow<IllegalStateException> {
            handler.handle(UpdateWorkspaceIconCommand("rocket"))
        }
    }

    @Test
    fun `should throw exception when icon name is invalid`() = runTest {
        val workspaceId = "ws-1"
        coEvery { workspaceAuthorizationDecider.decideDetailed(any(), any(), any()) } returns
            AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.require() } returns ctx
        every { resourceContextProvider.current() } returns ctx

        shouldThrow<IllegalArgumentException> {
            handler.handle(UpdateWorkspaceIconCommand("Invalid_Icon!"))
        }
    }
}
