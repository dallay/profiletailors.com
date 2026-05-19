package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.MutationAuditFact
import com.profiletailors.smp.platform.application.RequestOutcome
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import com.profiletailors.smp.tenancy.domain.LastOwnerRemovalRequiresReplacementException
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ManageWorkspaceOwnershipCommandsTest {

    private val principalContext = PrincipalContext(
        principalId = "owner-1",
        principalType = PrincipalType.USER,
        subject = "subject-owner-1",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC)

    @Test
    fun `add owner requires current owner and active membership for target`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val membershipLookup = StubWorkspaceMembershipLookup(
            memberships = mapOf(
                "owner-2" to WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "owner-2",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                ),
            ),
        )
        val auditHook = CapturingAuditHook()
        val handler = AddWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(FixedPrincipalContextProvider(principalContext), auditHook),
        )

        val result = handler.handle(AddWorkspaceOwnerCommand(targetPrincipalId = "owner-2"))

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(listOf("owner-1", "owner-2"), result.ownerPrincipalIds)
        val addedOwner = ownershipRepository.findByWorkspaceId("workspace-1").first { it.ownerPrincipalId == "owner-2" }
        assertEquals("owner-1", addedOwner.createdBy)
        assertEquals(fixedClock.instant(), addedOwner.createdAt)
        assertTrue(auditHook.mutations.any { it.action == "workspace.owner.add" && it.targetId == "owner-2" })
    }

    @Test
    fun `remove owner blocks removal of last owner`() = runTest {
        val soleOwner = WorkspaceOwnership(
            workspaceId = "workspace-1",
            ownerPrincipalId = "owner-1",
            ownerPrincipalType = PrincipalType.USER,
        )
        val auditHook = CapturingAuditHook()
        val handler = RemoveWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = InMemoryWorkspaceOwnershipRepository(mutableSetOf(soleOwner)),
            tenancyMutationAuditor = TenancyMutationAuditor(FixedPrincipalContextProvider(principalContext), auditHook),
        )

        assertThrows(LastOwnerRemovalRequiresReplacementException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(RemoveWorkspaceOwnerCommand(targetPrincipalId = "owner-1"))
            }
        }
        assertTrue(auditHook.mutations.any { it.action == "workspace.owner.remove" && it.targetId == "owner-1" })
    }

    @Test
    fun `transfer ownership adds successor and removes actor ownership`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-3",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val membershipLookup = StubWorkspaceMembershipLookup(
            memberships = mapOf(
                "owner-2" to WorkspaceMembership(
                    workspaceId = "workspace-1",
                    principalId = "owner-2",
                    principalType = PrincipalType.USER,
                    status = WorkspaceMembershipStatus.ACTIVE,
                ),
            ),
        )
        val auditHook = CapturingAuditHook()
        val handler = TransferWorkspaceOwnershipHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(FixedPrincipalContextProvider(principalContext), auditHook),
        )

        val result = handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "owner-2"))

        assertEquals(listOf("owner-2", "owner-3"), result.ownerPrincipalIds)
        assertTrue(auditHook.mutations.any { it.action == "workspace.owner.transfer" && it.targetId == "owner-2" })
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

        override suspend fun onAuthorizationDecision(fact: com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact) = Unit

        override suspend fun onMutation(fact: MutationAuditFact) {
            mutations += fact
        }
    }

    private class InMemoryWorkspaceOwnershipRepository(
        private val ownerships: MutableSet<WorkspaceOwnership>,
    ) : WorkspaceOwnershipRepository {
        override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership> =
            ownerships.filterTo(linkedSetOf()) { ownership -> ownership.workspaceId == workspaceId }

        override suspend fun add(ownership: WorkspaceOwnership) {
            ownerships.removeIf { current ->
                current.workspaceId == ownership.workspaceId && current.ownerPrincipalId == ownership.ownerPrincipalId
            }
            ownerships.add(ownership)
        }

        override suspend fun remove(workspaceId: String, principalId: String) {
            ownerships.removeIf { ownership ->
                ownership.workspaceId == workspaceId && ownership.ownerPrincipalId == principalId
            }
        }

        override suspend fun exists(workspaceId: String, principalId: String): Boolean =
            ownerships.any { ownership ->
                ownership.workspaceId == workspaceId && ownership.ownerPrincipalId == principalId
            }
    }
}
