package com.profiletailors.smp.platformadmin.application

import com.profiletailors.smp.identity.application.CurrentTermsVersionProvider
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapTermsConsentVerifier
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.AdminActorKind
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationStatus
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CompleteBootstrapOwnerGrantHandlerTest {
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val principalId = UUID.randomUUID()
    private val repository = ClaimedInvitationRepository(principalId)
    private val assignments = FakeRoleAssignmentRepository()
    private val audit = RecordingAuditPublisher()
    private val termsVerifier = FixedBootstrapTermsConsentVerifier(true)
    private val handler = CompleteBootstrapOwnerGrantHandler(
        invitationRepository = repository,
        roleAssignmentRepository = assignments,
        termsConsentVerifier = termsVerifier,
        currentTermsVersionProvider = CurrentTermsVersionProvider { "terms-v1.0.0" },
        auditPublisher = audit,
        clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
    )

    @Test
    fun `grants only verified bootstrap recipient with system actor and audit`() = runTest {
        handler.handle(principalId.toString(), "owner@example.test", now)

        val assignment = assignments.saved.single()
        assertEquals(PlatformRole.PLATFORM_OWNER, assignment.role)
        assertEquals(AdminActorKind.SYSTEM_BOOTSTRAP, assignment.actorKind)
        assertNull(assignment.assignedBy)
        assertEquals(BootstrapInvitationStatus.COMPLETED, repository.current?.status)
        assertEquals(AdminActorKind.SYSTEM_BOOTSTRAP, audit.events.single().actorKind)
        assertTrue(audit.events.single().operatorPlatformRoles.isEmpty())
        assertEquals("owner@example.test", audit.events.single().metadata["email"])
    }

    @Test
    fun `does not grant owner when current platform terms consent is absent`() = runTest {
        termsVerifier.hasConsent = false

        assertThrows(BootstrapUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(principalId.toString(), "owner@example.test", now)
            }
        }

        assertTrue(assignments.saved.isEmpty())
        assertEquals(BootstrapInvitationStatus.CLAIMED, repository.current?.status)
        assertTrue(audit.events.isEmpty())
    }

    private class ClaimedInvitationRepository(principalId: UUID) : BootstrapInvitationRepository {
        var current: BootstrapInvitation? = BootstrapInvitation(
            email = "owner@example.test",
            tokenHash = "hash-only",
            expiresAt = Instant.parse("2026-11-02T10:30:00Z"),
            createdAt = Instant.parse("2026-11-02T09:30:00Z"),
            status = BootstrapInvitationStatus.CLAIMED,
            claimedPrincipalId = principalId,
            claimedAt = Instant.parse("2026-11-02T09:45:00Z"),
        )

        override suspend fun lock() = Unit
        override suspend fun hasActiveOwner(): Boolean = false
        override suspend fun findCurrent(): BootstrapInvitation? = current
        override suspend fun replace(invitation: BootstrapInvitation) = Unit
        override suspend fun findByTokenHash(tokenHash: String): BootstrapInvitation? = null
        override suspend fun claim(tokenHash: String, principalId: UUID, now: Instant): Boolean = false
        override suspend fun findClaimedByPrincipalId(principalId: UUID): BootstrapInvitation? =
            current?.takeIf { it.claimedPrincipalId == principalId }

        override suspend fun complete(principalId: UUID, now: Instant): Boolean {
            current = current?.complete(principalId, now)
            return current?.status == BootstrapInvitationStatus.COMPLETED
        }
    }

    private class FakeRoleAssignmentRepository : PlatformRoleAssignmentRepository {
        val saved = mutableListOf<PlatformRoleAssignment>()
        override suspend fun findById(id: PlatformRoleAssignmentId): PlatformRoleAssignment? = null
        override suspend fun findActiveByPrincipalId(principalId: UUID): List<PlatformRoleAssignment> = emptyList()
        override suspend fun findAllActive(): List<PlatformRoleAssignment> = saved
        override suspend fun save(assignment: PlatformRoleAssignment): PlatformRoleAssignment {
            saved += assignment
            return assignment
        }

        override suspend fun update(assignment: PlatformRoleAssignment): PlatformRoleAssignment = assignment
    }

    private class FixedBootstrapTermsConsentVerifier(var hasConsent: Boolean) : BootstrapTermsConsentVerifier {
        override suspend fun hasCurrentTermsConsent(principalId: UUID, termsVersion: String): Boolean = hasConsent
    }

    private class RecordingAuditPublisher : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()
        override suspend fun publish(event: AdminAuditEvent) {
            events += event
        }
    }
}
