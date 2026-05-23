package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class R2dbcLocalPasswordCredentialGatewayTest : DatabaseUnitTestBase() {

    override fun databaseName() = "local_password_gateway"

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

        gateway.create("user-1", "hashed-password123")

        val record = gateway.findByEmail("yuniel@example.com")

        assertNotNull(record)
        assertEquals("user-1", record?.principalId)
        assertEquals("hashed-password123", record?.passwordHash)
        assertEquals("yuniel", record?.username)
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
}
