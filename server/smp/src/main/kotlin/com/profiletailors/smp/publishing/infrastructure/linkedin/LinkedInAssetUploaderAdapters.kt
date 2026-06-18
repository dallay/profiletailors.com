package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.AssetUploadContext
import com.profiletailors.smp.publishing.domain.AssetUploader
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.ProviderUploadException
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.net.URI
import java.net.http.HttpRequest

data class LinkedInAssetUploadProperties(
    val attachmentsBucket: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInAssetRegisterResponse(
    @JsonProperty("asset") val asset: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("document") val document: String? = null,
    @JsonProperty("video") val video: String? = null,
    @JsonProperty("uploadUrl") val uploadUrl: String? = null,
    @JsonProperty("uploadInstructions") val uploadInstructions: List<LinkedInUploadInstruction>? = null,
    @JsonProperty("uploadToken") val uploadToken: String? = null,
)

/**
 * Parses a LinkedIn asset registration response, extracting from the envelope if present.
 *
 * If the response contains a `"value"` object, extracts and parses that; otherwise parses the root object directly.
 *
 * @param body The JSON response body as a string.
 * @param objectMapper Jackson `ObjectMapper` for parsing.
 * @return The parsed `LinkedInAssetRegisterResponse`.
 */
private fun unwrapLinkedInResponse(body: String, objectMapper: ObjectMapper): LinkedInAssetRegisterResponse {
    val tree = objectMapper.readTree(body)
    val node = if (tree.has("value") && tree.get("value").isObject) tree.get("value") else tree
    return objectMapper.treeToValue(node, LinkedInAssetRegisterResponse::class.java)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInUploadInstruction(
    @JsonProperty("uploadUrl") val uploadUrl: String? = null,
    @JsonProperty("firstByte") val firstByte: Long? = null,
    @JsonProperty("lastByte") val lastByte: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInAssetStatusResponse(
    @JsonProperty("status") val status: String? = null,
)

class RealLinkedInAssetUploader(
    private val properties: LinkedInPublishingProperties,
    private val assetUploadProperties: LinkedInAssetUploadProperties,
    private val objectMapper: ObjectMapper,
    private val httpTransport: LinkedInHttpTransport,
    private val storage: Storage?,
    private val assetRepository: PublicationAssetRepository,
) : AssetUploader {
    /**
     * Uploads a publication asset to LinkedIn.
     *
     * Updates the asset status to [PublicationAssetStatus.PROCESSING] initially, then to
     * [PublicationAssetStatus.READY] upon success or [PublicationAssetStatus.FAILED] upon failure.
     *
     * @param asset The publication asset to upload.
     * @param content A flow of byte arrays representing the asset's binary data.
     * @param context Upload configuration including API endpoint, authentication, and account details.
     * @return A [ProviderAssetRef] containing the LinkedIn asset URN and media type.
     * @throws ProviderUploadException if the upload fails or LinkedIn returns an error.
     * @throws CancellationException if the upload is cancelled.
     */
    override suspend fun uploadAsset(
        asset: PublicationAsset,
        content: Flow<ByteArray>,
        context: AssetUploadContext,
    ): ProviderAssetRef {
        assetRepository.updateStatus(asset.id, PublicationAssetStatus.PROCESSING)

        val providerRef = runCatching {
            val registerResponse = registerAsset(asset, context)
            val uploadUrl = registerResponse.uploadUrl
                ?: registerResponse.uploadInstructions?.firstOrNull()?.uploadUrl
                .orThrow { "LinkedIn asset registration response missing uploadUrl" }
            val assetUrn = registerResponse.asset
                ?: registerResponse.image
                ?: registerResponse.document
                ?: registerResponse.video
                .orThrow { "LinkedIn asset registration response missing asset URN" }

            uploadBinary(uploadUrl, content)
            confirmAsset(assetUrn, registerResponse.uploadToken, asset, context)

            ProviderAssetRef(
                providerAssetId = assetUrn,
                mediaType = asset.mediaType,
                accessUrl = null,
            )
        }.onFailure { e ->
            if (e is CancellationException) {
                assetRepository.updateStatus(asset.id, PublicationAssetStatus.FAILED)
                throw e
            }
            if (e is ProviderUploadException || e is IllegalStateException || e is RuntimeException) {
                assetRepository.updateStatus(asset.id, PublicationAssetStatus.FAILED)
            }
        }.getOrThrow()

        assetRepository.updateStatus(asset.id, PublicationAssetStatus.READY)
        assetRepository.updateProviderAssetRef(asset.id, providerRef)
        return providerRef
    }

    /**
     * Initializes an asset upload by registering it with LinkedIn.
     *
     * @param asset The publication asset to register.
     * @param context The upload context containing API configuration and authentication.
     * @return The registration response containing upload instructions and asset URN.
     * @throws ProviderUploadException If the social account is missing a profile URN
     * or if the registration request fails.
     */
    private suspend fun registerAsset(
        asset: PublicationAsset,
        context: AssetUploadContext,
    ): LinkedInAssetRegisterResponse {
        val ownerUrn = context.socialAccount.profileUrn
            ?: throw ProviderUploadException(
                "Social account is missing a profile URN for asset registration."
            )

        val initializeUploadRequest = linkedMapOf<String, Any>(
            "owner" to ownerUrn,
        ).also {
            if (asset.mediaType.startsWith("video/", ignoreCase = true)) {
                it["fileSizeBytes"] = asset.fileSizeBytes ?: 0L
                it["uploadCaptions"] = false
                it["uploadThumbnail"] = false
            }
        }
        val registerBody = mapOf("initializeUploadRequest" to initializeUploadRequest)
        val endpoint = assetEndpoint(asset)

        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${context.apiBaseUrl}/rest/$endpoint?action=initializeUpload"))
                .header("Authorization", "Bearer ${context.accessToken}")
                .header(CONTENT_TYPE, "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("LinkedIn-Version", context.apiVersion)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(registerBody)))
                .build(),
        )

        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            throw ProviderUploadException(
                "LinkedIn asset registration failed: ${response.statusCode} ${response.body}"
            )
        }

        return runCatching {
            unwrapLinkedInResponse(response.body, objectMapper)
        }.getOrThrow()
    }

    /**
     * Uploads binary content to a specified LinkedIn URL via HTTP PUT.
     *
     * @throws ProviderUploadException If the HTTP response status code is not in the 200-299 range.
     */
    private suspend fun uploadBinary(uploadUrl: String, content: Flow<ByteArray>) {
        val bytes = content.collectToByteArray()
        val binaryRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
            .header(CONTENT_TYPE, "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()

        val response = httpTransport.send(binaryRequest)

        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            throw ProviderUploadException(
                "LinkedIn binary upload failed: ${response.statusCode} ${response.body}"
            )
        }
    }

    /**
     * Confirms the asset upload with LinkedIn.
     *
     * For videos, sends a finalization request with the upload token. For other asset types,
     * sends a status check request to the corresponding endpoint.
     *
     * @throws ProviderUploadException if the confirmation fails.
     */
    private suspend fun confirmAsset(
        assetUrn: String,
        uploadToken: String?,
        asset: PublicationAsset,
        context: AssetUploadContext,
    ) {
        val endpoint = assetEndpoint(asset)
        val confirmUrl = if (endpoint == "videos") {
            "${context.apiBaseUrl}/rest/videos?action=finalizeUpload"
        } else {
            "${context.apiBaseUrl}/rest/$endpoint/${encodeUrn(assetUrn)}?action=checkStatus"
        }
        val confirmBody = if (endpoint == "videos") {
            val requiredUploadToken = uploadToken
                ?: throw ProviderUploadException(
                    "LinkedIn video upload missing uploadToken for finalizeUpload (asset: ${asset.id})"
                )
            mapOf(
                "finalizeUploadRequest" to mapOf(
                    "video" to assetUrn,
                    "uploadToken" to requiredUploadToken,
                    "uploadedPartIds" to emptyList<String>(),
                ),
            )
        } else {
            emptyMap<String, Any>()
        }
        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create(confirmUrl))
                .header("Authorization", "Bearer ${context.accessToken}")
                .header(CONTENT_TYPE, "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("LinkedIn-Version", context.apiVersion)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(confirmBody)))
                .build(),
        )

        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            throw ProviderUploadException(
                "LinkedIn asset confirmation failed: ${response.statusCode} ${response.body}"
            )
        }
    }

    /**
     * Determines the LinkedIn REST endpoint segment for the given asset.
     *
     * @return The endpoint segment (`videos`, `documents`, or `images`) corresponding to the asset's media type.
     */
    private fun assetEndpoint(asset: PublicationAsset): String = when {
        asset.mediaType.startsWith("video/", ignoreCase = true) -> "videos"
        asset.mediaType.uppercase() in DOCUMENT_MEDIA_TYPES -> "documents"
        else -> "images"
    }

    /**
     * Encodes a URN by percent-encoding characters that are reserved in URI paths.
     *
     * @return The URN with `:` encoded as `%3A` and `/` encoded as `%2F`.
     */
    private fun encodeUrn(urn: String): String = urn.replace(":", "%3A").replace("/", "%2F")

    /**
     * Concatenates all byte arrays emitted by the flow.
     *
     * @return The concatenated bytes, or an empty byte array if no chunks were emitted.
     */
    private suspend fun Flow<ByteArray>.collectToByteArray(): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        this.collect { chunk ->
            chunks.add(chunk)
        }
        if (chunks.isEmpty()) return byteArrayOf()

        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        const val CONTENT_TYPE = "Content-Type"
    }
}

/**
 * Media types that LinkedIn treats as documents (uploaded via `/rest/documents`).
 * Shared between asset upload and post publishing logic.
 */
internal val DOCUMENT_MEDIA_TYPES = setOf(
    "APPLICATION/PDF",
    "APPLICATION/MSWORD",
    "APPLICATION/VND.OPENXMLFORMATS-OFFICEDOCUMENT.WORDPROCESSINGML.DOCUMENT",
    "APPLICATION/VND.MS-POWERPOINT",
    "APPLICATION/VND.OPENXMLFORMATS-OFFICEDOCUMENT.PRESENTATIONML.PRESENTATION",
)

private fun <T> T?.orThrow(lazyMessage: () -> String): T =
    this ?: throw ProviderUploadException(lazyMessage())
