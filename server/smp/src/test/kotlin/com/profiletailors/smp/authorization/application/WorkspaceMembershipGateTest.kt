package com.profiletailors.smp.authorization.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WorkspaceMembershipGateTest {

    @Test
    fun `should allow access when membership is active`() = runTest {
        val gate = WorkspaceMembershipGate(fixedPrincipal(), memberResolver(WorkspaceMembershipStatus.ACTIVE))

        gate.requireActiveMember("workspace-1")
    }

    @Test
    fun `should deny access when membership is missing`() = runTest {
        val gate = WorkspaceMembershipGate(fixedPrincipal(), memberResolver(null))

        assertThrows<AuthorizationDeniedException> {
            gate.requireActiveMember("workspace-2")
        }
    }

    @Test
    fun `should deny access when membership is suspended`() = runTest {
        val gate = WorkspaceMembershipGate(fixedPrincipal(), memberResolver(WorkspaceMembershipStatus.SUSPENDED))

        assertThrows<AuthorizationDeniedException> {
            gate.requireActiveMember("workspace-1")
        }
    }

    @Test
    fun `should deny access when membership is removed`() = runTest {
        val gate = WorkspaceMembershipGate(fixedPrincipal(), memberResolver(WorkspaceMembershipStatus.REMOVED))

        assertThrows<AuthorizationDeniedException> {
            gate.requireActiveMember("workspace-1")
        }
    }

    private fun fixedPrincipal(): PrincipalContextProvider = object : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principal()
    }

    private fun principal(): PrincipalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "local:owner@example.com",
    )

    private fun memberResolver(status: WorkspaceMembershipStatus?): WorkspaceMembershipResolver =
        WorkspaceMembershipResolver { _, resource ->
            status?.let {
                object : WorkspaceMembershipSnapshot {
                    override val id: String = "membership-1"
                    override val workspaceId: String = resource.workspaceId.orEmpty()
                    override val principalId: String = "principal-1"
                    override val principalType: PrincipalType = PrincipalType.USER
                    override val status: WorkspaceMembershipStatus = it
                    override val roleKeys: Set<String> = emptySet()
                }
            }
        }
}
