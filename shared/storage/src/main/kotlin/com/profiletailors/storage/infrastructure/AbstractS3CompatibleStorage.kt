package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.StorageAccessDeniedException
import com.profiletailors.storage.domain.StorageConnectionException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.reactivestreams.Publisher
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.nio.ByteBuffer
import java.time.Duration

/**
 * Abstract base class for S3-compatible storage providers.
 * Contains all shared logic between S3Storage and R2StorageAdapter.
 *
 * Extracted to eliminate code duplication between the two adapters.
 * All cloud operations are wrapped with retry logic via [S3RetryHelper].
 */
abstract class AbstractS3CompatibleStorage(
    protected val client: S3AsyncClient,
    protected val bucketName: String,
    protected val presigner: S3Presigner,
    protected val timeoutSeconds: Long = 30
) : PresignableStorage {

    init {
        require(bucketName.isNotBlank()) { "bucketName cannot be blank" }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive, got $timeoutSeconds" }
    }

    protected fun validateBucket(bucket: String) {
        require(bucket == bucketName) {
            "Bucket mismatch: this storage instance is bound to bucket '$bucketName'. " +
            "Requested bucket '$bucket' is not supported."
        }
    }

    /**
     * Validates that a key does not contain path traversal sequences.
     * Rejects keys containing "../" or "..\\" to prevent security vulnerabilities.
     *
     * @param key The key to validate
     * @throws StorageSecurityException if the key contains path traversal sequences
     */
    protected fun validateKey(key: String) {
        require(!key.contains("../") && !key.contains("..\\")) {
            throw StorageSecurityException("Key contains path traversal sequence: '$key'")
        }
    }

    /**
     * Checks if an S3Exception represents a ServiceUnavailable (503) error.
     */
    private fun S3Exception.isServiceUnavailable(): Boolean =
        statusCode() == 503

    /**
     * Checks if an S3Exception represents an AccessDenied (403) error.
     * In AWS SDK v2 S3, AccessDenied is represented as S3Exception with statusCode 403.
     */
    private fun S3Exception.isAccessDenied(): Boolean =
        statusCode() == 403

    /**
     * Maps S3Exception or SdkException to the appropriate domain exception.
     */
    protected fun mapToStorageException(message: String, e: Exception): Nothing {
        when (e) {
            is S3Exception -> when {
                e.isAccessDenied() ->
                    throw StorageAccessDeniedException("$message: access denied")
                e.isServiceUnavailable() ->
                    throw StorageConnectionException("$message: service unavailable", e)
                else -> throw StorageServiceException(message, e)
            }
            is NoSuchKeyException ->
                throw StorageObjectNotFoundException(bucketName, message.substringAfter("'").substringBefore("'").takeIf { it.isNotEmpty() } ?: "unknown")
            else -> throw StorageServiceException(message, e)
        }
    }

    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        validateBucket(bucket)
        validateKey(key)
        // Materialize the Flow into a ByteArray so it can be safely retried.
        // For large files, this buffers in memory. Alternative: use a temp file.
        val bytes = mutableListOf<ByteArray>()
        content.collect { bytes.add(it) }
        val fullContent = if (bytes.isEmpty()) ByteArray(0) else bytes.reduce { acc, b -> acc + b }

        withTimeout(timeoutSeconds * 1000L) {
            S3RetryHelper.withRetry {
                try {
                    val request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .metadata(metadata)
                        .build()

                    val body = AsyncRequestBody.fromBytes(fullContent)
                    client.putObject(request, body).await()
                } catch (e: S3Exception) {
                    if (e.isAccessDenied()) {
                        throw StorageAccessDeniedException("Failed to upload '$key' to bucket '$bucketName'")
                    }
                    if (e.isServiceUnavailable()) {
                        throw StorageConnectionException("Failed to upload '$key' to bucket '$bucketName'", e)
                    }
                    throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
                } catch (e: SdkException) {
                    throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
                }
            }
        }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> = channelFlow {
        validateBucket(bucket)
        validateKey(key)
        // No retry wrapper here — Flow-based downloads can't safely retry
        // because partial emission means the stream has already progressed.
        // Callers handle transient errors via their own retry logic.
        withTimeout(timeoutSeconds * 1000L) {
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
                if (e.isAccessDenied()) {
                    throw StorageAccessDeniedException("Failed to download '$key' from bucket '$bucketName'")
                }
                if (e.isServiceUnavailable()) {
                    throw StorageConnectionException("Failed to download '$key' from bucket '$bucketName'", e)
                }
                throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
            } catch (e: SdkException) {
                throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
            }
        }
    }

    override suspend fun delete(bucket: String, key: String) {
        validateBucket(bucket)
        validateKey(key)
        withTimeout(timeoutSeconds * 1000L) {
            S3RetryHelper.withRetry {
                try {
                    val req = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build()
                    client.deleteObject(req).await()
                } catch (e: S3Exception) {
                    if (e.isAccessDenied()) {
                        throw StorageAccessDeniedException("Failed to delete '$key' from bucket '$bucketName'")
                    }
                    if (e.isServiceUnavailable()) {
                        throw StorageConnectionException("Failed to delete '$key' from bucket '$bucketName'", e)
                    }
                    throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
                } catch (e: SdkException) {
                    throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
                }
            }
        }
    }

    override suspend fun list(bucket: String, prefix: String): List<String> {
        validateBucket(bucket)
        if (prefix.isNotEmpty()) validateKey(prefix)
        return withTimeout(timeoutSeconds * 1000L) {
            S3RetryHelper.withRetry {
                try {
                    val results = mutableListOf<String>()
                    var isTruncated: Boolean
                    var continuationToken: String? = null

                    do {
                        val reqBuilder = ListObjectsV2Request.builder()
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

                    results
                } catch (e: S3Exception) {
                    if (e.isAccessDenied()) {
                        throw StorageAccessDeniedException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'")
                    }
                    if (e.isServiceUnavailable()) {
                        throw StorageConnectionException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'", e)
                    }
                    throw StorageServiceException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'", e)
                } catch (e: SdkException) {
                    throw StorageServiceException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'", e)
                }
            }
        }
    }

    override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String {
        validateBucket(bucket)
        validateKey(key)
        // Presigning is not retried as it doesn't have transient failures
        // Note: withTimeout is not applied here because presigning is a fast operation
        // and the AWS SDK handles its own timeouts. Timeout would require wrapping the
        // SdkFuture which is complex and low-value for presign operations.
        return try {
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build()

            presigner.presignGetObject(presignRequest).url().toString()
        } catch (e: S3Exception) {
            if (e.isAccessDenied()) {
                throw StorageAccessDeniedException("Failed to generate presigned URL for '$key' in bucket '$bucketName'")
            }
            if (e.isServiceUnavailable()) {
                throw StorageConnectionException("Failed to generate presigned URL for '$key' in bucket '$bucketName'", e)
            }
            throw StorageServiceException("Failed to generate presigned URL for '$key' in bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to generate presigned URL for '$key' in bucket '$bucketName'", e)
        }
    }

    override suspend fun exists(bucket: String, key: String): Boolean {
        validateBucket(bucket)
        validateKey(key)
        return withTimeout(timeoutSeconds * 1000L) {
            S3RetryHelper.withRetry {
                try {
                    val request = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build()
                    client.headObject(request).await()
                    true
                } catch (e: NoSuchKeyException) {
                    false
                } catch (e: S3Exception) {
                    if (e.isAccessDenied()) {
                        throw StorageAccessDeniedException("Failed to check existence of '$key' in bucket '$bucketName'")
                    }
                    if (e.isServiceUnavailable()) {
                        throw StorageConnectionException("Failed to check existence of '$key' in bucket '$bucketName'", e)
                    }
                    throw StorageServiceException("Failed to check existence of '$key' in bucket '$bucketName'", e)
                } catch (e: SdkException) {
                    throw StorageServiceException("Failed to check existence of '$key' in bucket '$bucketName'", e)
                }
            }
        }
    }
}
