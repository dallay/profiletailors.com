package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class R2dbcPrincipalIdentityLookupTest : DatabaseUnitTestBase() {

    override fun databaseName() = "identity_lookup"

    private lateinit var lookup: PrincipalIdentityLookup

    @BeforeEach
    fun setUpLookup() {
        lookup = R2dbcPrincipalIdentityLookup(databaseClient)
    }

    @Test
    fun `loads principal plus user identity facts by subject and provider`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('principal-1', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "subject-123",
            provider = "https://issuer.example",
        )

        requireNotNull(facts)
        assertEquals("principal-1", facts.principalId)
        assertEquals(PrincipalType.USER, facts.principalType)
        assertEquals("yuniel@example.com", facts.email)
        assertEquals("yuniel", facts.username)
        assertEquals("yuniel", facts.displayIdentity)
    }

    @Test
    fun `loads service-account principal facts without requiring user identity row`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES (
                'service-principal-1', 'SERVICE_ACCOUNT',
                'service-account-subject', 'https://issuer.example', 'scheduler-bot'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = lookup.findBySubject(
            principalType = PrincipalType.SERVICE_ACCOUNT,
            subject = "service-account-subject",
            provider = "https://issuer.example",
        )

        requireNotNull(facts)
        assertEquals("service-principal-1", facts.principalId)
        assertEquals(PrincipalType.SERVICE_ACCOUNT, facts.principalType)
        assertEquals("scheduler-bot", facts.displayIdentity)
        assertNull(facts.email)
        assertNull(facts.username)
    }

    @Test
    fun `loads api key principal facts without requiring user identity row`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = lookup.findBySubject(
            principalType = PrincipalType.API_KEY,
            subject = "api-key-subject",
            provider = null,
        )

        requireNotNull(facts)
        assertEquals("api-key-principal-1", facts.principalId)
        assertEquals(PrincipalType.API_KEY, facts.principalType)
        assertEquals("integration-key", facts.displayIdentity)
        assertNull(facts.email)
        assertNull(facts.username)
    }

    @Test
    fun `loads principal facts by email`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-2', 'USER', 'local:yuniel@example.com', NULL, 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('principal-2', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = lookup.findByEmail("yuniel@example.com")

        requireNotNull(facts)
        assertEquals("principal-2", facts.principalId)
        assertEquals(PrincipalType.USER, facts.principalType)
        assertEquals("yuniel@example.com", facts.email)
    }

    @Test
    fun `returns null when no principal facts exist for subject`() = runTest {
        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "missing-subject",
            provider = "https://issuer.example",
        )

        assertNull(facts)
    }
}
