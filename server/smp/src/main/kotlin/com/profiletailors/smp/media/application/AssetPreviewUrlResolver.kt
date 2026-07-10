package com.profiletailors.smp.media.application

import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.PresignableStorage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun interface AssetPreviewUrlResolver {
    suspend fun resolvePreviewUrl(
        assetId: String,
        workspaceId: String,
        mediaType: String,
        storageKey: String?,
        externalUrl: String?,
    ): String?
}

class MediaPreviewTokenService(private val signingSecret: String, private val previewUrlExpirySeconds: Long) {
    fun buildSignedPreviewPath(assetId: String, workspaceId: String): String {
        val expiresAt = Instant.now().epochSecond + previewUrlExpirySeconds
        val signature = sign(assetId, workspaceId, expiresAt)
        return "/api/media/assets/$assetId/preview?workspaceId=$workspaceId&expiresAt=$expiresAt&signature=$signature"
    }

    fun buildSignedContentPath(assetId: String, workspaceId: String): String {
        val expiresAt = Instant.now().epochSecond + previewUrlExpirySeconds
        val signature = sign(assetId, workspaceId, expiresAt)
        return "/api/media/assets/$assetId/content?workspaceId=$workspaceId&expiresAt=$expiresAt&signature=$signature"
    }

    fun isValid(assetId: String, workspaceId: String, expiresAt: Long, signature: String): Boolean {
        if (Instant.now().epochSecond > expiresAt) return false
        return constantTimeEquals(signature, sign(assetId, workspaceId, expiresAt))
    }

    private fun sign(assetId: String, workspaceId: String, expiresAt: Long): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        val payload = "$assetId:$workspaceId:$expiresAt".toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload))
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var diff = 0
        for (index in left.indices) {
            diff = diff or (left[index].code xor right[index].code)
        }
        return diff == 0
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}

class StorageAssetPreviewUrlResolver(
    private val bucketRegistry: BucketRegistry,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
    private val storageBucket: String,
    private val previewUrlExpirySeconds: Long,
) : AssetPreviewUrlResolver {
    private val logger = LoggerFactory.getLogger(StorageAssetPreviewUrlResolver::class.java)

    /**
     * Convenience constructor that resolves its dependencies from the
     * shared [AttachmentsStorageBinding]. This is the constructor wired by
     * Spring Boot — the underlying `[storageBucket]` route stays in a single
     * place.
     */
    constructor(
        binding: com.profiletailors.storage.domain.AttachmentsStorageBinding,
        mediaPreviewTokenService: MediaPreviewTokenService,
        previewUrlExpirySeconds: Long,
    ) : this(
        bucketRegistry = BucketRegistry { binding.storage },
        mediaPreviewTokenService = mediaPreviewTokenService,
        storageBucket = binding.bucketName,
        previewUrlExpirySeconds = previewUrlExpirySeconds,
    )

    override suspend fun resolvePreviewUrl(
        assetId: String,
        workspaceId: String,
        mediaType: String,
        storageKey: String?,
        externalUrl: String?,
    ): String? {
        val isImageType = mediaType.startsWith("image/", ignoreCase = true)
        val resolvedStorageKey = storageKey?.takeUnless { it.isBlank() }

        val previewUrl = when {
            !isImageType -> null
            !externalUrl.isNullOrBlank() -> externalUrl
            resolvedStorageKey == null -> null
            else -> resolveStoredPreviewUrl(assetId, workspaceId, resolvedStorageKey)
        }

        return previewUrl
    }

    private suspend fun resolveStoredPreviewUrl(assetId: String, workspaceId: String, storageKey: String): String? {
        val storage = bucketRegistry.getStorage(storageBucket)
        if (storage is PresignableStorage) {
            val presigned = runCatching {
                storage.presignGet(
                    bucket = storageBucket,
                    key = storageKey,
                    expirySeconds = previewUrlExpirySeconds,
                )
            }.onFailure { err ->
                logger.warn(
                    "Failed to generate presigned preview URL for assetId={} storageKey={}: {}",
                    assetId,
                    storageKey,
                    err.message,
                )
            }.getOrNull()
            if (presigned != null) return presigned
        }

        logger.debug("Falling back to signed local preview endpoint for assetId={}", assetId)
        return mediaPreviewTokenService.buildSignedPreviewPath(assetId, workspaceId)
    }
}
