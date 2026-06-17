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
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.SocialPublisher
import com.profiletailors.smp.publishing.infrastructure.scheduling.RetryablePublishingException
import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
import org.slf4j.LoggerFactory

@ConfigurationProperties(prefix = "publishing.linkedin")
data class LinkedInPublishingProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val scopes: String = "",
    val apiBaseUrl: String = "https://api.linkedin.com",
    val publishingApiBaseUrl: String = apiBaseUrl,
    val authorizationBaseUrl: String = "https://www.linkedin.com/oauth/v2/authorization",
    val tokenBaseUrl: String = "https://www.linkedin.com/oauth/v2/accessToken",
    val apiVersion: String = "202601",
) {
    fun isConfigured(): Boolean = clientId.isNotBlank() && clientSecret.isNotBlank() && redirectUri.isNotBlank()
}

class RealLinkedInConnectionProvider(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val credentialGateway: com.profiletailors.smp.publishing.infrastructure
        .credentials.LinkedInCredentialGateway,
) : SocialConnectionProvider {
    private val log = LoggerFactory.getLogger(javaClass)

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
                .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
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
                .header(CONTENT_TYPE, "application/json")
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
                avatarUrl = sanitizeAvatarUrl(profile.picture),
            ),
        )
    }

    private fun sanitizeAvatarUrl(picture: String?): String? {
        val trimmed = picture?.trim()
        if (trimmed.isNullOrBlank() || !trimmed.startsWith("https://", ignoreCase = true)) {
            if (!trimmed.isNullOrBlank()) {
                log.debug("LinkedIn avatar rejected — not HTTPS: {}", trimmed.take(MAX_AVATAR_URL_LOG_LENGTH))
            }
            return null
        }
        return trimmed
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        const val MILLIS_TO_SECONDS = 1000L
        const val MAX_AVATAR_URL_LOG_LENGTH = 120
    }
}

class LinkedInCapabilityValidator(
    private val enabledBundles: Set<com.profiletailors.smp.publishing.domain.LinkedinCapabilityBundle> = setOf(
        com.profiletailors.smp.publishing.domain.LinkedinCapabilityBundle.PERSONAL_PROFILE_TEXT,
        com.profiletailors.smp.publishing.domain.LinkedinCapabilityBundle.PERSONAL_PROFILE_IMAGE,
    ),
) : ProviderCapabilityValidator {
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
        // LinkedIn videos support up to 500MB; documents are capped lower by API.
        const val MAX_ASSET_SIZE_BYTES = 500L * 1024 * 1024
        const val MB = 1024 * 1024
        val SUPPORTED_MEDIA_TYPES = setOf(
            "IMAGE/JPEG",
            "IMAGE/PNG",
            "IMAGE/GIF",
            "IMAGE/WEBP",
            "VIDEO/MP4",
            "APPLICATION/PDF",
            "APPLICATION/MSWORD",
            "APPLICATION/VND.OPENXMLFORMATS-OFFICEDOCUMENT.WORDPROCESSINGML.DOCUMENT",
            "APPLICATION/VND.MS-POWERPOINT",
            "APPLICATION/VND.OPENXMLFORMATS-OFFICEDOCUMENT.PRESENTATIONML.PRESENTATION",
        )
    }
}

