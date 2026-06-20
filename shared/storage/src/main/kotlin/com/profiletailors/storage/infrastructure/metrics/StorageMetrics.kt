package com.profiletailors.storage.infrastructure.metrics

import com.profiletailors.storage.domain.StorageObservation
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

private const val OPERATION = "operation"
private const val PROVIDER = "provider"
private const val BUCKET = "bucket"

/**
 * Metrics collector for storage operations.
 * Tracks upload/download/delete counts, bytes transferred, latency, and error rates.
 *
 * Metrics exposed:
 * - storage.operations.total: Counter for all storage operations (tags: operation, provider, bucket, result)
 * - storage.bytes.uploaded.total: Counter for total bytes uploaded (tags: provider, bucket)
 * - storage.bytes.downloaded.total: Counter for total bytes downloaded (tags: provider, bucket)
 * - storage.operation.time: Timer for operation latency (tags: operation, provider)
 * - storage.errors.total: Counter for failed operations (tags: operation, provider, bucket, error_type)
 * - storage.presigned.urls.generated: Counter for presigned URL generations (tags: provider)
 *
 * All metrics are tagged with provider and bucket for fine-grained observability.
 *
 * @property meterRegistry Micrometer meter registry for metric registration
 */
@Component
open class StorageMetrics(private val meterRegistry: MeterRegistry) : StorageObservation {

    /**
     * Records a storage operation (upload, download, delete, list).
     *
     * @param operation The type of operation (upload, download, delete, list, presign)
     * @param provider The storage provider (local, s3, s2)
     * @param bucket The bucket name
     * @param success Whether the operation succeeded
     */
    override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) {
        val result = if (success) "success" else "failure"

        Counter.builder("storage.operations.total")
            .tag(OPERATION, operation)
            .tag(PROVIDER, provider)
            .tag(BUCKET, sanitizeBucketTag(bucket))
            .tag("result", result)
            .description("Total number of storage operations")
            .register(meterRegistry)
            .increment()
    }

    /**
     * Records bytes uploaded.
     *
     * @param bytes Number of bytes uploaded
     * @param provider The storage provider
     * @param bucket The bucket name
     */
    override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) {
        Counter.builder("storage.bytes.uploaded.total")
            .tag(PROVIDER, provider)
            .tag(BUCKET, sanitizeBucketTag(bucket))
            .description("Total bytes uploaded")
            .register(meterRegistry)
            .increment(bytes.toDouble())
    }

    /**
     * Records bytes downloaded.
     *
     * @param bytes Number of bytes downloaded
     * @param provider The storage provider
     * @param bucket The bucket name
     */
    override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) {
        Counter.builder("storage.bytes.downloaded.total")
            .tag(PROVIDER, provider)
            .tag(BUCKET, sanitizeBucketTag(bucket))
            .description("Total bytes downloaded")
            .register(meterRegistry)
            .increment(bytes.toDouble())
    }

    /**
     * Records operation latency.
     *
     * @param operation The type of operation
     * @param provider The storage provider
     * @param durationNanos Operation duration in nanoseconds
     */
    override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) {
        Timer.builder("storage.operation.time")
            .tag(OPERATION, operation)
            .tag(PROVIDER, provider)
            .description("Storage operation latency")
            .register(meterRegistry)
            .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS)
    }

    /**
     * Records a storage error.
     *
     * @param operation The type of operation that failed
     * @param provider The storage provider
     * @param bucket The bucket name
     * @param errorType Type of error (not_found, security, service, timeout)
     */
    override fun recordError(operation: String, provider: String, bucket: String, errorType: String) {
        Counter.builder("storage.errors.total")
            .tag(OPERATION, operation)
            .tag(PROVIDER, provider)
            .tag(BUCKET, sanitizeBucketTag(bucket))
            .tag("error_type", errorType)
            .description("Total number of storage errors")
            .register(meterRegistry)
            .increment()
    }

    /**
     * Records presigned URL generation.
     *
     * @param provider The storage provider
     * @param success Whether the URL was generated successfully
     */
    override fun recordPresignedUrlGenerated(provider: String, success: Boolean) {
        val result = if (success) "success" else "failure"

        Counter.builder("storage.presigned.urls.generated")
            .tag(PROVIDER, provider)
            .tag("result", result)
            .description("Total presigned URLs generated")
            .register(meterRegistry)
            .increment()
    }

    /**
     * Measures and records the latency of a storage operation.
     *
     * @param operation The type of operation
     * @param provider The storage provider
     * @param action The operation to measure
     * @return The result of the operation
     */
    override suspend fun <T : Any> recordOperationTime(
        operation: String,
        provider: String,
        action: suspend () -> T
    ): T {
        val timer = Timer.builder("storage.operation.time")
            .tag(OPERATION, operation)
            .tag(PROVIDER, provider)
            .description("Storage operation latency")
            .register(meterRegistry)

        val sample = Timer.start(meterRegistry)
        return try {
            action()
        } finally {
            sample.stop(timer)
        }
    }

    /**
     * Sanitizes bucket tag to prevent high cardinality issues.
     * Buckets are normalized to prevent explosion of metric time series.
     */
    private fun sanitizeBucketTag(bucket: String): String {
        // Truncate very long bucket names and normalize
        return if (bucket.length > 30) {
            bucket.take(27) + "..."
        } else {
            bucket
        }
    }

    /**
     * Operation types for storage metrics.
     */
    object Operations {
        const val UPLOAD = "upload"
        const val DOWNLOAD = "download"
        const val DELETE = "delete"
        const val LIST = "list"
        const val PRESIGN = "presign"
    }

    /**
     * Error types for storage metrics.
     */
    object ErrorTypes {
        const val NOT_FOUND = "not_found"
        const val SECURITY = "security"
        const val SERVICE = "service"
        const val TIMEOUT = "timeout"
        const val RATE_LIMITED = "rate_limited"
    }

    /**
     * Storage providers for metrics.
     */
    object Providers {
        const val LOCAL = "local"
        const val S3 = "s3"
        const val S2 = "s2"
    }
}