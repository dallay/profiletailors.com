package com.profiletailors.smp.identity.integration

import com.profiletailors.smp.identity.infrastructure.R2dbcIdentityRegistrationGateway
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers
@Tag("postgres")
class NativeUuidTokenPostgresTest : PostgresDatabaseTestBase() {
    @Container
    override val postgres = PostgresTestContainerSupport.newContainer("native_uuid_tokens")

    @Test
    fun `verification token can be created read and consumed with native UUID primary key`() = runTest {
        val gateway = R2dbcIdentityRegistrationGateway(databaseClient)
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")

        gateway.createEmailVerificationToken("person@example.test", "opaque-verification-token", expiresAt)

        val token = requireNotNull(gateway.verifyEmailToken("opaque-verification-token"))
        assertEquals("person@example.test", token.email)
        assertEquals(expiresAt, token.expiresAt)
        val id = databaseClient.sql("SELECT id FROM email_verification_tokens WHERE token_hash = :hash")
            .bind("hash", token.tokenHash)
            .map { row, _ -> requireNotNull(row.get("id", UUID::class.java)) }
            .one().awaitSingle()
        assertEquals(token, gateway.findActiveTokenByEmail(token.email))
        gateway.markTokenUsed(token.tokenHash, Instant.now())
        val usedAt = databaseClient.sql("SELECT used_at FROM email_verification_tokens WHERE id = :id")
            .bind("id", id)
            .map { row, _ -> requireNotNull(row.get("used_at", Instant::class.java)) }
            .one().awaitSingle()
        org.junit.jupiter.api.Assertions.assertNotNull(usedAt)
    }
}
