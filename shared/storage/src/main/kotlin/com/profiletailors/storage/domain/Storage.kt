package com.profiletailors.storage.domain

import kotlinx.coroutines.flow.Flow

/**
 * Core storage interface for object storage operations.
 *
 * This is the base contract for all storage providers. It defines the fundamental
 * operations: upload, download, delete, and list.
 *
 * For providers that support presigned URLs (S3, GCS, Azure Blob, R2, etc.),
 * use [PresignableStorage] instead, which extends this interface with presigned URL generation.
 *
 * Implementations:
 * - [com.profiletailors.storage.infrastructure.LocalFilesystemStorage]
 *
 * Example usage:
 * ```kotlin
 * class MyService(private val storage: Storage) {
 *     suspend fun saveFile(bucket: String, key: String, data: ByteArray) {
 *         storage.upload(bucket, key, flowOf(data))
 *     }
 * }
 * ```
 *
 * @see PresignableStorage for providers with presigned URL support
 */
interface Storage {
    /**
     * Uploads an object to storage.
     *
     * @param bucket The target bucket name
     * @param key The object key (path within the bucket)
     * @param content A flow of byte arrays representing the object content
     * @param metadata Optional metadata key-value pairs to store with the object
     * @throws StorageServiceException if the upload fails
     */
    suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String> = emptyMap()
    )

    /**
     * Downloads an object from storage as a flow of byte arrays.
     *
     * @param bucket The bucket name containing the object
     * @param key The object key
     * @return A flow emitting the object content in chunks
     * @throws StorageObjectNotFoundException if the object does not exist
     * @throws StorageServiceException if the download fails
     */
    fun download(bucket: String, key: String): Flow<ByteArray>

    /**
     * Deletes an object from storage.
     *
     * @param bucket The bucket name containing the object
     * @param key The object key
     * @throws StorageServiceException if the deletion fails
     */
    suspend fun delete(bucket: String, key: String)

    /**
     * Lists object keys within a bucket, optionally filtered by prefix.
     *
     * @param bucket The bucket name
     * @param prefix Only return keys starting with this prefix
     * @return List of object keys matching the criteria
     * @throws StorageServiceException if the listing fails
     */
    suspend fun list(bucket: String, prefix: String = ""): List<String>
}