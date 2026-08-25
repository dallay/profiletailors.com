package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.AssetUploadContext
import com.profiletailors.smp.publishing.domain.AssetUploader
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
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
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentials
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingFailureCategory
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingFailureException
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.infrastructure.AttachmentsStorageBindingFactory
import com.profiletailors.storage.infrastructure.ProviderConfig
import com.profiletailors.storage.infrastructure.StorageProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.util.UUID

class LinkedInPublishingWiringTest {

    private val properties = LinkedInPublishingProperties(
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
                    """{"access_token":"access-123","expires_in":5184000,"scope":""" +
                        """"openid profile email w_member_social"}""",
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
    fun `real connection provider maps profile lookup failure`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000}""",
                ),
                LinkedInHttpResponse(
                    401,
                    emptyHeaders(),
                    "Unauthorized",
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
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            }
        }

        assertEquals(true, error.message!!.contains("profile lookup failed"))
    }

    @Test
    fun `real connection provider throws when profile missing sub`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"name":"No Sub User"}""",
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
                        authorizationCode = "auth-code-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            }
        }

        assertEquals(true, error.message!!.contains("did not include subject id"))
    }

    @Test
    fun `real publisher escapes little text reserved characters in multiline commentary`() = runTest {
        val transport = RecordingTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    201,
                    headersOf("x-restli-id" to "post-little-text"),
                    """{"id":"post-little-text"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
        )
        val account = testSocialAccount().copy(providerAccountId = accountId)
        val body = """Spring Boot 4.x (el cambio más grande)

La migración exige cuidado con (paréntesis), [corchetes] y {llaves}."""

        val result = publisher.publish(
            ProviderPublishCommand(
                publicationId = "pub-little-text",
                workspaceId = "workspace-1",
                socialAccount = account,
                publication = testPublication(bodyText = body),
                assets = emptyList(),
            ),
        )

        assertNull(result.providerMessage)
        val payload = transport.capturedBodies.single()
        assertEquals(
            """Spring Boot 4.x \(el cambio más grande\)

La migración exige cuidado con \(paréntesis\), \[corchetes\] y \{llaves\}.""",
            payload["commentary"].asText(),
        )
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
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val assetUploader = FakeLinkedInAssetUploader()
        val storage = FakeStorage()
        val assetUploadProperties = LinkedInAssetUploadProperties("test-bucket")
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
            assetUploader = assetUploader,
            storage = storage,
            attachmentsBucket = assetUploadProperties.attachmentsBucket,
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

    @Test
    fun `should throw typed retryable rate limit failure when LinkedIn returns 429`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    429,
                    emptyHeaders(),
                    """{"message":"Too many requests"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
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
            bodyText = "Test",
        )

        val error = assertThrows(PublishingFailureException::class.java) {
            kotlinx.coroutines.runBlocking {
                publisher.publish(
                    ProviderPublishCommand(
                        publicationId = "pub-1",
                        workspaceId = "workspace-1",
                        socialAccount = account,
                        publication = publication,
                        assets = emptyList(),
                    ),
                )
            }
        }

        assertEquals(PublishingFailureCategory.PROVIDER_RATE_LIMITED, error.failure.category)
        assertEquals(true, error.failure.retryable)
        assertEquals("status=429", error.failure.diagnostic)
    }

    @Test
    fun `should throw typed retryable provider unavailable failure when LinkedIn returns 500`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    500,
                    emptyHeaders(),
                    """{"message":"Internal server error"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
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
            bodyText = "Test",
        )

        val error = assertThrows(PublishingFailureException::class.java) {
            kotlinx.coroutines.runBlocking {
                publisher.publish(
                    ProviderPublishCommand(
                        publicationId = "pub-1",
                        workspaceId = "workspace-1",
                        socialAccount = account,
                        publication = publication,
                        assets = emptyList(),
                    ),
                )
            }
        }

        assertEquals(PublishingFailureCategory.PROVIDER_UNAVAILABLE, error.failure.category)
        assertEquals(true, error.failure.retryable)
        assertEquals("status=500", error.failure.diagnostic)
    }

    @Test
    fun `should throw typed reconnect failure when LinkedIn returns 403`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    403,
                    emptyHeaders(),
                    """{"message":"Forbidden"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
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
            bodyText = "Test",
        )

        val error = assertThrows(PublishingFailureException::class.java) {
            kotlinx.coroutines.runBlocking {
                publisher.publish(
                    ProviderPublishCommand(
                        publicationId = "pub-1",
                        workspaceId = "workspace-1",
                        socialAccount = account,
                        publication = publication,
                        assets = emptyList(),
                    ),
                )
            }
        }

        assertEquals(PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED, error.failure.category)
        assertEquals(false, error.failure.retryable)
        assertEquals("status=403", error.failure.diagnostic)
    }

    @Test
    fun `should throw typed reconnect failure when LinkedIn returns 401`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    401,
                    emptyHeaders(),
                    """{"message":"Unauthorized"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
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
            bodyText = "Test",
        )

        val error = assertThrows(PublishingFailureException::class.java) {
            kotlinx.coroutines.runBlocking {
                publisher.publish(
                    ProviderPublishCommand(
                        publicationId = "pub-1",
                        workspaceId = "workspace-1",
                        socialAccount = account,
                        publication = publication,
                        assets = emptyList(),
                    ),
                )
            }
        }

        assertEquals(PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED, error.failure.category)
        assertEquals(false, error.failure.retryable)
        assertEquals("status=401", error.failure.diagnostic)
    }

    @Test
    fun `real publisher throws when social account missing profile urn`() = runTest {
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
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
        )
        val accountWithoutUrn = SocialAccount(
            id = "account-1",
            socialConnectionId = "connection-1",
            workspaceId = "workspace-1",
            provider = SocialProvider.LINKEDIN,
            providerAccountId = accountId,
            kind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Yuniel",
            profileUrn = null,
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
            bodyText = "Test post",
        )

        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                publisher.publish(
                    ProviderPublishCommand(
                        publicationId = "pub-1",
                        workspaceId = "workspace-1",
                        socialAccount = accountWithoutUrn,
                        publication = publication,
                        assets = emptyList(),
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("missing a person URN"))
    }

    @Test
    fun `real publisher builds post with asset content entities and article link`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    201,
                    headersOf("x-restli-id" to "post-456"),
                    """{"id":"post-456"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val assetUploader = FakeLinkedInAssetUploader()
        val storage = FakeStorage()
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
            assetUploader = assetUploader,
            storage = storage,
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
        val asset = PublicationAsset(
            id = "asset-1",
            workspaceId = "workspace-1",
            sourceType = AssetSourceType.EXTERNAL_URL,
            mediaType = "image/png",
            externalUrl = "https://cdn.example.com/image.png",
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = "principal-1",
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
        )

        val result = publisher.publish(
            ProviderPublishCommand(
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                socialAccount = account,
                publication = publication,
                assets = listOf(asset),
            ),
        )

        assertEquals("post-456", result.externalPublicationId)
    }

    @Test
    fun `real publisher builds post with assets only no article link`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    201,
                    headersOf("x-restli-id" to "post-789"),
                    """{"id":"post-789"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val accountId = "abcd1234"
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:$accountId".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token-123", null, null, scope = null))
        val assetUploader = FakeLinkedInAssetUploader()
        val storage = FakeStorage()
        val publisher = testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
            assetUploader = assetUploader,
            storage = storage,
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
        val asset = PublicationAsset(
            id = "asset-1",
            workspaceId = "workspace-1",
            sourceType = AssetSourceType.EXTERNAL_URL,
            mediaType = "image/png",
            externalUrl = "https://cdn.example.com/image.png",
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = "principal-1",
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
            bodyText = "Just an image post with no link",
        )

        val result = publisher.publish(
            ProviderPublishCommand(
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                socialAccount = account,
                publication = publication,
                assets = listOf(asset),
            ),
        )

        assertEquals("post-789", result.externalPublicationId)
    }

    // ===== LinkedInPublishingConfiguration Tests =====

    @Test
    fun `linkedin publishing properties binds from environment`() {
        val props = LinkedInPublishingProperties(
            clientId = "my-client-id",
            clientSecret = "my-client-secret",
            redirectUri = "https://app.example.com/callback",
            scopes = "openid profile email",
            apiBaseUrl = "https://api.linkedin.com",
            authorizationBaseUrl = "https://www.linkedin.com/oauth/v2/authorization",
            tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
            apiVersion = "202601",
        )

        assertEquals("my-client-id", props.clientId)
        assertEquals("my-client-secret", props.clientSecret)
        assertEquals("https://api.linkedin.com", props.apiBaseUrl)
        assertEquals("202601", props.apiVersion)
    }

    @Test
    fun `attachments storage binding preserves logical provider and physical bucket`() {
        val binding = AttachmentsStorageBinding(
            providerName = "attachments",
            bucketName = "my-bucket",
            storage = FakeStorage(),
        )

        assertEquals("attachments", binding.providerName)
        assertEquals("my-bucket", binding.bucketName)
    }

    @Test
    fun `attachments storage binding factory resolves provider from BucketRegistry`() {
        val storage = FakeStorage()
        val registry = BucketRegistry { storage }
        val properties = StorageProperties(
            default = "attachments",
            providers = mapOf("attachments" to ProviderConfig(type = "local", basePath = "/tmp/x")),
        )

        val binding = AttachmentsStorageBindingFactory.from(registry, properties)

        assertEquals("attachments", binding.providerName)
        assertEquals("attachments", binding.bucketName)
        assertSame(storage, binding.storage)
    }

    @Test
    fun `attachments storage binding factory uses configured physical bucket when present`() {
        val storage = FakeStorage()
        val registry = BucketRegistry { storage }
        val properties = StorageProperties(
            default = "attachments",
            providers = mapOf(
                "attachments" to ProviderConfig(type = "s3", bucket = "profiletailors-attachments"),
            ),
        )

        val binding = AttachmentsStorageBindingFactory.from(registry, properties)

        assertEquals("attachments", binding.providerName)
        assertEquals("profiletailors-attachments", binding.bucketName)
    }

    @Test
    fun `publisher reads attachments from binding bucket and never bypasses it`() = runTest {
        val storage = BucketAssertingStorage()
        val binding = AttachmentsStorageBinding(
            providerName = "attachments",
            bucketName = "attachments",
            storage = storage,
        )
        val publisher = publisherWiredTo(binding, storage)
        val asset = testAsset("image/png").copy(storageKey = "assets/dev-workspace-001/blobs/hash.png")

        publisher.publish(
            publishCommandForAsset(asset),
        )

        // Binding forces the publisher to read from the configured logical bucket only.
        // Anything probed under "profiletailors-attachments" would mean we bypassed the binding.
        val download = storage.lastDownload
        assertNotNull(download)
        assertEquals(binding.bucketName, download!!.first)
        assertEquals(false, storage.wrongBucketProbed)
    }

    private fun publisherWiredTo(
        binding: AttachmentsStorageBinding,
        storage: BucketAssertingStorage,
    ): RealLinkedInPublisher {
        val transport = StubTransport(
            listOf(
                LinkedInHttpResponse(
                    statusCode = 201,
                    headers = headersOf("x-restli-id" to "post-123"),
                    body = """{"id":"post-123"}""",
                ),
            ),
        )
        val credentialGateway = FakeCredentialGateway()
        val derivedUuid = UUID.nameUUIDFromBytes("linkedin:abcd1234".toByteArray())
        credentialGateway.store(derivedUuid, LinkedInCredentials("access-token", null, null, scope = null))
        return testPublisher(
            transport = transport,
            credentialGateway = credentialGateway,
            credentialReference = derivedUuid,
            assetUploader = FakeLinkedInAssetUploader(),
            storage = storage,
            attachmentsBucket = binding.bucketName,
        )
    }

    private fun publishCommandForAsset(asset: PublicationAsset): ProviderPublishCommand {
        val account = testSocialAccount().copy(providerAccountId = "abcd1234")
        val publication = PublicationDraft(
            id = "pub-1",
            workspaceId = "workspace-1",
            authorPrincipalId = "principal-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = account.id,
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = false,
            title = null,
            bodyText = "hi",
            assetIds = emptyList(),
            scheduledFor = null,
            nextSlotAfter = null,
            publishedAt = null,
            failedAt = null,
            externalPublicationId = null,
            publicUrl = null,
            blockedAt = null,
            blockedReason = null,
            retryCount = 0,
            lastErrorCode = null,
            lastErrorMessage = null,
            createdAt = null,
            updatedAt = null,
        )
        return ProviderPublishCommand(
            publicationId = publication.id,
            workspaceId = publication.workspaceId,
            publication = publication,
            socialAccount = account,
            assets = listOf(asset),
        )
    }

    @Test
    fun `form url encoded encodes key value pairs correctly`() {
        val result = formUrlEncoded(
            "grant_type" to "authorization_code",
            "code" to "auth-code-123",
            "client_id" to "my-id",
        )

        assertTrue(result.contains("grant_type=authorization_code"))
        assertTrue(result.contains("code=auth-code-123"))
        assertTrue(result.contains("client_id=my-id"))
        assertTrue(result.contains("&"))
    }

    @Test
    fun `url encode handles special characters`() {
        val result = urlEncode("auth-code-123!")
        // The result should contain URL-encoded characters
        assert(result.contains("%"))
    }

    // ===== LinkedInUserInfoResponse.displayName() Tests =====

    @Test
    fun `display name uses name field when present`() {
        val response = LinkedInUserInfoResponse(
            sub = "user-123",
            name = "Yuniel Acosta",
            givenName = "Yuniel",
            familyName = "Acosta",
            email = "yuniel@example.com",
        )

        assertEquals("Yuniel Acosta", response.displayName())
    }

    // ===== Avatar URL mapping tests =====

    @Test
    fun `connection provider maps valid https picture to avatarUrl`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000,"scope":"openid profile email"}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"user-123","name":"Yuniel",""" +
                        """"picture":"https://media.licdn.com/dms/image/v2/example.jpg"}""",
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

        assertEquals("https://media.licdn.com/dms/image/v2/example.jpg", result.account.avatarUrl)
    }

    @Test
    fun `connection provider sets null avatarUrl when picture is absent`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"user-123","name":"Yuniel"}""",
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

        assertNull(result.account.avatarUrl)
    }

    @Test
    fun `connection provider rejects data-uri picture and sets null avatarUrl`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"user-123","name":"Yuniel","picture":"data:image/png;base64,iVBOR..."}""",
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

        assertNull(result.account.avatarUrl)
    }

    @Test
    fun `connection provider rejects non-https picture and sets null avatarUrl`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"access_token":"access-123","expires_in":5184000}""",
                ),
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"sub":"user-123","name":"Yuniel","picture":"http://insecure.example.com/photo.jpg"}""",
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

        assertNull(result.account.avatarUrl)
    }

    @Test
    fun `display name falls back to given plus family name when no name`() {
        val response = LinkedInUserInfoResponse(
            sub = "user-123",
            name = null,
            givenName = "Yuniel",
            familyName = "Acosta",
            email = "yuniel@example.com",
        )

        assertEquals("Yuniel Acosta", response.displayName())
    }

    @Test
    fun `display name falls back to email when no name or given name`() {
        val response = LinkedInUserInfoResponse(
            sub = "user-123",
            name = null,
            givenName = null,
            familyName = null,
            email = "user@example.com",
        )

        assertEquals("user@example.com", response.displayName())
    }

    @Test
    fun `display name falls back to sub when nothing else available`() {
        val response = LinkedInUserInfoResponse(
            sub = "user-123",
            name = null,
            givenName = null,
            familyName = null,
            email = null,
        )

        assertEquals("user-123", response.displayName())
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
        val asset = testAsset("application/zip")

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, listOf(asset)))
        }

        assertTrue(error.message!!.contains("Unsupported media type"))
        assertTrue(error.message!!.contains("application/zip"))
    }

    @Test
    fun `capability validator rejects multiple unsupported media types`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val assets = listOf(
            testAsset("application/zip"),
            testAsset("text/html"),
        )

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, assets))
        }

        assertTrue(error.message!!.contains("application/zip"))
        assertTrue(error.message!!.contains("text/html"))
    }

    @Test
    fun `capability validator accepts empty assets list`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()

        validator.validate(testValidationInput(account, emptyList()))
        // If we get here without exception, the test passes
    }

    @Test
    fun `capability validator rejects blank media type`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val asset = testAsset("")

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, listOf(asset)))
        }

        assertTrue(error.message!!.contains("media type"))
    }

    @Test
    fun `capability validator rejects asset exceeding max size`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val largeAsset = PublicationAsset(
            id = "asset-big",
            workspaceId = "workspace-1",
            sourceType = AssetSourceType.UPLOADED,
            mediaType = "image/jpeg",
            storageKey = "assets/workspace-1/asset-big",
            fileSizeBytes = 600L * 1024 * 1024, // 600MB > 500MB limit
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = "principal-1",
        )

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, listOf(largeAsset)))
        }

        assertTrue(error.message!!.contains("exceeds maximum size"))
    }

    @Test
    fun `capability validator accepts asset at exactly max size`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        // Exactly 500MB should pass
        val maxAsset = PublicationAsset(
            id = "asset-max",
            workspaceId = "workspace-1",
            sourceType = AssetSourceType.UPLOADED,
            mediaType = "image/jpeg",
            storageKey = "assets/workspace-1/asset-max",
            fileSizeBytes = 500L * 1024 * 1024,
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = "principal-1",
        )

        validator.validate(testValidationInput(account, listOf(maxAsset)))
        // If we get here without exception, the test passes
    }

    @Test
    fun `capability validator rejects more than 10 assets`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val tooManyAssets = (1..11).map { i ->
            PublicationAsset(
                id = "asset-$i",
                workspaceId = "workspace-1",
                sourceType = AssetSourceType.UPLOADED,
                mediaType = "image/jpeg",
                storageKey = "assets/workspace-1/asset-$i",
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = "principal-1",
            )
        }

        val error = assertThrows(PublicationValidationException::class.java) {
            validator.validate(testValidationInput(account, tooManyAssets))
        }

        assertTrue(error.message!!.contains("10 assets"))
    }

    // ===== RealLinkedInAssetUploader Tests =====

    @Test
    fun `real linkedin asset uploader completes three step flow`() = runTest {
        val transport = StubTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"image":"urn:li:image:abc123","uploadUrl":"https://upload.linkedin.com/upload"}""",
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
        val uploader =
            RealLinkedInAssetUploader(
                properties,
                assetUploadProperties,
                objectMapper,
                transport,
                storage,
                assetRepository,
            )
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val result = uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)

        assertEquals("urn:li:image:abc123", result.providerAssetId)
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
        val uploader =
            RealLinkedInAssetUploader(
                properties,
                assetUploadProperties,
                objectMapper,
                transport,
                storage,
                FakePublicationAssetRepository(),
            )
        val asset = testAsset("image/jpeg")
        val context = testAssetUploadContext()

        val error = assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                uploader.uploadAsset(asset, flowOf(ByteArray(1024)), context)
            }
        }

        assertTrue(error.message!!.contains("asset registration failed"))
        assertTrue(!error.message!!.contains("Bad request"))
    }

    @Test
    fun `real linkedin asset uploader skips checkStatus for images`() = runTest {
        val transport = RecordingTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"image":"urn:li:image:abc123","uploadUrl":"https://upload.linkedin.com/upload"}""",
                ),
                LinkedInHttpResponse(200, emptyHeaders(), """{}"""),
            ),
        )
        val uploader = RealLinkedInAssetUploader(
            properties,
            LinkedInAssetUploadProperties("test-bucket"),
            objectMapper,
            transport,
            FakeStorage(),
            FakePublicationAssetRepository(),
        )
        val context = testAssetUploadContext()

        uploader.uploadAsset(testAsset("image/jpeg"), flowOf(ByteArray(1024)), context)

        val requests = transport.capturedRequests
        assertEquals(2, requests.size, "Expected only 2 calls (initializeUpload + binary upload), got ${requests.size}")
        assertTrue(requests[0].uri().toString().contains("/rest/images"))
        assertTrue(requests[0].uri().query?.contains("action=initializeUpload") == true)
        assertEquals("POST", requests[0].method())
        assertEquals("PUT", requests[1].method())
        assertTrue(
            requests.none { it.uri().query?.contains("checkStatus") == true },
            "Should not call checkStatus for images",
        )
    }

    @Test
    fun `real linkedin asset uploader skips checkStatus for documents`() = runTest {
        val transport = RecordingTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """{"document":"urn:li:document:doc123","uploadUrl":"https://upload.linkedin.com/upload"}""",
                ),
                LinkedInHttpResponse(200, emptyHeaders(), """{}"""),
            ),
        )
        val uploader = RealLinkedInAssetUploader(
            properties,
            LinkedInAssetUploadProperties("test-bucket"),
            objectMapper,
            transport,
            FakeStorage(),
            FakePublicationAssetRepository(),
        )
        val context = testAssetUploadContext()

        uploader.uploadAsset(testAsset("application/pdf"), flowOf(ByteArray(1024)), context)

        val requests = transport.capturedRequests
        assertEquals(2, requests.size, "Expected only 2 calls (initializeUpload + binary upload), got ${requests.size}")
        assertTrue(requests[0].uri().toString().contains("/rest/documents"))
        assertTrue(requests[0].uri().query?.contains("action=initializeUpload") == true)
        assertEquals("POST", requests[0].method())
        assertEquals("PUT", requests[1].method())
        assertTrue(
            requests.none { it.uri().query?.contains("checkStatus") == true },
            "Should not call checkStatus for documents",
        )
    }

    @Test
    fun `real linkedin asset uploader calls finalizeUpload for videos`() = runTest {
        val transport = RecordingTransport(
            responses = listOf(
                LinkedInHttpResponse(
                    200,
                    emptyHeaders(),
                    """
                        |{"video":"urn:li:video:v123","uploadUrl":"https://upload.linkedin.com/upload","uploadToken":"tok"}
                    """.trimMargin(),
                ),
                LinkedInHttpResponse(200, emptyHeaders(), """{}"""),
                LinkedInHttpResponse(200, emptyHeaders(), """{"status":"AVAILABLE"}"""),
            ),
        )
        val uploader = RealLinkedInAssetUploader(
            properties,
            LinkedInAssetUploadProperties("test-bucket"),
            objectMapper,
            transport,
            FakeStorage(),
            FakePublicationAssetRepository(),
        )
        val context = testAssetUploadContext()

        uploader.uploadAsset(testAsset("video/mp4"), flowOf(ByteArray(1024)), context)

        val requests = transport.capturedRequests
        assertEquals(
            3,
            requests.size,
            "Expected 3 calls (initializeUpload + binary + finalizeUpload), got ${requests.size}",
        )
        assertTrue(requests[0].uri().toString().contains("/rest/videos"))
        assertTrue(requests[0].uri().query?.contains("action=initializeUpload") == true)
        assertEquals("PUT", requests[1].method())
        assertTrue(requests[2].uri().toString().contains("/rest/videos"))
        assertTrue(requests[2].uri().query?.contains("action=finalizeUpload") == true)
        assertEquals("POST", requests[2].method())
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

    private fun testPublication(bodyText: String): PublicationDraft = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = PublicationStatus.QUEUED,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        bodyText = bodyText,
    )

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

    private fun testAsset(mediaType: String): PublicationAsset = testAsset(mediaType, null)

    private fun testAsset(mediaType: String, fileSizeBytes: Long?): PublicationAsset = PublicationAsset(
        id = "asset-1",
        workspaceId = "workspace-1",
        sourceType = AssetSourceType.UPLOADED,
        mediaType = mediaType,
        storageKey = "assets/workspace-1/asset-1",
        fileSizeBytes = fileSizeBytes,
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

    private fun testPublisher(
        transport: LinkedInHttpTransport,
        credentialGateway: LinkedInCredentialGateway,
        credentialReference: UUID,
        assetUploader: AssetUploader = FakeLinkedInAssetUploader(),
        storage: Storage? = FakeStorage(),
        attachmentsBucket: String = "test-bucket",
    ): RealLinkedInPublisher {
        val resolver = TestCredentialResolver(credentialGateway, credentialReference)
        return RealLinkedInPublisher(
            properties,
            objectMapper,
            transport,
            resolver,
            assetUploader,
            attachmentsBinding = AttachmentsStorageBinding(
                providerName = attachmentsBucket,
                bucketName = attachmentsBucket,
                storage = storage ?: FakeStorage(),
            ),
        )
    }

    private class TestCredentialResolver(
        private val gateway: LinkedInCredentialGateway,
        private val credentialId: UUID,
    ) : com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver {
        override suspend fun resolve(account: SocialAccount): String =
            gateway.resolveCredential(credentialId).accessToken
    }

    private class StubTransport(private val responses: List<LinkedInHttpResponse>) : LinkedInHttpTransport {
        private var index = 0
        override suspend fun send(request: java.net.http.HttpRequest): LinkedInHttpResponse =
            responses.getOrElse(index++) {
                throw IllegalStateException("No stub response configured")
            }
    }

    /**
     * Transport that records every incoming request so tests can assert on URL, method, and
     * call count. Returns the next response from [responses], throwing if the list is exhausted.
     */
    private class RecordingTransport(private val responses: List<LinkedInHttpResponse>) : LinkedInHttpTransport {
        val capturedRequests: MutableList<java.net.http.HttpRequest> = mutableListOf()
        val capturedBodies: MutableList<com.fasterxml.jackson.databind.JsonNode> = mutableListOf()
        private var index = 0
        override suspend fun send(request: java.net.http.HttpRequest): LinkedInHttpResponse {
            capturedRequests += request
            request.bodyPublisher().ifPresent { publisher ->
                val bytes = java.io.ByteArrayOutputStream()
                val subscription = java.util.concurrent.CountDownLatch(1)
                publisher.subscribe(object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
                    override fun onSubscribe(subscriptionValue: java.util.concurrent.Flow.Subscription) {
                        subscriptionValue.request(Long.MAX_VALUE)
                    }
                    override fun onNext(item: java.nio.ByteBuffer) {
                        val copy = item.duplicate()
                        val data = ByteArray(copy.remaining())
                        copy.get(data)
                        bytes.write(data)
                    }
                    override fun onError(throwable: Throwable) {
                        subscription.countDown()
                    }
                    override fun onComplete() {
                        subscription.countDown()
                    }
                })
                check(subscription.await(1, java.util.concurrent.TimeUnit.SECONDS))
                if (request.headers().firstValue("Content-Type").orElse("").contains("application/json")) {
                    capturedBodies.add(ObjectMapper().readTree(bytes.toByteArray()))
                }
            }
            return responses.getOrElse(index++) {
                throw IllegalStateException("No stub response configured for call ${capturedRequests.size}")
            }
        }
    }

    private class FakeCredentialGateway : LinkedInCredentialGateway {
        private val store = mutableMapOf<UUID, LinkedInCredentials>()

        override suspend fun storeForOwner(ownerType: String, ownerId: UUID, credentials: LinkedInCredentials): UUID {
            store[ownerId] = credentials
            return ownerId
        }

        override suspend fun resolveCredential(id: UUID): LinkedInCredentials =
            store[id] ?: throw IllegalStateException("No credentials found for $id")

        fun store(id: UUID, credentials: LinkedInCredentials) {
            store[id] = credentials
        }
    }

    private class FakeSocialConnectionRepository(private val connection: SocialConnection) :
        SocialConnectionRepository {
        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection

        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connection.takeIf { it.workspaceId == workspaceId && it.id == connectionId }
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

        override suspend fun exists(bucket: String, key: String): Boolean = false

        override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String): Unit =
            throw IllegalStateException("copyObject: source not found: $sourceKey")
    }

    /**
     * Storage test double that records the last attempted (bucket, key) probe so the test can
     * assert publishing reads from the configured logical bucket rather than a hardcoded
     * physical bucket name.
     */
    private class BucketAssertingStorage : Storage {
        var lastDownload: Pair<String, String>? = null
        var wrongBucketProbed: Boolean = false

        override suspend fun upload(
            bucket: String,
            key: String,
            content: Flow<ByteArray>,
            metadata: Map<String, String>,
        ) {
            content.collect { /* no-op */ }
        }

        override fun download(bucket: String, key: String): Flow<ByteArray> {
            if (bucket == "profiletailors-attachments") wrongBucketProbed = true
            lastDownload = bucket to key
            return flowOf("ok".toByteArray())
        }

        override suspend fun delete(bucket: String, key: String) = Unit

        override suspend fun list(bucket: String, prefix: String): List<String> = emptyList()

        override suspend fun exists(bucket: String, key: String): Boolean = false

        override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String): Unit =
            throw IllegalStateException("copyObject: source not found: $sourceKey")
    }

    private class FakePublicationAssetRepository : com.profiletailors.smp.publishing.domain.PublicationAssetRepository {
        private val items = linkedMapOf<String, com.profiletailors.smp.publishing.domain.PublicationAsset>()

        override suspend fun findByWorkspaceAndIds(
            workspaceId: String,
            assetIds: Collection<String>,
        ): List<com.profiletailors.smp.publishing.domain.PublicationAsset> =
            items.values.filter { it.workspaceId == workspaceId && it.id in assetIds }

        override suspend fun create(
            asset: com.profiletailors.smp.publishing.domain.PublicationAsset,
        ): com.profiletailors.smp.publishing.domain.PublicationAsset {
            items[asset.id] = asset
            return asset
        }

        override suspend fun updateStatus(
            assetId: String,
            status: com.profiletailors.smp.publishing.domain.PublicationAssetStatus,
        ) {
            items[assetId]?.let { items[assetId] = it.copy(status = status) }
        }

        override suspend fun updateProviderAssetRef(
            assetId: String,
            providerAssetRef: com.profiletailors.smp.publishing.domain.ProviderAssetRef,
        ) {
            items[assetId]?.let {
                items[assetId] = it.copy(
                    status = com.profiletailors.smp.publishing.domain.PublicationAssetStatus.READY,
                    providerAssetRef = providerAssetRef,
                )
            }
        }
    }

    private fun emptyHeaders(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

    private fun headersOf(vararg pairs: Pair<String, String>): HttpHeaders =
        HttpHeaders.of(pairs.groupBy({ it.first }, { it.second })) { _, _ -> true }

    // ===== Gated Capability Tests =====

    @Test
    fun `capability validator rejects organization page publishing for personal profile account`() {
        val validator = LinkedInCapabilityValidator()
        val personalAccount = testSocialAccount()

        // Simulate attempting to publish as organization
        val orgAccount = personalAccount.copy(kind = SocialAccountKind.ORGANIZATION_PAGE)

        val error = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(testValidationInput(orgAccount, emptyList()))
        }

        assertTrue(error.message!!.contains("personal profiles only"))
    }

    @Test
    fun `capability validator accepts personal profile text publishing`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()

        // Text-only publication (no assets) should pass for personal profile
        validator.validate(testValidationInput(account, emptyList()))
        // If we get here without exception, the test passes
    }

    @Test
    fun `capability validator accepts personal profile image publishing within limits`() {
        val validator = LinkedInCapabilityValidator()
        val account = testSocialAccount()
        val imageAsset = testAsset("image/jpeg")

        validator.validate(testValidationInput(account, listOf(imageAsset)))
        // If we get here without exception, the test passes
    }
}
