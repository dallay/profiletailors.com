package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.AssetUploader
import com.profiletailors.smp.publishing.domain.AssetUploadContext
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentials
import com.profiletailors.smp.publishing.infrastructure.scheduling.RetryablePublishingException
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.util.UUID

class LinkedInPublishingAdaptersTest {

    private val properties = LinkedInPublishingProperties(
        mode = "real",
        clientId = "client-id",
        clientSecret = "client-secret",
        redirectUri = "https://app.example.com/callback",
        scopes = "openid profile email w_member_social",
        apiBaseUrl = "https://api.linkedin.com",
        authorizationBaseUrl = "https://www.linkedin.com/oauth/v2/authorization",
        tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
        apiVersion = "202601",
    )
    private val objectMapper = ObjectMapper()

    @Test
    fun `real connection provider exchanges token and resolves profile`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000,"scope":"openid profile email w_member_social"}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"abcd1234","name":"Yuniel Acosta","email":"yuniel@example.com"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val provider = RealLinkedInConnectionProvider(properties, objectMapper, transport, credentialGateway)

        val result = provider.completeConnection(
            CompleteProviderConnectionCommand(
                workspaceId = "workspace-1",
                actorPrincipalId = "principal-1",
                authorizationCode = "auth-code-1",
                redirectUri = "https://app.example.com/callback",
            ),
        )

        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertEquals("linkedin-member-abcd1234", result.providerConnectionRef)
        assertEquals("abcd1234", result.account.providerAccountId)
        assertEquals("urn:li:person:abcd1234", result.account.profileUrn)
        // Verify credentials were stored with derived UUID
        val expectedUuid = UUID.nameUUIDFromBytes("linkedin:abcd1234".toByteArray())
        assertEquals(expectedUuid.toString(), result.credentialReference)
    }

    @Test
    fun `real connection provider maps token exchange failure`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    400,
                    emptyHeaders(),
                    "invalid_request",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val provider = RealLinkedInConnectionProvider(properties, objectMapper, transport, credentialGateway)

        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.completeConnection(
                    CompleteProviderConnectionCommand(
                        workspaceId = "workspace-1",
                        actorPrincipalId = "principal-1",
                        authorizationCode = "bad-code",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            }
        }

        assertEquals(true, error.message!!.contains("token exchange failed"))
    }

    @Test
    fun `real publisher builds article post and publishes with resolved token`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    201,
                    headersOf("x-restli-id" to "post-123"),
                    """{"id":"post-123"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, null))
        val assetUploader = FakeLinkedInAssetUploader()
        val storage = FakeStorage()
        val assetUploadProperties = LinkedInAssetUploadProperties("test-bucket")
        val publisher = RealLinkedInPublisher(
            properties,
            objectMapper,
            transport,
            credentialGateway,
            assetUploader,
            storage,
            assetUploadProperties.attachmentsBucket,
        )
        val account = SocialAccount(
            id = "account-1",
            socialConnectionId = "connection-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.LINKEDIN,
            providerAccountId = accountId,
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Yuniel",
            profileUrn = "urn:li:person:$accountId",
            status = SocialConnectionStatus.ACTIVE,
        )
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "account-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            bodyText = "Check this out https://example.com/article",
            title = "Article title",
        )

        val result = publisher.publish(
            ProviderPublishCommand(
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                socialAccount = account,
                publication = publication,
                assets = emptyList(),
            ),
        )

        assertEquals("post-123", result.externalPublicationId)
    }

    // ===== LinkedInCapabilityValidator Tests =====

    @Test
    fun `capability validator accepts supported media types`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val assets = listOf(
            testAsset("image/jpeg"),
            testAsset("image/png"),
            testAsset("image/gif"),
            testAsset("image/webp"),
            testAsset("video/mp4"),
        )

        assets.forEach { asset ->
            validator.validate(testValidationInput(account, listOf(asset)))
        }
        // If we get here without exception, the test passes
    }

    @Test
    fun `capability validator rejects unsupported media type`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val asset = testAsset("application/pdf")

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, listOf(asset)))
        }

        assertTrue(error.message!!.contains("Unsupported media type"))
        assertTrue(error.message!!.contains("application/pdf"))
    }

    @Test
    fun `capability validator rejects multiple unsupported media types`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val assets = listOf(
            testAsset("application/pdf"),
            testAsset("application/zip"),
        )

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, assets))
        }

        assertTrue(error.message!!.contains("application/pdf"))
        assertTrue(error.message!!.contains("application/zip"))
    }

    @Test
    fun `capability validator accepts empty assets list`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()

        validator.validate(testValidationInput(account, emptyList()))
        // If we get here without exception, the test passes
    }

    // ===== RealLinkedInAssetUploader Tests =====

    @Test
    fun `real linkedin asset uploader completes three step flow`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"asset":"urn:li:digitalmediaAsset:image:abc123","uploadUrl":"https://upload.linkedin.com/upload"}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"status":"SUCCESS"}""",
                ),
            ),
        )
        val storage = FakeStorage()
        val assetUploadProperties = LinkedInAssetUploadProperties("test-bucket")
        val assetRepository = FakePublicationAssetRepository()
        val uploader = RealLinkedInAssetUploader(properties, assetUploadProperties, objectMapper, transport, storage, assetRepository)
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val result = uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)

        assertEquals("urn:li:digitalmediaAsset:image:abc123", result.providerAssetId)
        assertEquals("image/jpeg", result.mediaType)
        assertNotNull(result)
    }

    @Test
    fun `real linkedin asset uploader throws on registration failure`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    400,
                    emptyHeaders(),
                    """{"message":"Bad request"}""",
                ),
            ),
        )
        val storage = FakeStorage()
        val assetUploadProperties = LinkedInAssetUploadProperties("test-bucket")
        val uploader = RealLinkedInAssetUploader(properties, assetUploadProperties, objectMapper, transport, storage, FakePublicationAssetRepository())
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val error = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)
            }
        }

        assertTrue(error.message!!.contains("asset registration failed"))
    }

    // ===== FakeLinkedInAssetUploader Tests =====

    @Test
    fun `fake linkedin asset uploader returns deterministic fake urn on success`() = runTest {
        val uploader = FakeLinkedInAssetUploader()
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val result = uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)

        assertTrue(result.providerAssetId.startsWith("urn:li:digitalmediaAsset:image:fake-asset-"))
        assertEquals("image/jpeg", result.mediaType)
        assertEquals(null, result.accessUrl)
    }

    @Test
    fun `fake linkedin asset uploader throws when configured to fail`() = runTest {
        val uploader = FakeLinkedInAssetUploader()
        uploader.failOnNextCall = true
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val error = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)
            }
        }

        assertTrue(error.message!!.contains("Fake LinkedIn asset upload failure"))
    }

    @Test
    fun `fake linkedin asset uploader resets fail flag after throwing`() = runTest {
        val uploader = FakeLinkedInAssetUploader()
        uploader.failOnNextCall = true
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        // First call should fail
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)
            }
        }

        // Second call should succeed
        val result = uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)
        assertTrue(result.providerAssetId.startsWith("urn:li:digitalmediaAsset:"))
    }

    // ===== Helper methods =====

    private fun testSocialAccount(): SocialAccount = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "linkedin-account-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Test User",
        profileUrn = "urn:li:person:test123",
        status = SocialConnectionStatus.ACTIVE,
    )

    private fun testAsset(mediaType: String): PublicationAsset = PublicationAsset(
        id = "asset-1",
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = mediaType,
        storageKey = "assets/workspace-1/asset-1",
        status = PublicationAssetStatus.READY,
        createdByPrincipalId = "principal-1",
    )

    private fun testValidationInput(
        account: SocialAccount,
        assets: List<PublicationAsset>,
    ): com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput =
        com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput(
            provider = SocialProvider.LINKEDIN,
            socialAccount = account,
            publication = PublicationDraft(
                id = "pub-1",
                workspaceId = "workspace-1",
                authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = account.id,
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Test post",
            ),
            assets = assets,
        )

    private fun testAssetUploadContext(): AssetUploadContext = AssetUploadContext(
        socialAccount = testSocialAccount(),
        accessToken = "test-access-token",
        apiBaseUrl = "https://api.linkedin.com",
        apiVersion = "202601",
    )

    private class StubTransport(
        private val responses: List<LinkedInHttpResponse>,
    ) : LinkedInHttpTransport {
        private var index = 0
        override suspend fun send(request: java.net.http.HttpRequest): LinkedInHttpResponse =
            responses.getOrElse(index++) {
                throw RetryablePublishingException("No stub response configured")
            }
    }

    private class FakeCredentialGateway : LinkedInCredentialGateway {
        private val store = mutableMapOf<UUID, LinkedInCredentials>()

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials): UUID {
            store[ownerId] = credentials
            return ownerId
        }

        override suspend fun resolveCredential(id: UUID): LinkedInCredentials {
            return store[id] ?: throw IllegalStateException("No credentials found for $id")
        }

        fun store(id: UUID, credentials: LinkedInCredentials) {
            store[id] = credentials
        }
    }

    private class FakeStorage : Storage {
        override suspend fun upload(
            bucket: String,
            key: String,
            content: Flow<ByteArray>,
            metadata: Map<String, String>,
        ) {
            content.collect { /* no-op */ }
        }

        override fun download(bucket: String, key: String): Flow<ByteArray> = flowOf(ByteArray(0))

        override suspend fun delete(bucket: String, key: String) {}

        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()
    }

    private class FakePublicationAssetRepository : com.profiletailors.smp.publishing.domain.PublicationAssetRepository {
        private val items = linkedMapOf<String, com.profiletailors.smp.publishing.domain.PublicationAsset>()

        override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<com.profiletailors.smp.publishing.domain.PublicationAsset> =
            items.values.filter { it.workspaceId == workspaceId && it.id in assetIds }

        override suspend fun create(asset: com.profiletailors.smp.publishing.domain.PublicationAsset): com.profiletailors.smp.publishing.domain.PublicationAsset {
            items[asset.id] = asset
            return asset
        }

        override suspend fun updateStatus(assetId: String, status: com.profiletailors.smp.publishing.domain.PublicationAssetStatus) {
            items[assetId]?.let { items[assetId] = it.copy(status = status) }
        }

        override suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: com.profiletailors.smp.publishing.domain.ProviderAssetRef) {
            items[assetId]?.let { items[assetId] = it.copy(status = com.profiletailors.smp.publishing.domain.PublicationAssetStatus.READY, providerAssetRef = providerAssetRef) }
        }
    }

    private fun emptyHeaders(): HttpHeaders =
        HttpHeaders.of(emptyMap()) { _, _ -> true }

    private fun headersOf(vararg pairs: Pair<String, String>): HttpHeaders =
        HttpHeaders.of(pairs.groupBy({ it.first }, { it.second })) { _, _ -> true }
}