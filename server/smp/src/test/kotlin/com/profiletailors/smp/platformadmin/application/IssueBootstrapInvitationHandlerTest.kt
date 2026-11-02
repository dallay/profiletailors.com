package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.ActiveBootstrapInvitationException
import com.profiletailors.smp.platformadmin.domain.AdminActorKind
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationStatus
import com.profiletailors.smp.platformadmin.domain.BootstrapToken
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenGenerator
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.ExistingBootstrapAccountException
import com.profiletailors.smp.platformadmin.domain.PlatformOwnerAlreadyExistsException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class IssueBootstrapInvitationHandlerTest {
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val repository = FakeBootstrapInvitationRepository()
    private val audit = RecordingAuditPublisher()
    private val identities = FixedPrincipalIdentityLookup(null)
    private val handler = IssueBootstrapInvitationHandler(
        repository = repository,
        identityLookup = identities,
        auditPublisher = audit,
        transactionRunner = AtomicTransactionRunner.noop,
        tokenGenerator = BootstrapTokenGenerator { BootstrapToken("raw-one-time-token", "sha256-token-hash") },
        clock = Clock.fixed(now, ZoneOffset.UTC),
        adminPublicOrigin = "https://admin.example.test",
    )

    @Test
    fun `issues normalized hash-only invitation with one terminal completion link`() = runTest {
        val result = handler.handle(
            IssueBootstrapInvitationCommand(" Owner@Example.Test ", "owner@example.test", false),
        )

        assertEquals("owner@example.test", repository.current?.email)
        assertEquals("sha256-token-hash", repository.current?.tokenHash)
        assertEquals(now.plusSeconds(1800), repository.current?.expiresAt)
        assertEquals("https://admin.example.test/bootstrap#raw-one-time-token", result.link)
        assertEquals(1, audit.events.size)
        assertEquals(AdminActorKind.SYSTEM_BOOTSTRAP, audit.events.single().actorKind)
        assertEquals(AdminAuditAction.BOOTSTRAP_INVITATION_ISSUED, audit.events.single().action)
        assertFalse(audit.events.single().metadata.values.any { it.contains("token") || it.contains("sha256") })
    }

    @Test
    fun `requires explicit confirmation before replacing a pending invitation`() = runTest {
        repository.current = invitation()

        assertThrows(ActiveBootstrapInvitationException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", false))
            }
        }

        assertEquals("existing-hash", repository.current?.tokenHash)
        assertTrue(audit.events.isEmpty())
    }

    @Test
    fun `confirmed replacement invalidates the previous invitation atomically`() = runTest {
        repository.current = invitation()

        handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", true))

        assertEquals("sha256-token-hash", repository.current?.tokenHash)
        assertEquals(AdminAuditAction.BOOTSTRAP_INVITATION_REPLACED, audit.events.single().action)
    }

    @Test
    fun `claimed invitation cannot be reassigned even when replacement is confirmed`() = runTest {
        repository.current = invitation().copy(
            status = BootstrapInvitationStatus.CLAIMED,
            claimedPrincipalId = UUID.randomUUID(),
            claimedAt = now.minusSeconds(30),
        )

        assertThrows(BootstrapUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", true))
            }
        }

        assertEquals(BootstrapInvitationStatus.CLAIMED, repository.current?.status)
        assertEquals(0, audit.events.size)
    }

    @Test
    fun `existing account and owner prevent issuance without mutation`() = runTest {
        identities.facts = PrincipalIdentityFacts(
            principalId = UUID.randomUUID().toString(),
            principalType = PrincipalType.USER,
            subject = "existing-subject",
            provider = null,
            displayIdentity = "owner",
            email = "owner@example.test",
            username = "owner",
            emailStatus = EmailStatus.PENDING,
        )

        assertThrows(ExistingBootstrapAccountException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", false))
            }
        }
        assertEquals(null, repository.current)
        assertTrue(audit.events.isEmpty())

        identities.facts = null
        repository.ownerExists = true
        assertThrows(PlatformOwnerAlreadyExistsException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", false))
            }
        }
        assertEquals(null, repository.current)
        assertTrue(audit.events.isEmpty())
    }

    @Test
    fun `owner status errors fail closed`() = runTest {
        repository.ownerCheckFailure = IllegalStateException("owner status unavailable")

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(IssueBootstrapInvitationCommand("owner@example.test", "owner@example.test", false))
            }
        }

        assertEquals(null, repository.current)
        assertTrue(audit.events.isEmpty())
    }

    private fun invitation() = BootstrapInvitation(
        email = "owner@example.test",
        tokenHash = "existing-hash",
        expiresAt = now.plusSeconds(1800),
        createdAt = now,
    )

    private class FakeBootstrapInvitationRepository : BootstrapInvitationRepository {
        var current: BootstrapInvitation? = null
        var ownerExists = false
        var ownerCheckFailure: RuntimeException? = null

        override suspend fun lock() = Unit

        override suspend fun hasActiveOwner(): Boolean {
            ownerCheckFailure?.let { throw it }
            return ownerExists
        }

        override suspend fun findCurrent(): BootstrapInvitation? = current

        override suspend fun replace(invitation: BootstrapInvitation) {
            current = invitation
        }

        override suspend fun findByTokenHash(tokenHash: String): BootstrapInvitation? =
            current?.takeIf { it.tokenHash == tokenHash }

        override suspend fun claim(tokenHash: String, principalId: UUID, now: Instant): Boolean = false

        override suspend fun findClaimedByPrincipalId(principalId: UUID): BootstrapInvitation? = null

        override suspend fun complete(principalId: UUID, now: Instant): Boolean = false
    }

    private class FixedPrincipalIdentityLookup(var facts: PrincipalIdentityFacts?) : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = null

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = facts

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
    }

    private class RecordingAuditPublisher : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()

        override suspend fun publish(event: AdminAuditEvent) {
            events += event
        }
    }
}
