package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.tenancy.domain.LastOwnerRemovalRequiresReplacementException
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TenancyOwnershipHandlersInternalTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault())

    private val ownerPrincipal = PrincipalContext(
        principalId = "owner-1",
        principalType = PrincipalType.USER,
        subject = "subject-owner-1",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )

    // -------------------------------------------------------------------------
    // AddWorkspaceOwnerHandler tests
    // -------------------------------------------------------------------------

    @Test
    fun `AddWorkspaceOwnerHandler adds new owner and returns result with both owners`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val targetMembership = WorkspaceMembership(
            id = "membership-2",
            workspaceId = "workspace-1",
            principalId = "member-2",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
        val auditHook = CapturingAuditHook()

        val handler = AddWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val result = handler.handle(AddWorkspaceOwnerCommand(targetPrincipalId = "member-2"))

        assertEquals("workspace-1", result.workspaceId)
        assertTrue(result.ownerPrincipalIds.contains("owner-1"))
        assertTrue(result.ownerPrincipalIds.contains("member-2"))
        assertTrue(
            auditHook.mutations.any {
                it.action == "workspace.owner.add" && it.targetId == "member-2" && it.outcome.name == "SUCCESS"
            },
        )
    }

    @Test
    fun `AddWorkspaceOwnerHandler throws WorkspaceOwnerAccessDeniedException when actor is not an owner`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "other-owner",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val membershipLookup = StubWorkspaceMembershipLookup(emptyMap())
        val auditHook = CapturingAuditHook()

        val handler = AddWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val exception = runCatching {
            handler.handle(AddWorkspaceOwnerCommand(targetPrincipalId = "member-2"))
        }.exceptionOrNull()
        assertInstanceOf(WorkspaceOwnerAccessDeniedException::class.java, exception)
    }

    @Test
    fun `AddWorkspaceOwnerHandler throws OwnerTargetMustBeActiveMemberException when target is not active`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val targetMembership = WorkspaceMembership(
            id = "membership-2",
            workspaceId = "workspace-1",
            principalId = "member-2",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.SUSPENDED,
        )
        val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
        val auditHook = CapturingAuditHook()

        val handler = AddWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val exception = runCatching {
            handler.handle(AddWorkspaceOwnerCommand(targetPrincipalId = "member-2"))
        }.exceptionOrNull()
        assertInstanceOf(OwnerTargetMustBeActiveMemberException::class.java, exception)
    }

    @Test
    fun `AddWorkspaceOwnerHandler is idempotent when target is already an owner`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "member-2",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val targetMembership = WorkspaceMembership(
            id = "membership-2",
            workspaceId = "workspace-1",
            principalId = "member-2",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
        val auditHook = CapturingAuditHook()

        val handler = AddWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val result = handler.handle(AddWorkspaceOwnerCommand(targetPrincipalId = "member-2"))

        assertEquals(2, result.ownerPrincipalIds.size)
        assertTrue(result.ownerPrincipalIds.contains("owner-1"))
        assertTrue(result.ownerPrincipalIds.contains("member-2"))
    }

    // -------------------------------------------------------------------------
    // TransferWorkspaceOwnershipHandler tests
    // -------------------------------------------------------------------------

    @Test
    fun `TransferWorkspaceOwnershipHandler transfers ownership and removes old owner`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val targetMembership = WorkspaceMembership(
            id = "membership-2",
            workspaceId = "workspace-1",
            principalId = "member-2",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
        val auditHook = CapturingAuditHook()

        val handler = TransferWorkspaceOwnershipHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val result = handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "member-2"))

        assertEquals("workspace-1", result.workspaceId)
        assertTrue(result.ownerPrincipalIds.contains("member-2"))
        assertFalse(result.ownerPrincipalIds.contains("owner-1"))
        assertTrue(
            auditHook.mutations.any {
                it.action == "workspace.owner.transfer" && it.targetId == "member-2" && it.outcome.name == "SUCCESS"
            },
        )
    }

    @Test
    fun `TransferWorkspaceOwnershipHandler throws IllegalArgumentException when transferring to self`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val membershipLookup = StubWorkspaceMembershipLookup(emptyMap())
        val auditHook = CapturingAuditHook()

        val handler = TransferWorkspaceOwnershipHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            workspaceMembershipLookup = membershipLookup,
            clock = fixedClock,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val exception = runCatching {
            handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "owner-1"))
        }.exceptionOrNull()
        assertInstanceOf(IllegalArgumentException::class.java, exception)
        assertEquals("Cannot transfer ownership to yourself", (exception as IllegalArgumentException).message)
    }

    @Test
    fun `TransferWorkspaceOwnershipHandler throws WorkspaceOwnerAccessDeniedException when actor is not an owner`() =
        runTest {
            val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
                mutableSetOf(
                    WorkspaceOwnership(
                        workspaceId = "workspace-1",
                        ownerPrincipalId = "other-owner",
                        ownerPrincipalType = PrincipalType.USER,
                    ),
                ),
            )
            val membershipLookup = StubWorkspaceMembershipLookup(emptyMap())
            val auditHook = CapturingAuditHook()

            val handler = TransferWorkspaceOwnershipHandler(
                principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
                resourceContextProvider = FixedResourceContextProvider(workspaceContext),
                workspaceOwnershipRepository = ownershipRepository,
                workspaceMembershipLookup = membershipLookup,
                clock = fixedClock,
                tenancyMutationAuditor = TenancyMutationAuditor(
                    FixedPrincipalContextProvider(ownerPrincipal),
                    auditHook,
                ),
                transactionRunner = NoOpAtomicTransactionRunner(),
            )

            val exception = runCatching {
                handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "member-2"))
            }.exceptionOrNull()
            assertInstanceOf(WorkspaceOwnerAccessDeniedException::class.java, exception)
        }

    @Test
    fun `TransferWorkspaceOwnershipHandler throws OwnerTargetMustBeActiveMemberException when target is not active`() =
        runTest {
            val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
                mutableSetOf(
                    WorkspaceOwnership(
                        workspaceId = "workspace-1",
                        ownerPrincipalId = "owner-1",
                        ownerPrincipalType = PrincipalType.USER,
                    ),
                ),
            )
            val targetMembership = WorkspaceMembership(
                id = "membership-2",
                workspaceId = "workspace-1",
                principalId = "member-2",
                principalType = PrincipalType.USER,
                status = WorkspaceMembershipStatus.SUSPENDED,
            )
            val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
            val auditHook = CapturingAuditHook()

            val handler = TransferWorkspaceOwnershipHandler(
                principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
                resourceContextProvider = FixedResourceContextProvider(workspaceContext),
                workspaceOwnershipRepository = ownershipRepository,
                workspaceMembershipLookup = membershipLookup,
                clock = fixedClock,
                tenancyMutationAuditor = TenancyMutationAuditor(
                    FixedPrincipalContextProvider(ownerPrincipal),
                    auditHook,
                ),
                transactionRunner = NoOpAtomicTransactionRunner(),
            )

            val exception = runCatching {
                handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "member-2"))
            }.exceptionOrNull()
            assertInstanceOf(OwnerTargetMustBeActiveMemberException::class.java, exception)
        }

    @Test
    fun `TransferWorkspaceOwnershipHandler throws LastOwnerRemovalRequiresReplacementException concurrent transfer`() =
        runTest {
            // Simulates the TOCTOU scenario: by the time removeIfReplacementExists runs, the
            // replacement owner no longer exists (simulated via a repository that always refuses).
            val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
                ownerships = mutableSetOf(
                    WorkspaceOwnership(
                        workspaceId = "workspace-1",
                        ownerPrincipalId = "owner-1",
                        ownerPrincipalType = PrincipalType.USER,
                    ),
                ),
                removeIfReplacementAlwaysFails = true,
            )
            val targetMembership = WorkspaceMembership(
                id = "membership-2",
                workspaceId = "workspace-1",
                principalId = "member-2",
                principalType = PrincipalType.USER,
                status = WorkspaceMembershipStatus.ACTIVE,
            )
            val membershipLookup = StubWorkspaceMembershipLookup(mapOf("member-2" to targetMembership))
            val auditHook = CapturingAuditHook()

            val handler = TransferWorkspaceOwnershipHandler(
                principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
                resourceContextProvider = FixedResourceContextProvider(workspaceContext),
                workspaceOwnershipRepository = ownershipRepository,
                workspaceMembershipLookup = membershipLookup,
                clock = fixedClock,
                tenancyMutationAuditor = TenancyMutationAuditor(
                    FixedPrincipalContextProvider(ownerPrincipal),
                    auditHook,
                ),
                transactionRunner = NoOpAtomicTransactionRunner(),
            )

            val exception = runCatching {
                handler.handle(TransferWorkspaceOwnershipCommand(targetPrincipalId = "member-2"))
            }.exceptionOrNull()
            assertInstanceOf(LastOwnerRemovalRequiresReplacementException::class.java, exception)
            // Verify the business invariant: workspace must always have at least one owner.
            assertTrue(ownershipRepository.findByWorkspaceId("workspace-1").isNotEmpty())
        }

    // -------------------------------------------------------------------------
    // RemoveWorkspaceOwnerHandler tests
    // -------------------------------------------------------------------------

    @Test
    fun `RemoveWorkspaceOwnerHandler removes owner and returns remaining owner`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "owner-1",
                    ownerPrincipalType = PrincipalType.USER,
                ),
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "member-2",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val auditHook = CapturingAuditHook()

        val handler = RemoveWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val result = handler.handle(RemoveWorkspaceOwnerCommand(targetPrincipalId = "member-2"))

        assertEquals("workspace-1", result.workspaceId)
        assertEquals(1, result.ownerPrincipalIds.size)
        assertTrue(result.ownerPrincipalIds.contains("owner-1"))
        assertFalse(result.ownerPrincipalIds.contains("member-2"))
        assertTrue(
            auditHook.mutations.any {
                it.action == "workspace.owner.remove" && it.targetId == "member-2" && it.outcome.name == "SUCCESS"
            },
        )
    }

    @Test
    fun `RemoveWorkspaceOwnerHandler throws LastOwnerRemovalRequiresReplacementException when target is last owner`() =
        runTest {
            val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
                mutableSetOf(
                    WorkspaceOwnership(
                        workspaceId = "workspace-1",
                        ownerPrincipalId = "owner-1",
                        ownerPrincipalType = PrincipalType.USER,
                    ),
                ),
            )
            val auditHook = CapturingAuditHook()

            val handler = RemoveWorkspaceOwnerHandler(
                principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
                resourceContextProvider = FixedResourceContextProvider(workspaceContext),
                workspaceOwnershipRepository = ownershipRepository,
                tenancyMutationAuditor = TenancyMutationAuditor(
                    FixedPrincipalContextProvider(ownerPrincipal),
                    auditHook,
                ),
                transactionRunner = NoOpAtomicTransactionRunner(),
            )

            val exception = runCatching {
                handler.handle(RemoveWorkspaceOwnerCommand(targetPrincipalId = "owner-1"))
            }.exceptionOrNull()
            assertInstanceOf(LastOwnerRemovalRequiresReplacementException::class.java, exception)
        }

    @Test
    fun `RemoveWorkspaceOwnerHandler throws AccessDenied when actor is not owner`() = runTest {
        val ownershipRepository = InMemoryWorkspaceOwnershipRepository(
            mutableSetOf(
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "other-owner",
                    ownerPrincipalType = PrincipalType.USER,
                ),
                WorkspaceOwnership(
                    workspaceId = "workspace-1",
                    ownerPrincipalId = "member-2",
                    ownerPrincipalType = PrincipalType.USER,
                ),
            ),
        )
        val auditHook = CapturingAuditHook()

        val handler = RemoveWorkspaceOwnerHandler(
            principalContextProvider = FixedPrincipalContextProvider(ownerPrincipal),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            workspaceOwnershipRepository = ownershipRepository,
            tenancyMutationAuditor = TenancyMutationAuditor(
                FixedPrincipalContextProvider(ownerPrincipal),
                auditHook,
            ),
            transactionRunner = NoOpAtomicTransactionRunner(),
        )

        val exception = runCatching {
            handler.handle(RemoveWorkspaceOwnerCommand(targetPrincipalId = "member-2"))
        }.exceptionOrNull()
        assertInstanceOf(WorkspaceOwnerAccessDeniedException::class.java, exception)
    }

    // -------------------------------------------------------------------------
    // Helper classes — same pattern as UpdateWorkspaceMembershipStatusHandlerTest
    // -------------------------------------------------------------------------

    private class FixedPrincipalContextProvider(private val principalContext: PrincipalContext) :
        PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(private val resourceContext: ResourceContext) : ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }

    private class StubWorkspaceMembershipLookup(private val memberships: Map<String, WorkspaceMembership>) :
        WorkspaceMembershipLookup {
        override suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership? =
            memberships[principalId]
    }

    private class CapturingAuditHook :
        AuditHook,
        TenancyMutationAuditPort {
        val mutations = mutableListOf<MutationAuditFact>()

        override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit
        override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit
        override suspend fun onMutation(fact: MutationAuditFact) {
            mutations += fact
        }

        override suspend fun record(
            action: String,
            targetType: String,
            targetId: String,
            actorPrincipalId: String,
            workspaceId: String?,
            outcome: TenancyMutationAuditOutcome,
            details: Map<String, String>,
        ) {
            mutations += MutationAuditFact(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorPrincipalId = actorPrincipalId,
                workspaceId = workspaceId,
                outcome = if (outcome == TenancyMutationAuditOutcome.SUCCESS) {
                    MutationAuditOutcome.SUCCESS
                } else {
                    MutationAuditOutcome.REJECTED
                },
                details = details,
            )
        }
    }

    private class NoOpAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class InMemoryWorkspaceOwnershipRepository(
        private val ownerships: MutableSet<WorkspaceOwnership>,
        private val removeIfReplacementAlwaysFails: Boolean = false,
    ) : WorkspaceOwnershipRepository {
        override suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership> =
            ownerships.filterTo(linkedSetOf()) { it.workspaceId == workspaceId }

        override suspend fun add(ownership: WorkspaceOwnership) {
            ownerships.add(ownership)
        }

        override suspend fun remove(workspaceId: String, principalId: String) {
            ownerships.removeIf {
                it.workspaceId == workspaceId && it.ownerPrincipalId == principalId
            }
        }

        override suspend fun removeIfReplacementExists(workspaceId: String, principalId: String): Boolean {
            if (removeIfReplacementAlwaysFails) return false
            val hasOtherOwner = ownerships.any {
                it.workspaceId == workspaceId && it.ownerPrincipalId != principalId
            }
            if (!hasOtherOwner) return false
            return ownerships.removeIf {
                it.workspaceId == workspaceId && it.ownerPrincipalId == principalId
            }
        }

        override suspend fun exists(workspaceId: String, principalId: String): Boolean =
            ownerships.any { it.workspaceId == workspaceId && it.ownerPrincipalId == principalId }
    }
}
