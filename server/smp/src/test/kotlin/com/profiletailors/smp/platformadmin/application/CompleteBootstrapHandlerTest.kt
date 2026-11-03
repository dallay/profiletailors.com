package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.governance.application.RecordBootstrapTermsConsentCommand
import com.profiletailors.smp.governance.application.RecordBootstrapTermsConsentHandler
import com.profiletailors.smp.identity.application.CurrentTermsVersionProvider
import com.profiletailors.smp.identity.application.EmailVerificationTokenData
import com.profiletailors.smp.identity.application.IdentityRegistrationGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialGateway
import com.profiletailors.smp.identity.application.LocalPasswordCredentialRecord
import com.profiletailors.smp.identity.application.PasswordHasher
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitation
import com.profiletailors.smp.platformadmin.domain.BootstrapInvitationRepository
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import com.profiletailors.smp.platformadmin.domain.StaleBootstrapTermsException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CompleteBootstrapHandlerTest {
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val rawToken = "one-time-bootstrap-token"
    private val tokenHash = sha256(rawToken)
    private val invitation = BootstrapInvitation(
        email = "owner@example.test",
        tokenHash = tokenHash,
        expiresAt = now.plusSeconds(1800),
        createdAt = now.minusSeconds(60),
    )
    private val order = mutableListOf<String>()
    private val repository = FakeBootstrapInvitationRepository(invitation, order)
    private val identityGateway = FakeIdentityRegistrationGateway(order)
    private val credentialGateway = FakePasswordCredentialGateway()
    private val identityLookup = FixedPrincipalIdentityLookup(null)
    private val eventPublisher = RecordingEventPublisher(order)
    private val transactionRunner = RecordingTransactionRunner(order)
    private val consentSlot = io.mockk.slot<RecordBootstrapTermsConsentCommand>()
    private val consentHandler = mockk<RecordBootstrapTermsConsentHandler>()
    private val handler = CompleteBootstrapHandler(
        invitationRepository = repository,
        identityRegistrationGateway = identityGateway,
        localPasswordCredentialGateway = credentialGateway,
        principalIdentityLookup = identityLookup,
        passwordHasher = object : PasswordHasher {
            override val algorithm: String = "test"
            override fun hash(rawPassword: String): String = "hashed-password"
            override fun matches(rawPassword: String, passwordHash: String): Boolean = false
        },
        recordBootstrapTermsConsentHandler = consentHandler,
        currentTermsVersionProvider = CurrentTermsVersionProvider { "terms-v1.0.0" },
        eventPublisher = eventPublisher,
        transactionRunner = transactionRunner,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `sets password records platform terms and sends existing verification event`() = runTest {
        coEvery { consentHandler.handle(capture(consentSlot)) } returns Unit

        handler.handle(CompleteBootstrapCommand(rawToken, "bootstrap-test-password", "terms-v1.0.0"))

        assertTrue(repository.claimed)
        assertEquals(
            listOf("identity:create", "invitation:claim"),
            order.filter { it == "identity:create" || it == "invitation:claim" },
        )
        assertEquals("owner@example.test", identityGateway.createdEmail)
        assertEquals(EmailStatus.PENDING, identityGateway.createdEmailStatus)
        assertEquals("hashed-password", credentialGateway.createdHash)
        assertEquals(
            UUID.fromString(requireNotNull(identityGateway.createdPrincipalId)),
            consentSlot.captured.principalId,
        )
        assertEquals("terms-v1.0.0", consentSlot.captured.termsVersion)
        assertEquals(now, consentSlot.captured.occurredAt)
        assertEquals(1, eventPublisher.events.size)
        assertTrue(eventPublisher.events.single() is com.profiletailors.smp.identity.domain.UserRegistered)
        assertEquals(
            listOf("transaction:start", "identity:create", "invitation:claim", "transaction:commit", "event:publish"),
            order,
        )
    }

    @Test
    fun `stale terms version makes no mutation`() = runTest {
        coEvery { consentHandler.handle(any()) } returns Unit

        assertThrows(StaleBootstrapTermsException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(CompleteBootstrapCommand(rawToken, "bootstrap-test-password", "terms-v0.9"))
            }
        }

        assertFalse(repository.claimed)
        assertEquals(null, identityGateway.createdEmail)
        assertTrue(eventPublisher.events.isEmpty())
    }

    @Test
    fun `existing account cannot be elevated through bootstrap`() = runTest {
        coEvery { consentHandler.handle(any()) } returns Unit
        identityLookup.facts = identityFacts()

        assertThrows(BootstrapUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(CompleteBootstrapCommand(rawToken, "bootstrap-test-password", "terms-v1.0.0"))
            }
        }

        assertFalse(repository.claimed)
        assertEquals(null, credentialGateway.createdHash)
        assertTrue(eventPublisher.events.isEmpty())
    }

    private fun identityFacts() = PrincipalIdentityFacts(
        principalId = UUID.randomUUID().toString(),
        principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
        subject = "existing-subject",
        provider = null,
        displayIdentity = "owner",
        email = "owner@example.test",
        username = "owner",
    )

    private class FakeBootstrapInvitationRepository(
        private val invitation: BootstrapInvitation,
        private val order: MutableList<String>,
    ) : BootstrapInvitationRepository {
        var claimed = false

        override suspend fun lock() = Unit
        override suspend fun hasActiveOwner(): Boolean = false
        override suspend fun findCurrent(): BootstrapInvitation? = invitation
        override suspend fun replace(invitation: BootstrapInvitation) = Unit
        override suspend fun findByTokenHash(tokenHash: String): BootstrapInvitation? =
            invitation.takeIf { it.tokenHash == tokenHash }

        override suspend fun claim(tokenHash: String, principalId: UUID, now: Instant): Boolean {
            order.add("invitation:claim")
            claimed = invitation.tokenHash == tokenHash && invitation.canClaim(now)
            return claimed
        }

        override suspend fun findClaimedByPrincipalId(principalId: UUID): BootstrapInvitation? = null
        override suspend fun complete(principalId: UUID, now: Instant): Boolean = false
    }

    private class FakeIdentityRegistrationGateway(private val order: MutableList<String>) :
        IdentityRegistrationGateway {
        var createdPrincipalId: String? = null
        var createdEmail: String? = null
        var createdEmailStatus: EmailStatus? = null
        var verificationToken: EmailVerificationTokenData? = null

        override suspend fun createUserIdentity(
            principalId: String,
            subject: String,
            email: String,
            username: String,
            provider: String?,
            displayIdentity: String,
            emailStatus: EmailStatus,
        ) {
            order.add("identity:create")
            createdPrincipalId = principalId
            createdEmail = email
            createdEmailStatus = emailStatus
        }

        override suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant) {
            verificationToken = EmailVerificationTokenData(email, tokenHash, expiresAt, null)
        }

        override suspend fun verifyEmailToken(tokenHash: String): EmailVerificationTokenData? = null
        override suspend fun markTokenUsed(tokenHash: String, now: Instant): Boolean = false
        override suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus) = Unit
        override suspend fun invalidateEmailTokens(email: String) = Unit
        override suspend fun findActiveTokenByEmail(email: String): EmailVerificationTokenData? = null
    }

    private class FakePasswordCredentialGateway : LocalPasswordCredentialGateway {
        var createdHash: String? = null

        override suspend fun create(principalId: String, passwordHash: String) {
            createdHash = passwordHash
        }

        override suspend fun findByEmail(email: String): LocalPasswordCredentialRecord? = null
        override suspend fun findByPrincipalId(principalId: String): LocalPasswordCredentialRecord? = null
        override suspend fun updatePasswordHash(principalId: String, passwordHash: String) = Unit
    }

    private class FixedPrincipalIdentityLookup(var facts: PrincipalIdentityFacts?) : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: com.profiletailors.common.domain.context.PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = null

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = facts
        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
    }

    private class RecordingEventPublisher(private val order: MutableList<String>) : EventPublisher<DomainEvent> {
        val events = mutableListOf<DomainEvent>()

        override suspend fun publish(event: DomainEvent) {
            events += event
            order += "event:publish"
        }
    }

    private class RecordingTransactionRunner(private val order: MutableList<String>) : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            order += "transaction:start"
            val result = block()
            order += "transaction:commit"
            return result
        }
    }

    private companion object {
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
