package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderCredentialGatewayPostgresTest : PostgresDatabaseTestBase() {
    override val postgres = postgresContainer

    private lateinit var gateway: R2dbcProviderCredentialGateway
    private lateinit var linkedInGateway: R2dbcLinkedInCredentialGateway

    @BeforeEach
    fun setUpGateway() {
        val properties = PublishingCredentialsProperties().apply {
            key = "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODkwMTI="
        }
        val encryptionService = CredentialEncryptionService(properties)
        gateway = R2dbcProviderCredentialGateway(databaseClient, encryptionService)
        linkedInGateway = R2dbcLinkedInCredentialGateway(databaseClient, encryptionService)
    }

    @Test
    fun `stores provider credentials encrypted with refresh metadata`() = runTest {
        val ownerId = UUID.randomUUID()
        val credentials = ProviderCredentials(
            provider = SocialProvider.THREADS,
            accessToken = "threads-access-token",
            refreshToken = "threads-refresh-token",
            expiresAtEpochSeconds = 1_735_689_600L,
            refreshTokenExpiresAtEpochSeconds = 1_735_775_999L,
            lastRefreshAttemptAtEpochSeconds = 1_735_600_000L,
            lastRefreshStatus = "SUCCEEDED",
            grantedScopes = "threads_basic,threads_content_publish",
            scope = "threads_basic threads_content_publish",
        )

        val id = gateway.storeForOwner("WORKSPACE", ownerId, credentials)
        val row = databaseClient.sql(
            """
            SELECT provider, encrypted_payload, access_token_expires_at, refresh_token_expires_at,
                   last_refresh_attempt_at, last_refresh_status, granted_scopes
            FROM secure_credentials
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .map { row, _ ->
                listOf(
                    row.get("provider", String::class.java),
                    row.get("encrypted_payload", ByteArray::class.java),
                    row.get("access_token_expires_at", java.time.OffsetDateTime::class.java),
                    row.get("refresh_token_expires_at", java.time.OffsetDateTime::class.java),
                    row.get("last_refresh_attempt_at", java.time.OffsetDateTime::class.java),
                    row.get("last_refresh_status", String::class.java),
                    row.get("granted_scopes", String::class.java),
                )
            }
            .one()
            .awaitSingle()

        assertEquals("THREADS", row[0])
        assertNotNull(row[1])
        assertTrue(!(row[1] as ByteArray).decodeToString().contains("threads-access-token"))
        assertEquals(credentials.expiresAtEpochSeconds, (row[2] as java.time.OffsetDateTime).toEpochSecond())
        assertEquals(
            credentials.refreshTokenExpiresAtEpochSeconds,
            (row[3] as java.time.OffsetDateTime).toEpochSecond(),
        )
        assertEquals(
            credentials.lastRefreshAttemptAtEpochSeconds,
            (row[4] as java.time.OffsetDateTime).toEpochSecond(),
        )
        assertEquals("SUCCEEDED", row[5])
        assertEquals(credentials.grantedScopes, row[6])
        assertEquals(credentials, gateway.resolveCredential(id))
    }

    @Test
    fun `legacy LinkedIn gateway remains compatible after provider migration`() = runTest {
        val ownerId = UUID.randomUUID()
        val credentials = LinkedInCredentials(
            accessToken = "linkedin-access-token",
            refreshToken = "linkedin-refresh-token",
            expiresAtEpochSeconds = 1_735_689_600L,
            scope = "openid profile email",
        )

        val id = linkedInGateway.storeForOwner("linkedin:user", ownerId, credentials)

        assertEquals(credentials, linkedInGateway.resolveCredential(id))
        assertEquals(
            "LINKEDIN",
            databaseClient.sql("SELECT provider FROM secure_credentials WHERE id = :id")
                .bind("id", id)
                .map { row, _ -> row.get("provider", String::class.java) ?: error("Provider missing") }
                .one()
                .awaitSingle(),
        )
    }

    @Test
    fun `upserts atomically per owner and provider`() = runTest {
        val ownerId = UUID.randomUUID()
        val threads = ProviderCredentials(
            provider = SocialProvider.THREADS,
            accessToken = "threads-access-token",
            refreshToken = null,
            expiresAtEpochSeconds = 1_735_689_600L,
            refreshTokenExpiresAtEpochSeconds = null,
            lastRefreshAttemptAtEpochSeconds = null,
            lastRefreshStatus = null,
            grantedScopes = "threads_basic",
            scope = "threads_basic",
        )
        val refreshed = threads.copy(accessToken = "threads-refreshed-token", lastRefreshStatus = "SUCCEEDED")
        val linkedin = threads.copy(provider = SocialProvider.LINKEDIN, accessToken = "linkedin-access-token")

        val firstId = gateway.storeForOwner("WORKSPACE", ownerId, threads)
        val secondId = gateway.storeForOwner("WORKSPACE", ownerId, refreshed)
        val linkedinId = gateway.storeForOwner("WORKSPACE", ownerId, linkedin)
        val count = databaseClient.sql(
            "SELECT COUNT(*) AS count FROM secure_credentials WHERE owner_type = :ownerType AND owner_id = :ownerId",
        )
            .bind("ownerType", "WORKSPACE")
            .bind("ownerId", ownerId)
            .map { row, _ -> row.get("count", Long::class.java) ?: 0L }
            .one()
            .awaitSingle()

        assertEquals(firstId, secondId)
        assertNotEquals(firstId, linkedinId)
        assertEquals(2L, count)
        assertEquals(refreshed, gateway.resolveCredential(firstId))
        assertEquals(linkedin, gateway.resolveCredential(linkedinId))
    }

    @Test
    fun `should prevent resolution when a stored credential is invalidated`() = runTest {
        val id = gateway.storeForOwner(
            "threads:user",
            UUID.randomUUID(),
            ProviderCredentials(SocialProvider.THREADS, "access-token", null, null, scope = "threads_basic"),
        )
        assertEquals("access-token", gateway.resolveCredential(id).accessToken)

        gateway.invalidateCredential(id)

        shouldThrow<NoSuchElementException> { gateway.resolveCredential(id) }
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("provider_credentials")
    }
}
