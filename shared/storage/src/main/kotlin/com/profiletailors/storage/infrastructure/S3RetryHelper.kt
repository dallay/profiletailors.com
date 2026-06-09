package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.model.S3Exception
import kotlin.math.min

/**
 * Retry helper for S3-compatible operations.
 * Wraps operations with exponential backoff for transient failures (5xx, 429, 409).
 */
object S3RetryHelper {
    private val logger = LoggerFactory.getLogger(S3RetryHelper::class.java)

    private val transientStatusCodes = setOf(409, 429, 500, 502, 503, 504)

    /**
     * Executes an S3 operation with retry logic.
     * Retries on transient S3 errors (409 Conflict, 429 Rate Limit, 5xx Server Errors).
     */
    suspend fun <T> withRetry(operation: suspend () -> T): T {
        var lastException: Exception? = null
        val maxAttempts = 3
        val baseDelayMs = 100L

        for (attempt in 1..maxAttempts) {
            try {
                return operation()
            } catch (e: S3Exception) {
                lastException = e
                val statusCode = e.statusCode()
                if (statusCode != null && statusCode in transientStatusCodes && attempt < maxAttempts) {
                    val delayMs = min(baseDelayMs * (1 shl (attempt - 1)), 2000L)
                    logger.warn("Transient S3 error ($statusCode), retrying in ${delayMs}ms (attempt $attempt/$maxAttempts): ${e.message}")
                    delay(delayMs)
                } else {
                    throw StorageServiceException("S3 operation failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException?.let { StorageServiceException("S3 operation failed after $maxAttempts attempts", it) }
            ?: throw StorageServiceException("S3 operation failed")
    }
}