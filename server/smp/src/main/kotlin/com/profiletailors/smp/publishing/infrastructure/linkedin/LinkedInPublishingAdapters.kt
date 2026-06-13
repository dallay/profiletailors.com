package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.AssetUploader
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.AssetUploadContext
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderAccountProfile
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SocialPublisher
import com.profiletailors.smp.publishing.infrastructure.scheduling.RetryablePublishingException
import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.net.URI
import java.time.Clock
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID

data class LinkedInPublishingProperties(
    val mode: String,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: String,
    val apiBaseUrl: String,
    val authorizationBaseUrl: String,
    val tokenBaseUrl: String,
    val apiVersion: String,
)

class FakeLinkedInConnectionProvider : SocialConnectionProvider {
    override suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult =
        ProviderConnectionResult(
            provider = SocialProvider.LINKEDIN,
            providerConnectionRef = "fake-linkedin-connection-${command.workspaceId}",
            credentialReference = "fake-credential-${command.actorPrincipalId}",
            account = ProviderAccountProfile(
                providerAccountId = "linkedin-profile-${command.actorPrincipalId}",
                displayName = "Fake LinkedIn Profile",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                profileUrn = "urn:li:person:${command.actorPrincipalId}",
            ),
        )
}

class RealLinkedInConnectionProvider(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val credentialGateway: com.profiletailors.smp.publishing.infrastructure
        .credentials.LinkedInCredentialGateway,
) : SocialConnectionProvider {
    @Suppress("LongMethod", "ThrowsCount")
    override suspend fun completeConnection(
        command: CompleteProviderConnectionCommand
    ): ProviderConnectionResult {
        require(properties.clientId.isNotBlank()) {
            "LinkedIn clientId is required in real mode."
        }
        require(properties.clientSecret.isNotBlank()) {
            "LinkedIn clientSecret is required in real mode."
        }

        val formBody = formUrlEncoded(
            "grant_type" to "authorization_code",
            "code" to command.authorizationCode,
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "redirect_uri" to command.redirectUri,
        )
        val tokenResponse = httpTransport.send(
            HttpRequest.newBuilder(URI.create(properties.tokenBaseUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build(),
        )
        if (tokenResponse.statusCode !in HTTP_SUCCESS_RANGE) {
            val message = "LinkedIn token exchange failed: " +
                "${tokenResponse.statusCode} ${tokenResponse.body}"
            throw IllegalStateException(message)
        }
        val token = objectMapper.readValue(tokenResponse.body, LinkedInTokenResponse::class.java)
        val profileResponse = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${properties.apiBaseUrl}/v2/userinfo"))
                .header("Authorization", "Bearer ${token.accessToken}")
                .header("Content-Type", "application/json")
                .GET()
                .build(),
        )
        if (profileResponse.statusCode !in HTTP_SUCCESS_RANGE) {
            val message = "LinkedIn profile lookup failed: " +
                "${profileResponse.statusCode} ${profileResponse.body}"
            throw IllegalStateException(message)
        }
        val profile = objectMapper.readValue(
            profileResponse.body,
            LinkedInUserInfoResponse::class.java
        )
        val providerAccountId = profile.sub
            ?: throw IllegalStateException("LinkedIn user info response did not include subject id.")

        // store credentials securely and return a reference
        val credentials = com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentials(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAtEpochSeconds = token.expiresIn?.let {
                System.currentTimeMillis() / MILLIS_TO_SECONDS + it
            },
            scope = token.scope,
        )
        // LinkedIn providerAccountId is not a UUID, so we derive a stable UUID from it
        val ownerUuid = UUID.nameUUIDFromBytes("linkedin:$providerAccountId".toByteArray())
        val credentialRef = credentialGateway.storeForOwner("linkedin:user", ownerUuid, credentials)

        return ProviderConnectionResult(
            provider = SocialProvider.LINKEDIN,
            providerConnectionRef = "linkedin-member-$providerAccountId",
            credentialReference = credentialRef.toString(),
            account = ProviderAccountProfile(
                providerAccountId = providerAccountId,
                displayName = profile.displayName(),
                kind = SocialAccountKind.PERSONAL_PROFILE,
                profileUrn = "urn:li:person:$providerAccountId",
            ),
        )
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        const val MILLIS_TO_SECONDS = 1000L
    }
}

class LinkedInCapabilityValidator : ProviderCapabilityValidator {
    override fun validate(input: ProviderCapabilityValidationInput) {
        require(input.provider == SocialProvider.LINKEDIN) {
            "LinkedIn capability validator only supports LINKEDIN."
        }
        require(input.socialAccount.kind == SocialAccountKind.PERSONAL_PROFILE) {
            "LinkedIn MVP supports personal profiles only."
        }
        val assetCount = input.assets.size
        if (assetCount > MAX_ASSETS_PER_POST) {
            throw PublicationValidationException(
                "LinkedIn MVP supports up to $MAX_ASSETS_PER_POST assets per publication."
            )
        }
        if (input.assets.any { it.mediaType.isBlank() }) {
            throw PublicationValidationException("All publication assets require a media type.")
        }
        validateMediaTypes(input)
        validateFileSizes(input)
    }

    private fun validateMediaTypes(input: ProviderCapabilityValidationInput) {
        val unsupportedAssets = input.assets.filter { asset ->
            !SUPPORTED_MEDIA_TYPES.contains(asset.mediaType.uppercase())
        }
        if (unsupportedAssets.isNotEmpty()) {
            val types = unsupportedAssets.joinToString(", ") { it.mediaType }
            throw PublicationValidationException(
                "Unsupported media type(s) for LinkedIn: $types. " +
                    "Supported types: ${SUPPORTED_MEDIA_TYPES.joinToString(", ")}"
            )
        }
    }

    private fun validateFileSizes(input: ProviderCapabilityValidationInput) {
        for (asset in input.assets) {
            val size = asset.fileSizeBytes
            if (size != null && size > MAX_ASSET_SIZE_BYTES) {
                throw PublicationValidationException(
                    "Asset ${asset.id} exceeds maximum size of ${MAX_ASSET_SIZE_BYTES / MB}MB"
                )
            }
        }
    }

    private companion object {
        const val MAX_ASSETS_PER_POST = 10
        const val MAX_ASSET_SIZE_BYTES = 10L * 1024 * 1024 // 10MB
        const val MB = 1024 * 1024
        val SUPPORTED_MEDIA_TYPES = setOf(
            "IMAGE/JPEG",
            "IMAGE/PNG",
            "IMAGE/GIF",
            "IMAGE/WEBP",
            "VIDEO/MP4",
        )
    }
}

class FakeLinkedInPublisher : SocialPublisher {
    override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult =
        ProviderPublishResult(
            externalPublicationId = "fake-linkedin-post-${command.publicationId}",
            providerMessage = "Fake LinkedIn publish completed.",
        )
}

class RealLinkedInPublisher(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val credentialGateway: com.profiletailors.smp.publishing.infrastructure
        .credentials.LinkedInCredentialGateway,
    private val assetUploader: AssetUploader,
    private val storage: Storage?,
    private val attachmentsBucket: String,
) : SocialPublisher {
    override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
        val requestBody = buildPostBody(command)
        val accessToken = resolveAccessToken(command.socialAccount)
        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${properties.apiBaseUrl}/rest/posts"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("LinkedIn-Version", properties.apiVersion)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build(),
        )
        return when (response.statusCode) {
            in HTTP_SUCCESS_RANGE -> ProviderPublishResult(
                externalPublicationId = response.headers
                    .firstValue("x-restli-id")
                    .orElse("linkedin-post-${command.publicationId}"),
                providerMessage = response.body,
            )
            HTTP_TOO_MANY_REQUESTS, in HTTP_SERVER_ERROR_RANGE -> {
                val message = "LinkedIn post publish retryable failure: " +
                    "${response.statusCode} ${response.body}"
                throw RetryablePublishingException(message)
            }
            else -> {
                val message = "LinkedIn post publish failed: " +
                    "${response.statusCode} ${response.body}"
                throw IllegalStateException(message)
            }
        }
    }

    private suspend fun buildPostBody(command: ProviderPublishCommand): Map<String, Any> {
        val authorUrn = command.socialAccount.profileUrn
            ?: throw IllegalStateException(
                "LinkedIn social account is missing a person URN for authoring."
            )
        val commentary = command.publication.bodyText.orEmpty()
        val articleLink = extractFirstUrl(commentary)

        val assetContentEntities = buildAssetContentEntities(command, command.assets)

        val content = if (articleLink != null) {
            val articleContent = mapOf(
                "article" to mapOf(
                    "source" to articleLink,
                    "title" to (command.publication.title ?: articleLink),
                    "description" to commentary,
                ),
            )
            if (assetContentEntities.isNotEmpty()) {
                articleContent + ("contentEntities" to assetContentEntities)
            } else {
                articleContent
            }
        } else if (assetContentEntities.isNotEmpty()) {
            mapOf("contentEntities" to assetContentEntities)
        } else {
            emptyMap()
        }

        return linkedMapOf(
            "author" to authorUrn,
            "commentary" to commentary,
            "visibility" to "PUBLIC",
            "distribution" to mapOf(
                "feedDistribution" to "MAIN_FEED",
                "targetEntities" to emptyList<String>(),
                "thirdPartyDistributionChannels" to emptyList<String>(),
            ),
            "lifecycleState" to "PUBLISHED",
            "isReshareDisabledByAuthor" to false,
        ).also {
            if (content.isNotEmpty()) {
                it["content"] = content
            }
        }
    }

    private suspend fun buildAssetContentEntities(
        command: ProviderPublishCommand,
        assets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
    ): List<Map<String, Any>> {
        if (assets.isEmpty()) return emptyList()

        val accessToken = resolveAccessToken(command.socialAccount)
        val context = AssetUploadContext(
            socialAccount = command.socialAccount,
            accessToken = accessToken,
            apiBaseUrl = properties.apiBaseUrl,
            apiVersion = properties.apiVersion,
        )

        return assets.map { asset ->
            when (asset.sourceType) {
                AssetSourceType.UPLOADED -> {
                    val existingRef = asset.providerAssetRef
                    if (existingRef != null) {
                        mapOf("entity" to existingRef.providerAssetId)
                    } else {
                        val storageKey = asset.storageKey
                            ?: throw IllegalStateException("Uploaded asset is missing storage key")
                        val content = storage!!.download(attachmentsBucket, storageKey)
                        val assetRef = assetUploader.uploadAsset(asset, content, context)
                        mapOf("entity" to assetRef.providerAssetId)
                    }
                }
                AssetSourceType.EXTERNAL_URL -> {
                    val url = asset.externalUrl
                        ?: throw IllegalStateException("External URL asset is missing external URL")
                    mapOf("entity" to mapOf("source" to url))
                }
            }
        }
    }

    private fun extractFirstUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    private suspend fun resolveAccessToken(
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount
    ): String {
        val providerAccountId = socialAccount.providerAccountId
        require(providerAccountId.isNotBlank()) {
            "LinkedIn social account providerAccountId is required."
        }
        // Lookup social connection to find credential reference.
        // For now we infer owner id from providerAccountId.
        // In production we should link SocialAccount -> SocialConnection
        // and store the credentialReference on the connection.
        // LinkedIn providerAccountId is not a UUID, so we derive a stable UUID from it
        val ownerUuid = UUID.nameUUIDFromBytes("linkedin:$providerAccountId".toByteArray())
        val credential = credentialGateway.resolveCredential(ownerUuid)
        return credential.accessToken
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

interface LinkedInHttpTransport {
    suspend fun send(request: HttpRequest): LinkedInHttpResponse
}

data class LinkedInHttpResponse(
    val statusCode: Int,
    val headers: java.net.http.HttpHeaders,
    val body: String,
)

class JdkLinkedInHttpTransport(
    private val httpClient: HttpClient,
) : LinkedInHttpTransport {
    override suspend fun send(request: HttpRequest): LinkedInHttpResponse {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return LinkedInHttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers(),
            body = response.body(),
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long? = null,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
    @JsonProperty("scope") val scope: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInUserInfoResponse(
    @JsonProperty("sub") val sub: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("given_name") val givenName: String? = null,
    @JsonProperty("family_name") val familyName: String? = null,
    @JsonProperty("email") val email: String? = null,
) {
    fun displayName(): String = name
        ?: listOfNotNull(givenName, familyName).joinToString(" ").takeIf { it.isNotBlank() }
        ?: (email ?: sub ?: "LinkedIn Member")
}

@Configuration
class LinkedInPublishingConfiguration {
    @Bean
    fun linkedInPublishingProperties(
        @Value("\${publishing.linkedin.mode:fake}") mode: String,
        @Value("\${publishing.linkedin.client-id:}") clientId: String,
        @Value("\${publishing.linkedin.client-secret:}") clientSecret: String,
        @Value("\${publishing.linkedin.redirect-uri:}") redirectUri: String,
        @Value("\${publishing.linkedin.scopes:}") scopes: String,
        @Value("\${publishing.linkedin.api-base-url:https://api.linkedin.com}")
        apiBaseUrl: String,
        @Value("\${publishing.linkedin.authorization-base-url:https://www.linkedin.com/oauth/v2/authorization}")
        authorizationBaseUrl: String,
        @Value("\${publishing.linkedin.token-base-url:https://www.linkedin.com/oauth/v2/accessToken}")
        tokenBaseUrl: String,
        @Value("\${publishing.linkedin.api-version:202601}") apiVersion: String,
    ): LinkedInPublishingProperties = LinkedInPublishingProperties(
        mode = mode,
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUri = redirectUri,
        scopes = scopes,
        apiBaseUrl = apiBaseUrl,
        authorizationBaseUrl = authorizationBaseUrl,
        tokenBaseUrl = tokenBaseUrl,
        apiVersion = apiVersion,
    )

    @Bean
    fun linkedInAuthorizationUrlBuilder(properties: LinkedInPublishingProperties): LinkedInAuthorizationUrlBuilder =
        LinkedInAuthorizationUrlBuilderAdapter(properties)

    @Bean
    fun oauthStateSigner(
        @Value(
            "\${publishing.linkedin.state-signing-secret:profiletailors-dev-oauth-state-secret}",
        ) stateSigningSecret: String,
        objectMapper: ObjectMapper,
        clock: Clock,
    ): OAuthStateSigner = HmacOAuthStateSigner(stateSigningSecret, objectMapper, clock)

    @Bean
    fun linkedInHttpTransport(): LinkedInHttpTransport = JdkLinkedInHttpTransport(HttpClient.newHttpClient())

    @Bean
    fun linkedInAssetUploadProperties(
        @Value("\${platform.storage.providers.attachments.bucket:profiletailors-attachments}")
        attachmentsBucket: String,
    ): LinkedInAssetUploadProperties = LinkedInAssetUploadProperties(
        attachmentsBucket = attachmentsBucket,
    )

    @Bean
    fun assetUploader(
        properties: LinkedInPublishingProperties,
        assetUploadProperties: LinkedInAssetUploadProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        @Autowired(required = false) storage: Storage?,
        publicationAssetRepository: com.profiletailors.smp.publishing.domain.PublicationAssetRepository,
    ): AssetUploader =
        if (properties.mode.equals("real", ignoreCase = true)) {
            RealLinkedInAssetUploader(
                properties,
                assetUploadProperties,
                objectMapper,
                linkedInHttpTransport,
                storage,
                publicationAssetRepository,
            )
        } else {
            FakeLinkedInAssetUploader()
        }

    @Bean
    fun socialConnectionProvider(
        properties: LinkedInPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialGateway: com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway,
    ): SocialConnectionProvider =
        if (properties.mode.equals("real", ignoreCase = true)) {
            RealLinkedInConnectionProvider(properties, objectMapper, linkedInHttpTransport, credentialGateway)
        } else {
            FakeLinkedInConnectionProvider()
        }

    @Bean
    fun socialPublisher(
        properties: LinkedInPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialGateway: com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway,
        assetUploader: AssetUploader,
        @Autowired(required = false) storage: Storage?,
        assetUploadProperties: LinkedInAssetUploadProperties,
    ): SocialPublisher =
        if (properties.mode.equals("real", ignoreCase = true)) {
            RealLinkedInPublisher(
                properties,
                objectMapper,
                linkedInHttpTransport,
                credentialGateway,
                assetUploader,
                storage,
                assetUploadProperties.attachmentsBucket,
            )
        } else {
            FakeLinkedInPublisher()
        }

    @Bean
    fun providerCapabilityValidator(): ProviderCapabilityValidator = LinkedInCapabilityValidator()
}

fun formUrlEncoded(vararg parts: Pair<String, String>): String = parts.joinToString("&") { (key, value) ->
    "${urlEncode(key)}=${urlEncode(value)}"
}

fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
