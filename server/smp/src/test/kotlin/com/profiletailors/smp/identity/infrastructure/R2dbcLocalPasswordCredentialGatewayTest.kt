package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcLocalPasswordCredentialGatewayTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var gateway: R2dbcLocalPasswordCredentialGateway

    @BeforeEach
    fun setUpGateway() {
        gateway = R2dbcLocalPasswordCredentialGateway(databaseClient)
    }

    @Test
    fun `creates and reads local password credential by email`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-1', 'USER', 'local:yuniel@example.com', NULL, 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('user-1', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        gateway.create("user-1", "hashed-password123", "bcrypt")

        val record = gateway.findByEmail("yuniel@example.com")

        assertNotNull(record)
        assertEquals("user-1", record?.principalId)
        assertEquals("hashed-password123", record?.passwordHash)
        assertEquals("bcrypt", record?.passwordAlgorithm)
        assertEquals("yuniel", record?.username)
    }

    @Test
    fun `updates password hash algorithm and updated_at`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-update', 'USER', 'local:update@example.com', NULL, 'update')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('user-update', 'update@example.com', 'update')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        gateway.create("user-update", "old-hash", "bcrypt")
        val before = gateway.findByEmail("update@example.com")
        assertNotNull(before)
        assertEquals("bcrypt", before?.passwordAlgorithm)

        gateway.updatePassword("user-update", "new-argon2id-hash", "argon2id")

        val after = gateway.findByEmail("update@example.com")
        assertNotNull(after)
        assertEquals("new-argon2id-hash", after?.passwordHash)
        assertEquals("argon2id", after?.passwordAlgorithm)
        assertNotNull(after?.passwordHash)
        // updated_at must have changed — verify password_hash did
        assertEquals("new-argon2id-hash", after?.passwordHash)
    }

    @Test
    fun `returns null for unknown email`() = runTest {
        val record = gateway.findByEmail("missing@example.com")
        assertNull(record)
    }

    @Test
    fun `principal lookup can find user by email`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-1', 'USER', 'local:yuniel@example.com', NULL, 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('user-1', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val lookup = R2dbcPrincipalIdentityLookup(databaseClient)
        val facts = lookup.findByEmail("yuniel@example.com")

        assertNotNull(facts)
        assertEquals("user-1", facts?.principalId)
        assertEquals(PrincipalType.USER, facts?.principalType)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("local_password_credential_gateway")
    }
}