class RealLinkedInPublisher(
    private val properties: LinkedInPublishingProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val credentialResolver: com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver,
    private val assetUploader: AssetUploader,
    private val storage: Storage?,
    private val attachmentsBucket: String,
) : SocialPublisher {
    /**
     * Publishes a social media post to LinkedIn.
     *
     * @return The publication result containing the external publication ID and the provider's response message.
     * @throws RetryablePublishingException if the request is rate-limited (429) or receives a server error (500-599).
     * @throws IllegalStateException if the request fails with any other HTTP status code.
     */
    override suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult {
        val requestBody = buildPostBody(command)
        val accessToken = credentialResolver.resolve(command.socialAccount)
        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${properties.publishingApiBaseUrl}/rest/posts"))
                .header("Authorization", "Bearer $accessToken")
                .header(CONTENT_TYPE, "application/json")
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

    /**
     * Constructs the request body for publishing a post to LinkedIn.
     *
     * Requires the social account to have a profile URN; throws [IllegalStateException] if missing.
     *
     * @return A map containing the post author, commentary, visibility settings, and optional media or article content formatted for the LinkedIn API.
     * @throws IllegalStateException If the social account is missing a profile URN.
     */
    private suspend fun buildPostBody(command: ProviderPublishCommand): Map<String, Any> {
        val authorUrn = command.socialAccount.profileUrn
            ?: throw IllegalStateException(
                "LinkedIn social account is missing a person URN for authoring."
            )
        val commentary = command.publication.bodyText.orEmpty()
        val articleLink = extractFirstUrl(commentary)

        val assetContent = buildAssetContent(command, command.assets)

        val content = if (assetContent.isNotEmpty()) {
            assetContent
        } else if (articleLink != null) {
            mapOf(
                "article" to mapOf(
                    "source" to articleLink,
                    "title" to (command.publication.title ?: articleLink),
                    "description" to commentary,
                ),
            )
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

    /**
     * Constructs the asset content structure for a LinkedIn post.
     *
     * @return A map representing the asset content for the LinkedIn API, with `multiImage` for multiple images, `media` for a single image, video, or document, or an empty map if no assets are present or match.
     */
    private suspend fun buildAssetContent(
        command: ProviderPublishCommand,
        assets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
    ): Map<String, Any> {
        if (assets.isEmpty()) return emptyMap()

        val refs = resolveAssetRefs(command, assets)
        val imageRefs = refs.filter { it.mediaType.startsWith("image/", ignoreCase = true) }
        val videoRef = refs.firstOrNull { it.mediaType.startsWith("video/", ignoreCase = true) }
        val documentRef = refs.firstOrNull {
            it.mediaType.equals("application/pdf", ignoreCase = true) ||
                it.mediaType.startsWith("document/", ignoreCase = true)
        }

        return when {
            imageRefs.size > 1 -> mapOf(
                "multiImage" to mapOf(
                    "images" to imageRefs.map { mapOf("id" to it.providerAssetId) },
                ),
            )
            imageRefs.size == 1 -> mapOf(
                "media" to mapOf("id" to imageRefs.first().providerAssetId),
            )
            videoRef != null -> mapOf(
                "media" to mapOf(
                    "id" to videoRef.providerAssetId,
                    "title" to (command.publication.title ?: "Video"),
                ),
            )
            documentRef != null -> mapOf(
                "media" to mapOf(
                    "id" to documentRef.providerAssetId,
                    "title" to (command.publication.title ?: "Document"),
                ),
            )
            else -> emptyMap()
        }
    }

    /**
     * Converts publication assets into provider asset references for LinkedIn publication.
     *
     * @return A list of provider asset references.
     * @throws IllegalStateException if an uploaded asset lacks a storage key or an external URL asset lacks a URL.
     */
    private suspend fun resolveAssetRefs(
        command: ProviderPublishCommand,
        assets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
    ): List<com.profiletailors.smp.publishing.domain.ProviderAssetRef> {
        val accessToken = credentialResolver.resolve(command.socialAccount)
        val context = AssetUploadContext(
            socialAccount = command.socialAccount,
            accessToken = accessToken,
            apiBaseUrl = properties.publishingApiBaseUrl,
            apiVersion = properties.apiVersion,
        )

        return assets.map { asset ->
            when (asset.sourceType) {
                AssetSourceType.UPLOADED -> {
                    val existingRef = asset.providerAssetRef
                    if (existingRef != null) {
                        existingRef
                    } else {
                        val storageKey = asset.storageKey
                            ?: throw IllegalStateException("Uploaded asset is missing storage key")
                        val content = storage!!.download(attachmentsBucket, storageKey)
                        assetUploader.uploadAsset(asset, content, context)
                    }
                }
                AssetSourceType.EXTERNAL_URL -> {
                    val url = asset.externalUrl
                        ?: throw IllegalStateException("External URL asset is missing external URL")
                    com.profiletailors.smp.publishing.domain.ProviderAssetRef(
                        providerAssetId = url,
                        mediaType = asset.mediaType,
                        accessUrl = url,
                    )
                }
            }
        }
    }

    private fun extractFirstUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

internal const val CONTENT_TYPE = "Content-Type"

fun interface LinkedInHttpTransport {
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
    @JsonProperty("picture") val picture: String? = null,
) {
    fun displayName(): String = name
        ?: listOfNotNull(givenName, familyName).joinToString(" ").takeIf { it.isNotBlank() }
        ?: (email ?: sub ?: "LinkedIn Member")
}

@Configuration
@EnableConfigurationProperties(LinkedInPublishingProperties::class)
class LinkedInPublishingConfiguration(
    @Autowired(required = false)
    private val storage: Storage?,
) {
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
    ): AssetUploader = RealLinkedInAssetUploader(
        properties,
        assetUploadProperties,
        objectMapper,
        linkedInHttpTransport,
        storage,
        publicationAssetRepository,
    )

    @Bean
    fun socialConnectionProvider(
        properties: LinkedInPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialGateway: com.profiletailors.smp.publishing.infrastructure.credentials.LinkedInCredentialGateway,
    ): SocialConnectionProvider = RealLinkedInConnectionProvider(
        properties, objectMapper, linkedInHttpTransport, credentialGateway,
    )

    @Bean
    fun socialPublisher(
        properties: LinkedInPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialResolver: com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver,
        assetUploader: AssetUploader,
        assetUploadProperties: LinkedInAssetUploadProperties,
    ): SocialPublisher = RealLinkedInPublisher(
        properties,
        objectMapper,
        linkedInHttpTransport,
        credentialResolver,
        assetUploader,
        storage,
        assetUploadProperties.attachmentsBucket,
    )

    @Bean
    fun providerCapabilityValidator(): ProviderCapabilityValidator = LinkedInCapabilityValidator(
        enabledBundles = setOf(
            com.profiletailors.smp.publishing.domain.LinkedinCapabilityBundle.PERSONAL_PROFILE_TEXT,
            com.profiletailors.smp.publishing.domain.LinkedinCapabilityBundle.PERSONAL_PROFILE_IMAGE,
        ),
    )
}

fun formUrlEncoded(vararg parts: Pair<String, String>): String = parts.joinToString("&") { (key, value) ->
    "${urlEncode(key)}=${urlEncode(value)}"
}

fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
