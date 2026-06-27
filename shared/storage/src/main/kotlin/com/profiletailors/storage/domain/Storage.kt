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
        metadata: Map<String, String> = emptyMap(),
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

    /**
     * Checks if an object exists in storage.
     *
     * @param bucket The bucket name
     * @param key The object key
     * @return true if the object exists, false otherwise
     * @throws StorageServiceException if the check fails due to a service error
     */
    suspend fun exists(bucket: String, key: String): Boolean

    /**
     * Copies an object from a source key to a destination key within the same bucket.
     *
     * Used during the CAS upload finalization: the temp upload key is copied to the
     * canonical key after hash validation. The temp key is deleted after a successful copy.
     *
     * @param bucket The bucket name (same for source and destination)
     * @param sourceKey The source object key (e.g. temp upload key)
     * @param destKey The destination object key (e.g. canonical CAS key)
     * @throws StorageObjectNotFoundException if the source object does not exist
     * @throws StorageServiceException if the copy operation fails
     */
    suspend fun copyObject(bucket: String, sourceKey: String, destKey: String)
}
