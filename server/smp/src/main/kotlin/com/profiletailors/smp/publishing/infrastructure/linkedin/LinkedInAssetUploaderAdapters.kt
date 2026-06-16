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
    @JsonProperty("uploadUrl") val uploadUrl: String? = null,
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
    override suspend fun uploadAsset(
        asset: PublicationAsset,
        content: Flow<ByteArray>,
        context: AssetUploadContext,
    ): ProviderAssetRef {
        assetRepository.updateStatus(asset.id, PublicationAssetStatus.PROCESSING)

        val providerRef = runCatching {
            val registerResponse = registerAsset(context)
            val uploadUrl = registerResponse.uploadUrl
                .orThrow { "LinkedIn asset registration response missing uploadUrl" }
            val assetUrn = registerResponse.asset
                .orThrow { "LinkedIn asset registration response missing asset URN" }

            uploadBinary(uploadUrl, content)
            confirmAsset(assetUrn, context)

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

    private suspend fun registerAsset(
        context: AssetUploadContext,
    ): LinkedInAssetRegisterResponse {
        val ownerUrn = context.socialAccount.profileUrn
            ?: throw ProviderUploadException(
                "Social account is missing a profile URN for asset registration."
            )

        val registerBody = mapOf(
            "owner" to ownerUrn,
            "serviceRelationship" to mapOf(
                "relationshipType" to "OWNER",
                "identifier" to "urn:li:user:generated",
            ),
        )

        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create("${context.apiBaseUrl}/rest/images"))
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
            objectMapper.readValue(response.body, LinkedInAssetRegisterResponse::class.java)
        }.getOrThrow()
    }

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

    private suspend fun confirmAsset(assetUrn: String, context: AssetUploadContext) {
        val confirmUrl = "${context.apiBaseUrl}/rest/images/${encodeUrn(assetUrn)}?action=checkStatus"
        val response = httpTransport.send(
            HttpRequest.newBuilder(URI.create(confirmUrl))
                .header("Authorization", "Bearer ${context.accessToken}")
                .header(CONTENT_TYPE, "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("LinkedIn-Version", context.apiVersion)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
        )

        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            throw ProviderUploadException(
                "LinkedIn asset confirmation failed: ${response.statusCode} ${response.body}"
            )
        }
    }

    private fun encodeUrn(urn: String): String = urn.replace(":", "%3A").replace("/", "%2F")

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

private fun <T> T?.orThrow(lazyMessage: () -> String): T =
    this ?: throw ProviderUploadException(lazyMessage())
