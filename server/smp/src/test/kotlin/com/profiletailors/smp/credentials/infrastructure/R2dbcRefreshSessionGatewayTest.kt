package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcRefreshSessionGatewayTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var gateway: RefreshSessionGateway

    @BeforeEach
    fun setUpGateway() {
        gateway = R2dbcRefreshSessionGateway(
            databaseClient = databaseClient,
            refreshTokenHasher = BCryptRefreshTokenHasher(),
        )
    }

    @Test
    fun `creates and resolves active refresh session`() = runTest {
        seedPrincipal()
        val token = RefreshSessionToken("lookup-1", "secret-value")
        gateway.create("user-1", token, Instant.parse("2026-05-30T10:15:30Z"))

        val active = gateway.requireActive(token, Instant.parse("2026-05-22T10:15:30Z"))

        assertEquals("user-1", active.principalId)
        assertEquals("lookup-1", active.lookupKey)
    }

    @Test
    fun `rotates refresh session and denies predecessor afterwards`() = runTest {
        seedPrincipal()
        val original = gateway.create(
            "user-1",
            RefreshSessionToken("lookup-1", "secret-value"),
            Instant.parse("2026-05-30T10:15:30Z"),
        )

        val replacement = gateway.rotate(
            currentSessionId = original.id,
            replacementToken = RefreshSessionToken("lookup-2", "new-secret"),
            expiresAt = Instant.parse("2026-05-31T10:15:30Z"),
            now = Instant.parse("2026-05-22T10:15:30Z"),
        )

        val replacementActive = gateway.requireActive(
            RefreshSessionToken("lookup-2", "new-secret"),
            Instant.parse("2026-05-22T10:16:30Z"),
        )
        assertEquals(replacement.id, replacementActive.id)

        val error = assertThrows(RefreshSessionNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                gateway.requireActive(
                    RefreshSessionToken("lookup-1", "secret-value"),
                    Instant.parse("2026-05-22T10:16:30Z"),
                )
            }
        }
        assertEquals(RefreshSessionFailureReason.ROTATED, error.reason)
    }

    @Test
    fun `revokes refresh session`() = runTest {
        seedPrincipal()
        val created = gateway.create(
            "user-1",
            RefreshSessionToken("lookup-1", "secret-value"),
            Instant.parse("2026-05-30T10:15:30Z"),
        )

        gateway.revoke(created.id, Instant.parse("2026-05-22T10:20:30Z"))

        val error = assertThrows(RefreshSessionNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                gateway.requireActive(
                    RefreshSessionToken("lookup-1", "secret-value"),
                    Instant.parse("2026-05-22T10:21:30Z"),
                )
            }
        }
        assertEquals(RefreshSessionFailureReason.REVOKED, error.reason)
    }

    private suspend fun seedPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-1', 'USER', 'local:user@example.com', NULL, 'user')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("refresh_session_gateway")
    }
}
