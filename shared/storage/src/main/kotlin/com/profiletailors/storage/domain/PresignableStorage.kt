package com.profiletailors.storage.domain

/**
 * Storage interface with presigned URL generation capability.
 *
 * This interface extends [Storage] to add presigned URL generation, which is only
 * applicable for cloud object storage providers (S3, GCS, Azure Blob, R2, etc.)
 * that support time-limited, signed URLs for private object access.
 *
 * Implementations:
 * - [com.profiletailors.storage.infrastructure.S3Storage]
 * - [com.profiletailors.storage.infrastructure.S2Storage] (Cloudflare R2)
 *
 * Note: [com.profiletailors.storage.infrastructure.LocalFilesystemStorage] does NOT
 * implement this interface because local filesystem access does not have a presigned
 * URL concept. Use [Storage] directly for local storage operations.
 *
 * @see Storage for the base contract without presigning
 */
interface PresignableStorage : Storage {

    /**
     * Generates a presigned URL for downloading an object.
     *
     * The presigned URL grants temporary, authenticated access to a private object
     * without requiring the caller to have AWS credentials.
     *
     * @param bucket The bucket name containing the object
     * @param key The object key
     * @param expirySeconds How long the URL should be valid (provider-specific limits apply)
     * @return A presigned URL string that can be used to access the object directly
     * @throws StorageServiceException if URL generation fails
     * @throws StorageObjectNotFoundException if the object does not exist (if provider validates)
     */
    suspend fun presignGet(bucket: String, key: String, expirySeconds: Long = 300): String
}
