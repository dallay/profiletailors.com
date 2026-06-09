package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.nio.ByteBuffer
import java.time.Duration

/**
 * S3-compatible object storage adapter.
 *
 * Supports AWS S3, MinIO, Cloudflare R2, and other S3-compatible providers.
 * Implements [PresignableStorage] to provide presigned URL generation.
 *
 * ## Bucket Model
 *
 * Each instance is bound to exactly one bucket (configured at construction time).
 * All operations validate that the requested bucket matches the configured bucket.
 * If a different bucket is requested, an [IllegalArgumentException] is thrown.
 * This prevents silent cross-bucket writes.
 *
 * ## Thread Safety
 *
 * This class is safe for concurrent use; the underlying S3 client handles concurrency.
 *
 * @param client The S3 async client configured for the provider
 * @param bucketName The bucket name this instance is bound to
 * @param presigner The S3 presigner for generating presigned URLs
 * @throws IllegalArgumentException if bucketName is blank
 */
open class S3Storage(
    private val client: S3AsyncClient,
    private val bucketName: String,
    private val presigner: S3Presigner
) : PresignableStorage {

    init {
        require(bucketName.isNotBlank()) {
            "bucketName cannot be blank"
        }
    }

    /**
     * Validates that the requested bucket matches the configured bucket.
     *
     * @throws IllegalArgumentException if bucket does not match the configured bucket
     */
    private fun validateBucket(bucket: String) {
        require(bucket == bucketName) {
            "Bucket mismatch: this storage instance is bound to bucket '$bucketName'. " +
            "Requested bucket '$bucket' is not supported. " +
            "Create a separate S3Storage instance for each bucket."
        }
    }

    override suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String>
    ) {
        validateBucket(bucket)
        try {
            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .build()

            val publisher: Publisher<ByteBuffer> = content
                .map { ByteBuffer.wrap(it) }
                .asPublisher()

            val body = AsyncRequestBody.fromPublisher(publisher)

            client.putObject(request, body).await()
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
        }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> = channelFlow {
        validateBucket(bucket)
        try {
            val request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val transformer = AsyncResponseTransformer.toPublisher<GetObjectResponse>()
            val future = client.getObject(request, transformer)

            val responsePublisher = future.await()

            responsePublisher.asFlow().collect { byteBuffer ->
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                send(bytes)
            }
        } catch (e: NoSuchKeyException) {
            throw StorageObjectNotFoundException(bucketName, key)
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
        }
    }

    override suspend fun delete(bucket: String, key: String) {
        validateBucket(bucket)
        try {
            val req = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            client.deleteObject(req).await()
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
        }
    }

    override suspend fun list(bucket: String, prefix: String): List<String> {
        validateBucket(bucket)
        try {
            val results = mutableListOf<String>()
            var isTruncated: Boolean
            var continuationToken: String? = null

            do {
                val reqBuilder =
                    ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)

                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken)
                }

                val resp = client.listObjectsV2(reqBuilder.build()).await()
                results.addAll(resp.contents().map { it.key() })
                isTruncated = resp.isTruncated
                continuationToken = resp.nextContinuationToken()
            } while (isTruncated)

            return results
        } catch (e: S3Exception) {
            throw StorageServiceException(
                "Failed to list objects in bucket '$bucketName' with prefix '$prefix'",
                e
            )
        } catch (e: SdkException) {
            throw StorageServiceException(
                "Failed to list objects in bucket '$bucketName' with prefix '$prefix'",
                e
            )
        }
    }

    override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String {
        validateBucket(bucket)
        try {
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build()

            val presigned = presigner.presignGetObject(presignRequest)
            return presigned.url().toString()
        } catch (e: S3Exception) {
            throw StorageServiceException(
                "Failed to generate presigned URL for '$key' in bucket '$bucketName'",
                e
            )
        } catch (e: SdkException) {
            throw StorageServiceException(
                "Failed to generate presigned URL for '$key' in bucket '$bucketName'",
                e
            )
        }
    }
}
