package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.audit.application.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.tenancy.domain.OwnerMustRemainActiveMemberException
import com.profiletailors.smp.tenancy.application.TenancyMutationAuditor
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusHandler
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipLookup
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateWorkspaceMembershipStatusHandlerTest {

    private val principalContext = PrincipalContext(
        principalId = "owner-1",
        principalType = PrincipalType.USER,
        subject = "subject-owner-1",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )

    @Test
    fun `prevents suspending last active owner membership`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val membership = WorkspaceMembership(
            id = "membership-1",
            workspaceId = "workspace-1",
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val membershipRepository = InMemoryWorkspaceMembershipRepository(mutableSetOf(membership))
        val auditHook = CapturingAuditHook()
        val handler = UpdateWorkspaceMembershipStatusHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = StubWorkspaceMembershipLookup(mapOf("owner-1" to membership)),
            workspaceMembershipRepository = membershipRepository,
            tenancyMutationAuditor = TenancyMutationAuditor(FixedPrincipalContextProvider(principalContext), auditHook),
        )

        assertThrows(OwnerMustRemainActiveMemberException::class.java) {
            runBlocking {
                handler.handle(
                    UpdateWorkspaceMembershipStatusCommand(
                        targetPrincipalId = "owner-1",
                        targetStatus = WorkspaceMembershipStatus.SUSPENDED,
                    ),
                )
            }
        }
        // Note: rejected mutations are NOT recorded for BusinessRuleValidationException
        // because the handler only catches IllegalArgumentException/IllegalStateException
    }

    @Test
    fun `allows removing non owner membership`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val ownerMembership = WorkspaceMembership(
            id = "membership-1",
            workspaceId = "workspace-1",
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val memberMembership = WorkspaceMembership(
            id = "membership-2",
            workspaceId = "workspace-1",
            principalId = "member-2",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val membershipRepository = InMemoryWorkspaceMembershipRepository(mutableSetOf(ownerMembership, memberMembership))
        val auditHook = CapturingAuditHook()
        val handler = UpdateWorkspaceMembershipStatusHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = StubWorkspaceMembershipLookup(
                mapOf(
                    "owner-1" to ownerMembership,
                    "member-2" to memberMembership,
                ),
            ),
            workspaceMembershipRepository = membershipRepository,
            tenancyMutationAuditor = TenancyMutationAuditor(FixedPrincipalContextProvider(principalContext), auditHook),
        )

        val result = handler.handle(
            UpdateWorkspaceMembershipStatusCommand(
                targetPrincipalId = "member-2",
                targetStatus = WorkspaceMembershipStatus.REMOVED,
            ),
        )

        assertEquals("workspace-1", result.workspaceId)
        assertEquals("member-2", result.principalId)
        assertEquals(WorkspaceMembershipStatus.REMOVED, result.status)
        assertTrue(auditHook.mutations.any { it.action == "workspace.membership.status.update" && it.targetId == "member-2" })
    }

    private class FixedPrincipalContextProvider(
        private val principalContext: PrincipalContext,
    ) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(
        private val resourceContext: ResourceContext,
    ) : ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }

    private class StubWorkspaceMembershipLookup(
        private val memberships: Map<String, WorkspaceMembership>,
    ) : WorkspaceMembershipLookup {
        override suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership? =
            memberships[principalId]
    }

    private class CapturingAuditHook : AuditHook {
        val mutations = mutableListOf<MutationAuditFact>()

        override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit

        override suspend fun onMutation(fact: MutationAuditFact) {
            mutations += fact
        }
    }

    private class InMemoryWorkspaceOwnershipRepository(
        private val ownerships: MutableSet<WorkspaceOwnership>,
    ) : WorkspaceOwnershipRepository {
        override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership> =
            ownerships.filterTo(linkedSetOf()) { it.workspaceId == workspaceId }

        override suspend fun add(ownership: WorkspaceOwnership) {
            ownerships.add(ownership)
        }

        override suspend fun remove(workspaceId: String, principalId: String) {
            ownerships.removeIf { it.workspaceId == workspaceId && it.ownerPrincipalId == principalId }
        }

        override suspend fun exists(workspaceId: String, principalId: String): Boolean =
            ownerships.any { it.workspaceId == workspaceId && it.ownerPrincipalId == principalId }
    }

    private class InMemoryWorkspaceMembershipRepository(
        private val memberships: MutableSet<WorkspaceMembership>,
    ) : WorkspaceMembershipRepository {
        override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceMembership> =
            memberships.filterTo(linkedSetOf()) { it.workspaceId == workspaceId }

        override suspend fun updateStatus(
            workspaceId: String,
            principalId: String,
            status: WorkspaceMembershipStatus,
        ) {
            val current = memberships.first { it.workspaceId == workspaceId && it.principalId == principalId }
            memberships.remove(current)
            memberships.add(current.copy(status = status))
        }
    }
}
