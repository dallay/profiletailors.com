package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkedInCredentialGatewayTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var gateway: R2dbcLinkedInCredentialGateway
    private lateinit var encryptionService: CredentialEncryptionService

    @BeforeEach
    fun setUp() {
        val properties = PublishingCredentialsProperties().apply {
            key = "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODkwMTI="
        }
        encryptionService = CredentialEncryptionService(properties)
        gateway = R2dbcLinkedInCredentialGateway(databaseClient, encryptionService)
    }

    @Nested
    inner class StoreForOwnerTests {

        @Test
        fun `storeForOwner inserts encrypted credential and returns UUID`() = runTest {
            val ownerId = UUID.randomUUID()
            val credentials = LinkedInCredentials(
                accessToken = "eyJhbGciOiJSUzI1NiJ9...",
                refreshToken = "refresh-token-xyz",
                expiresAtEpochSeconds = 1735689600L,
                scope = "openid profile email",
            )

            val returnedId = gateway.storeForOwner("WORKSPACE", ownerId, credentials)

            returnedId shouldNotBe null
            // Verify row exists in DB with encrypted payload
            val row = databaseClient.sql(
                "SELECT encrypted_payload, owner_type, owner_id FROM secure_credentials WHERE id = :id",
            )
                .bind("id", returnedId)
                .map { r, _ ->
                    Triple(
                        r.get("encrypted_payload", ByteArray::class.java),
                        r.get("owner_type", String::class.java),
                        r.get("owner_id", UUID::class.java),
                    )
                }
                .one()
                .awaitSingle()

            row.first shouldNotBe null
            row.second shouldBe "WORKSPACE"
            row.third shouldBe ownerId
        }

        @Test
        fun `storeForOwner stores different owner types`() = runTest {
            val credentials = LinkedInCredentials(
                accessToken = "token",
                refreshToken = null,
                expiresAtEpochSeconds = null,
                scope = null,
            )

            val idUser = gateway.storeForOwner("USER", UUID.randomUUID(), credentials)
            val idWorkspace = gateway.storeForOwner("WORKSPACE", UUID.randomUUID(), credentials)

            val userType: String = databaseClient.sql("SELECT owner_type FROM secure_credentials WHERE id = :id")
                .bind("id", idUser).map { r, _ -> r.get("owner_type", String::class.java)!! }.one().awaitSingle()
            val workspaceType: String = databaseClient.sql("SELECT owner_type FROM secure_credentials WHERE id = :id")
                .bind("id", idWorkspace).map { r, _ -> r.get("owner_type", String::class.java)!! }.one().awaitSingle()

            userType shouldBe "USER"
            workspaceType shouldBe "WORKSPACE"
        }

        @Test
        fun `storeForOwner updates existing owner credential instead of inserting duplicate row`() = runTest {
            val ownerId = UUID.randomUUID()
            val firstId = gateway.storeForOwner(
                "USER",
                ownerId,
                LinkedInCredentials(
                    accessToken = "token-1",
                    refreshToken = "refresh-1",
                    expiresAtEpochSeconds = 100L,
                    scope = "openid",
                ),
            )

            val secondId = gateway.storeForOwner(
                "USER",
                ownerId,
                LinkedInCredentials(
                    accessToken = "token-2",
                    refreshToken = "refresh-2",
                    expiresAtEpochSeconds = 200L,
                    scope = "openid profile",
                ),
            )

            val count = databaseClient.sql(
                """
                SELECT COUNT(*) AS credential_count
                FROM secure_credentials
                WHERE owner_type = :ownerType
                  AND owner_id = :ownerId
                """.trimIndent(),
            )
                .bind("ownerType", "USER")
                .bind("ownerId", ownerId)
                .map { row, _ -> (row.get("credential_count") as Number).toLong() }
                .one()
                .awaitSingle()

            val resolved = gateway.resolveCredential(secondId)

            secondId shouldBe firstId
            count shouldBe 1L
            resolved.accessToken shouldBe "token-2"
            resolved.refreshToken shouldBe "refresh-2"
            resolved.expiresAtEpochSeconds shouldBe 200L
        }
    }

    @Nested
    inner class ResolveCredentialTests {

        @Test
        fun `resolveCredential roundtrips all credential fields`() = runTest {
            val ownerId = UUID.randomUUID()
            val credentials = LinkedInCredentials(
                accessToken = "access-token-abc123",
                refreshToken = "refresh-token-def456",
                expiresAtEpochSeconds = 1735689600L,
                scope = "openid profile email w_member_social",
            )

            val id = gateway.storeForOwner("WORKSPACE", ownerId, credentials)
            val resolved = gateway.resolveCredential(id)

            resolved.accessToken shouldBe "access-token-abc123"
            resolved.refreshToken shouldBe "refresh-token-def456"
            resolved.expiresAtEpochSeconds shouldBe 1735689600L
            resolved.scope shouldBe "openid profile email w_member_social"
        }

        @Test
        fun `resolveCredential with null optional fields`() = runTest {
            val ownerId = UUID.randomUUID()
            val credentials = LinkedInCredentials(
                accessToken = "minimal-token",
                refreshToken = null,
                expiresAtEpochSeconds = null,
                scope = null,
            )

            val id = gateway.storeForOwner("WORKSPACE", ownerId, credentials)
            val resolved = gateway.resolveCredential(id)

            resolved.accessToken shouldBe "minimal-token"
            resolved.refreshToken shouldBe null
            resolved.expiresAtEpochSeconds shouldBe null
            resolved.scope shouldBe null
        }

        @Test
        fun `resolveCredential throws when not found`() = runTest {
            val nonExistentId = UUID.randomUUID()

            shouldThrow<Exception> {
                gateway.resolveCredential(nonExistentId)
            }
        }
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("linkedin_credential_gateway")
    }
}

class CredentialEncryptionServiceKeyValidationTest {

    @Test
    fun `init throws IllegalArgumentException for key of invalid size`() {
        val properties = PublishingCredentialsProperties()
        // 8 bytes = 64 bits — not a valid AES key size (must be 128/192/256)
        properties.key = "dGVzdGtleTg=" // 8 bytes base64

        val exception = shouldThrow<IllegalArgumentException> {
            CredentialEncryptionService(properties)
        }
        exception.message!! shouldContain "Encryption key must be"
    }
}
