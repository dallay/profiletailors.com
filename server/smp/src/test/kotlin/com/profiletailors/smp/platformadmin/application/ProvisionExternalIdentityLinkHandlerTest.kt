package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.ExternalIdentityLinkConflictException
import com.profiletailors.smp.identity.application.ExternalIdentityLinkGateway
import com.profiletailors.smp.identity.application.ExternalIdentityLinkProvisioningService
import com.profiletailors.smp.identity.application.ExternalIdentityLinkTargetNotFoundException
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.VerifiedEmailSyncResult
import com.profiletailors.smp.identity.domain.ExternalIdentityLink
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ProvisionExternalIdentityLinkHandlerTest {
    @Test
    fun `successful provisioning audits explicit identity and local principals without authentication artifacts`() =
        runTest {
            val fixture = Fixture()

            fixture.handler.handle(fixture.command)

            val event = fixture.audit.events.single()
            assertEquals(AdminAuditResult.SUCCEEDED, event.result)
            assertEquals(fixture.command.operatorPrincipalId, event.operatorPrincipalId)
            assertEquals(fixture.command.targetPrincipalId, event.targetId)
            assertEquals(mapOf("issuer" to ISSUER, "subject" to "external-subject"), event.metadata)
            assertEquals(Triple(ISSUER, "external-subject", fixture.command.targetPrincipalId), fixture.gatewayCreate)
            assertEquals(null, event.reason)
            assertEquals(
                setOf("issuer", "subject"),
                event.metadata.keys,
            )
        }

    @Test
    fun `missing users manage permission is rejected and audited`() = runTest {
        val fixture = Fixture()
        val command = fixture.command.copy(operatorRoles = emptySet())

        val exception = runCatching { fixture.handler.handle(command) }.exceptionOrNull()
        assertTrue(exception is com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException)

        val event = fixture.audit.events.single()
        assertEquals(AdminAuditResult.REJECTED, event.result)
        assertEquals("permission_denied", event.reason)
        assertEquals(0, fixture.gatewayCreates)
    }

    @Test
    fun `known conflict is rejected and rethrown`() = runTest {
        val conflict = ExternalIdentityLinkConflictException()
        val fixture = Fixture(gatewayFailure = conflict)

        val exception = runCatching { fixture.handler.handle(fixture.command) }.exceptionOrNull()
        assertSame(conflict, exception)
        assertEquals(
            listOf(AdminAuditResult.REJECTED to "identity_conflict"),
            fixture.audit.events.map { it.result to it.reason },
        )
    }

    @Test
    fun `missing target is rejected and rethrown`() = runTest {
        val fixture = Fixture(targetExists = false)

        val exception = runCatching { fixture.handler.handle(fixture.command) }.exceptionOrNull()
        assertTrue(exception is ExternalIdentityLinkTargetNotFoundException)
        assertEquals(AdminAuditResult.REJECTED, fixture.audit.events.single().result)
        assertEquals("target_not_found", fixture.audit.events.single().reason)
    }

    @Test
    fun `unexpected provisioning failure is audited as failed and rethrown`() = runTest {
        val failure = IllegalStateException("database unavailable")
        val fixture = Fixture(gatewayFailure = failure)

        val exception = runCatching { fixture.handler.handle(fixture.command) }.exceptionOrNull()
        assertSame(failure, exception)
        val event = fixture.audit.events.single()
        assertEquals(AdminAuditResult.FAILED, event.result)
        assertEquals("persistence_failure", event.reason)
        assertEquals(fixture.command.operatorPrincipalId, event.operatorPrincipalId)
        assertEquals(fixture.command.targetPrincipalId, event.targetId)
        assertEquals("PRINCIPAL", event.targetType)
        assertEquals("EXTERNAL_IDENTITY_LINK_PROVISIONED", event.action.name)
        assertEquals(mapOf("issuer" to ISSUER, "subject" to "external-subject"), event.metadata)
        assertTrue(event.reason !in AUTHENTICATION_ARTIFACTS)
        assertTrue(event.metadata.values.none { it in AUTHENTICATION_ARTIFACTS })
    }

    @Test
    fun `audit failure does not replace provisioning failure`() = runTest {
        val failure = IllegalStateException("database unavailable")
        val auditFailure = IllegalStateException("audit unavailable")
        val fixture = Fixture(gatewayFailure = failure, auditFailure = auditFailure)

        val exception = runCatching { fixture.handler.handle(fixture.command) }.exceptionOrNull()
        assertSame(failure, exception)
        assertEquals(auditFailure, failure.suppressed.single())
    }

    @Test
    fun `cancellation is not audited`() = runTest {
        val cancellation = CancellationException("cancelled")
        val fixture = Fixture(gatewayFailure = cancellation)

        val exception = runCatching { fixture.handler.handle(fixture.command) }.exceptionOrNull()
        assertSame(cancellation, exception)
        assertEquals(emptyList<AdminAuditEvent>(), fixture.audit.events)
    }

    private class Fixture(
        targetExists: Boolean = true,
        private val gatewayFailure: Throwable? = null,
        auditFailure: Throwable? = null,
    ) {
        val audit = RecordingAuditPublisher(auditFailure)
        var gatewayCreates = 0
            private set
        var gatewayCreate: Triple<String, String, String>? = null
            private set
        private val principalId = UUID.randomUUID()
        private val gateway = object : ExternalIdentityLinkGateway {
            override suspend fun findByIssuerAndSubject(issuer: String, subject: String): List<ExternalIdentityLink> =
                emptyList()

            override suspend fun create(issuer: String, subject: String, principalId: String) {
                gatewayCreates += 1
                gatewayCreate = Triple(issuer, subject, principalId)
                gatewayFailure?.let { throw it }
            }

            override suspend fun synchronizeVerifiedEmail(principalId: String, email: String): VerifiedEmailSyncResult =
                VerifiedEmailSyncResult.UNCHANGED
        }
        private val lookup = object : PrincipalIdentityLookup {
            override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

            override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = if (targetExists) {
                PrincipalIdentityFacts(
                    principalId = principalId,
                    principalType = PrincipalType.USER,
                    subject = "local-subject",
                    provider = null,
                    displayIdentity = "Test user",
                    email = null,
                    username = null,
                )
            } else {
                null
            }

            override suspend fun findBySubject(
                principalType: PrincipalType,
                subject: String,
                provider: String?,
            ): PrincipalIdentityFacts? = null
        }
        private val provisioningService = ExternalIdentityLinkProvisioningService(gateway, lookup, ISSUER)
        val handler = ProvisionExternalIdentityLinkHandler(
            provisioningService,
            audit,
            AtomicTransactionRunner.noop,
        )
        val command = ProvisionExternalIdentityLinkCommand(
            operatorPrincipalId = UUID.randomUUID(),
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            targetPrincipalId = principalId.toString(),
            issuer = ISSUER,
            subject = "external-subject",
        )
    }

    private class RecordingAuditPublisher(private val failure: Throwable?) : AdministrativeAuditPublisher {
        val events = mutableListOf<AdminAuditEvent>()

        override suspend fun publish(event: AdminAuditEvent) {
            events += event
            failure?.let { throw it }
        }
    }

    private companion object {
        const val ISSUER = "https://keycloak.example.test/realms/pt"
        val AUTHENTICATION_ARTIFACTS = setOf(
            "Bearer token",
            "session-id",
            "access-token",
            "refresh-token",
        )
    }
}
