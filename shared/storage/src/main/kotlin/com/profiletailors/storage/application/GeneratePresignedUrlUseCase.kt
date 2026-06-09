package com.profiletailors.storage.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.domain.PresignedUrlGeneratedEvent
import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageServiceException
import com.profiletailors.storage.infrastructure.metrics.StorageMetrics
import com.profiletailors.storage.domain.RateLimitExceededException
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimiter
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Use case for generating presigned URLs for cloud object storage.
 *
 * This use case requires a [PresignableStorage] implementation because presigned URLs
 * are only supported by cloud providers (S3, R2, GCS, Azure Blob, etc.) and not by
 * local filesystem storage.
 *
 * NOTE: This class is NOT annotated with @Service to avoid auto-wiring issues when
 * the BucketRegistry returns Storage (not PresignableStorage). Create instances
 * explicitly where needed, or add a @Bean method in your configuration for each
 * PresignableStorage provider.
 *
 * Enforces security constraints (max expiry), audits all URL generation, and records metrics.
 *
 * @param storage The presignable storage adapter (must implement [PresignableStorage])
 * @param eventPublisher Publisher for auditing URL generation events
 * @param metrics Storage metrics for recording presigned URL operations
 * @param maxExpirySeconds Maximum allowed expiry time for presigned URLs (default: 1 hour)
 * @param provider The storage provider name for metrics
 */
class GeneratePresignedUrlUseCase(
    private val storage: PresignableStorage,
    private val eventPublisher: EventPublisher<BaseDomainEvent>,
    private val metrics: StorageMetrics,
    private val rateLimiter: RateLimiter,
    private val maxExpirySeconds: Long = DEFAULT_MAX_EXPIRY_SECONDS,
    private val provider: String = StorageMetrics.Providers.S3
) {
    private val logger = LoggerFactory.getLogger(GeneratePresignedUrlUseCase::class.java)

    /**
     * Generates a presigned URL for downloading an object from storage.
     *
     * @param bucket The bucket name containing the object
     * @param key The object key
     * @param expirySeconds How long the URL should be valid (max: [maxExpirySeconds])
     * @param requesterId Identifier of the user/application requesting the URL (for auditing)
     * @return The presigned URL string
     * @throws IllegalArgumentException If expirySeconds is out of range
     * @throws StorageServiceException If there's an error generating the URL
     */
    suspend fun execute(
        bucket: String,
        key: String,
        expirySeconds: Long,
        requesterId: String
    ): String {
        validateExpiry(expirySeconds)

        // Rate limit check before generating URL
        val rateLimitResult = rateLimiter.consumeToken(requesterId)
        if (rateLimitResult is RateLimitResult.Denied) {
            metrics.recordPresignedUrlGenerated(provider, false)
            metrics.recordError(StorageMetrics.Operations.PRESIGN, provider, bucket, StorageMetrics.ErrorTypes.RATE_LIMITED)
            throw RateLimitExceededException(
                retryAfterSeconds = rateLimitResult.retryAfter.seconds,
                message = "Rate limit exceeded for presigned URL generation. Retry after ${rateLimitResult.retryAfter.seconds}s"
            )
        }

        val url = try {
            metrics.recordOperationTime(StorageMetrics.Operations.PRESIGN, provider) {
                storage.presignGet(bucket, key, expirySeconds)
            }
        } catch (e: IllegalArgumentException) {
            // Validation errors (e.g., bucket validation) should not be wrapped as service errors
            metrics.recordPresignedUrlGenerated(provider, false)
            metrics.recordError(StorageMetrics.Operations.PRESIGN, provider, bucket, StorageMetrics.ErrorTypes.SECURITY)
            throw e
        } catch (e: StorageObjectNotFoundException) {
            metrics.recordPresignedUrlGenerated(provider, false)
            metrics.recordError(StorageMetrics.Operations.PRESIGN, provider, bucket, StorageMetrics.ErrorTypes.NOT_FOUND)
            throw StorageServiceException(
                "Failed to generate presigned URL for '$key' in bucket '$bucket'", e
            )
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            metrics.recordPresignedUrlGenerated(provider, false)
            metrics.recordError(StorageMetrics.Operations.PRESIGN, provider, bucket, StorageMetrics.ErrorTypes.SERVICE)
            throw StorageServiceException(
                "Failed to generate presigned URL for '$key' in bucket '$bucket'", e
            )
        }

        metrics.recordPresignedUrlGenerated(provider, true)

        try {
            eventPublisher.publish(
                PresignedUrlGeneratedEvent(
                    bucket = bucket,
                    key = key,
                    expirySeconds = expirySeconds,
                    requesterId = requesterId,
                    timestamp = Instant.now(),
                    expiryTime = Instant.now().plusSeconds(expirySeconds)
                )
            )
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            logger.warn("Failed to publish PresignedUrlGeneratedEvent for bucket=$bucket, key=$key", e)
        }

        return url
    }

    /**
     * Generates a presigned URL using default expiry (1 hour).
     */
    suspend fun execute(
        bucket: String,
        key: String,
        requesterId: String
    ): String = execute(bucket, key, DEFAULT_MAX_EXPIRY_SECONDS, requesterId)

    private fun validateExpiry(expirySeconds: Long) {
        require(expirySeconds > 0) {
            "Expiry seconds must be positive, got $expirySeconds"
        }
        require(expirySeconds <= maxExpirySeconds) {
            "Expiry seconds ($expirySeconds) exceeds maximum allowed ($maxExpirySeconds)"
        }
    }

    companion object {
        private const val DEFAULT_MAX_EXPIRY_SECONDS = 3600L // 1 hour
    }
}
