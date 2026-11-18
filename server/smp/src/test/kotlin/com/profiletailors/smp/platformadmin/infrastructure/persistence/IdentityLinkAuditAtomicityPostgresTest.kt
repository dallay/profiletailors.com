package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.application.ExternalIdentityLinkProvisioningService
import com.profiletailors.smp.identity.infrastructure.R2dbcExternalIdentityLinkGateway
import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcAtomicTransactionRunner
import com.profiletailors.smp.platformadmin.application.ProvisionExternalIdentityLinkCommand
import com.profiletailors.smp.platformadmin.application.ProvisionExternalIdentityLinkHandler
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class IdentityLinkAuditAtomicityPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer
    private val linkGateway by lazy { R2dbcExternalIdentityLinkGateway(databaseClient) }
    private val auditRepository by lazy { R2dbcAdminAuditRepository(databaseClient) }
    private val handler by lazy {
        ProvisionExternalIdentityLinkHandler(
            ExternalIdentityLinkProvisioningService(
                linkGateway,
                R2dbcPrincipalIdentityLookup(databaseClient),
                ISSUER,
            ),
            auditRepository,
            R2dbcAtomicTransactionRunner(transactionalOperator),
        )
    }

    @Test
    fun `success audit failure rolls back identity link insertion`() = runTest {
        val principalId = UUID.randomUUID()
        insertPrincipal(principalId)
        databaseClient.sql(
            """
            CREATE FUNCTION fail_success_identity_link_audit() RETURNS trigger AS $$
            BEGIN
                IF NEW.action = 'EXTERNAL_IDENTITY_LINK_PROVISIONED' AND NEW.result = 'SUCCEEDED' THEN
                    RAISE EXCEPTION 'success audit rejected';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            CREATE TRIGGER fail_success_identity_link_audit
            BEFORE INSERT ON platform_admin_audit_events
            FOR EACH ROW EXECUTE FUNCTION fail_success_identity_link_audit()
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val failure = runCatching {
            handler.handle(
                ProvisionExternalIdentityLinkCommand(
                    operatorPrincipalId = UUID.randomUUID(),
                    operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
                    targetPrincipalId = principalId.toString(),
                    issuer = ISSUER,
                    subject = SUBJECT,
                ),
            )
        }.exceptionOrNull()
        assertNotNull(failure)

        assertEquals(0L, linkCount())
    }

    private suspend fun insertPrincipal(id: UUID) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (:id, 'USER', :subject, NULL, 'Identity link audit test principal')
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("subject", "local-$id")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun linkCount(): Long = databaseClient.sql(
        "SELECT COUNT(*) FROM external_identity_links WHERE issuer = :issuer AND subject = :subject",
    )
        .bind("issuer", ISSUER)
        .bind("subject", SUBJECT)
        .map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
        .one()
        .awaitSingle()

    companion object {
        private const val ISSUER = "https://keycloak.example.test/realms/pt"
        private const val SUBJECT = "atomic-audit-subject"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("identity_link_audit_atomicity_test")
    }
}
