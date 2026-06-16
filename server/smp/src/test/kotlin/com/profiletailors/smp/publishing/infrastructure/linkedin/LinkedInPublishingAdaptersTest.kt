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
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
import java.util.UUID

class LinkedInPublishingAdaptersTest {

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
    fun `real publisher throws retryable on 429 rate limit`() = runTest {
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

        val error = assertThrows(RetryablePublishingException::class.java) {
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

        assertTrue(error.message!!.contains("retryable failure"))
    }

    @Test
    fun `real publisher throws retryable on 500 server error`() = runTest {
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

        val error = assertThrows(RetryablePublishingException::class.java) {
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

        assertTrue(error.message!!.contains("retryable failure"))
    }

    @Test
    fun `real publisher throws illegal state on unexpected error code`() = runTest {
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

        val error = assertThrows(IllegalStateException::class.java) {
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

        assertTrue(error.message!!.contains("publish failed"))
        assertTrue(error.message!!.contains("403"))
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
    fun `linkedin asset upload properties binds bucket from environment`() {
        val props = LinkedInAssetUploadProperties(attachmentsBucket = "my-bucket")

        assertEquals("my-bucket", props.attachmentsBucket)
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
                    """{"sub":"user-123","name":"Yuniel","picture":"https://media.licdn.com/dms/image/v2/example.jpg"}""",
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
            fileSizeBytes = 15L * 1024 * 1024, // 15MB > 10MB limit
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
        // Exactly 10MB should pass
        val maxAsset = PublicationAsset(
            id = "asset-max",
            workspaceId = "workspace-1",
            sourceType = AssetSourceType.UPLOADED,
            mediaType = "image/jpeg",
            storageKey = "assets/workspace-1/asset-max",
            fileSizeBytes = 10L * 1024 * 1024,
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
            storage,
            attachmentsBucket,
        )
    }

    private class TestCredentialResolver(
        private val gateway: LinkedInCredentialGateway,
        private val credentialId: UUID,
    ) : com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver {
        override suspend fun resolve(account: SocialAccount): String {
            return gateway.resolveCredential(credentialId).accessToken
        }
    }

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

    private class FakeSocialConnectionRepository(
        private val connection: SocialConnection,
    ) : SocialConnectionRepository {
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
