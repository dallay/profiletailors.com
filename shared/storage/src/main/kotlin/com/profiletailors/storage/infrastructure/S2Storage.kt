package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.PresignableStorage
import kotlinx.coroutines.flow.Flow
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Cloudflare R2 (S2) storage adapter.
 *
 * R2 is S3-compatible, so this adapter delegates to [S3Storage] with the
 * appropriate R2 endpoint configuration. Uses composition over inheritance
 * to avoid tight coupling to S3Storage implementation details.
 *
 * The R2-specific configuration (endpoint, region) should be applied when
 * building the [S3AsyncClient] and [S3Presigner] instances passed to this class.
 *
 * Example configuration:
 * ```kotlin
 * val client = S3AsyncClient.builder()
 *     .region(Region.US_EAST_1)
 *     .endpointOverride(URI.create("https://xxx.r2.cloudflarestorage.com"))
 *     .credentialsProvider(StaticCredentialsProvider.create(
 *         AwsBasicCredentials.create(accessKeyId, secretAccessKey)
 *     ))
 *     .build()
 * ```
 *
 * @param client The R2-configured S3 async client
 * @param bucketName The default R2 bucket name
 * @param presigner The R2-configured S3 presigner
 * @param timeoutSeconds Timeout for operations in seconds
 */
class S2Storage(
    client: S3AsyncClient,
    bucketName: String,
    presigner: S3Presigner,
    timeoutSeconds: Long = 30
) : PresignableStorage {

    /**
     * Internal S3Storage delegate handling R2 operations.
     *
     * Using composition instead of inheritance allows R2 to diverge from S3
     * behavior (e.g., different throttling, different presign semantics) without
     * affecting this class.
     */
    private val delegate = S3Storage(client, bucketName, presigner, timeoutSeconds)

    override suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String>
    ) = delegate.upload(bucket, key, content, metadata)

    override fun download(bucket: String, key: String): Flow<ByteArray> =
        delegate.download(bucket, key)

    override suspend fun delete(bucket: String, key: String) =
        delegate.delete(bucket, key)

    override suspend fun list(bucket: String, prefix: String): List<String> =
        delegate.list(bucket, prefix)

    override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String =
        delegate.presignGet(bucket, key, expirySeconds)

    override suspend fun exists(bucket: String, key: String): Boolean =
        delegate.exists(bucket, key)
}
