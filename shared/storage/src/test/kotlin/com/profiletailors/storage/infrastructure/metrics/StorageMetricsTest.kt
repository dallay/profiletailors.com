package com.profiletailors.storage.infrastructure.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@Suppress("UnsafeCallOnNullableType")
class StorageMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metrics: StorageMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        metrics = StorageMetrics(meterRegistry)
    }

    // ── recordOperation ─────────────────────────────────────────────────

    @Nested
    inner class RecordOperation {

        @Test
        fun `increments counter with success=true tag`() {
            metrics.recordOperation("upload", "local", "my-bucket", success = true)

            val counter = meterRegistry.find("storage.operations.total")
                .tag("operation", "upload")
                .tag("provider", "local")
                .tag("bucket", "my-bucket")
                .tag("result", "success")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `increments counter with success=false tag`() {
            metrics.recordOperation("delete", "s3", "backup-bucket", success = false)

            val counter = meterRegistry.find("storage.operations.total")
                .tag("operation", "delete")
                .tag("provider", "s3")
                .tag("bucket", "backup-bucket")
                .tag("result", "failure")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `accumulates multiple calls`() {
            metrics.recordOperation("upload", "local", "b1", success = true)
            metrics.recordOperation("upload", "local", "b1", success = true)
            metrics.recordOperation("download", "local", "b1", success = true)

            val uploadCounter = meterRegistry.find("storage.operations.total")
                .tag("operation", "upload")
                .tag("provider", "local")
                .tag("bucket", "b1")
                .tag("result", "success")
                .counter()

            val downloadCounter = meterRegistry.find("storage.operations.total")
                .tag("operation", "download")
                .tag("provider", "local")
                .tag("bucket", "b1")
                .tag("result", "success")
                .counter()

            assertThat(uploadCounter).isNotNull
            assertThat(uploadCounter!!.count()).isEqualTo(2.0)
            assertThat(downloadCounter).isNotNull
            assertThat(downloadCounter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `differentiates operations with different tag combinations`() {
            metrics.recordOperation("upload", "s3", "bucket-a", success = true)
            metrics.recordOperation("upload", "s3", "bucket-b", success = true)

            val counterA = meterRegistry.find("storage.operations.total")
                .tag("bucket", "bucket-a")
                .counter()
            val counterB = meterRegistry.find("storage.operations.total")
                .tag("bucket", "bucket-b")
                .counter()

            assertThat(counterA).isNotNull
            assertThat(counterA!!.count()).isEqualTo(1.0)
            assertThat(counterB).isNotNull
            assertThat(counterB!!.count()).isEqualTo(1.0)
        }
    }

    // ── recordBytesUploaded ─────────────────────────────────────────────

    @Nested
    inner class RecordBytesUploaded {

        @Test
        fun `records uploaded bytes with correct tags`() {
            metrics.recordBytesUploaded(1024L, "s3", "upload-bucket")

            val counter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("provider", "s3")
                .tag("bucket", "upload-bucket")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1024.0)
        }

        @Test
        fun `accumulates multiple uploads`() {
            metrics.recordBytesUploaded(500L, "local", "b1")
            metrics.recordBytesUploaded(300L, "local", "b1")

            val counter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("provider", "local")
                .tag("bucket", "b1")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(800.0)
        }

        @Test
        fun `different providers produce separate counters`() {
            metrics.recordBytesUploaded(100L, "s3", "shared-bucket")
            metrics.recordBytesUploaded(200L, "local", "shared-bucket")

            val s3Counter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("provider", "s3")
                .tag("bucket", "shared-bucket")
                .counter()
            val localCounter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("provider", "local")
                .tag("bucket", "shared-bucket")
                .counter()

            assertThat(s3Counter).isNotNull
            assertThat(s3Counter!!.count()).isEqualTo(100.0)
            assertThat(localCounter).isNotNull
            assertThat(localCounter!!.count()).isEqualTo(200.0)
        }

        @Test
        fun `handles zero bytes`() {
            metrics.recordBytesUploaded(0L, "s3", "b1")

            val counter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("provider", "s3")
                .tag("bucket", "b1")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(0.0)
        }
    }

    // ── recordBytesDownloaded ───────────────────────────────────────────

    @Nested
    inner class RecordBytesDownloaded {

        @Test
        fun `records downloaded bytes with correct tags`() {
            metrics.recordBytesDownloaded(4096L, "local", "dl-bucket")

            val counter = meterRegistry.find("storage.bytes.downloaded.total")
                .tag("provider", "local")
                .tag("bucket", "dl-bucket")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(4096.0)
        }

        @Test
        fun `accumulates multiple downloads`() {
            metrics.recordBytesDownloaded(100L, "s3", "b1")
            metrics.recordBytesDownloaded(250L, "s3", "b1")

            val counter = meterRegistry.find("storage.bytes.downloaded.total")
                .tag("provider", "s3")
                .tag("bucket", "b1")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(350.0)
        }

        @Test
        fun `separate counters per bucket`() {
            metrics.recordBytesDownloaded(10L, "s3", "alpha")
            metrics.recordBytesDownloaded(20L, "s3", "beta")

            val alpha = meterRegistry.find("storage.bytes.downloaded.total")
                .tag("bucket", "alpha")
                .counter()
            val beta = meterRegistry.find("storage.bytes.downloaded.total")
                .tag("bucket", "beta")
                .counter()

            assertThat(alpha).isNotNull
            assertThat(alpha!!.count()).isEqualTo(10.0)
            assertThat(beta).isNotNull
            assertThat(beta!!.count()).isEqualTo(20.0)
        }
    }

    // ── recordOperationLatency ──────────────────────────────────────────

    @Nested
    inner class RecordOperationLatency {

        @Test
        fun `records latency timer with correct tags`() {
            val durationNanos = 5_000_000L // 5ms

            metrics.recordOperationLatency("upload", "s3", durationNanos)

            val timer = meterRegistry.find("storage.operation.time")
                .tag("operation", "upload")
                .tag("provider", "s3")
                .timer()

            assertThat(timer).isNotNull
            assertThat(timer!!.count()).isEqualTo(1L)
        }

        @Test
        fun `records multiple latency samples`() {
            metrics.recordOperationLatency("download", "local", 1_000_000L)
            metrics.recordOperationLatency("download", "local", 3_000_000L)

            val timer = meterRegistry.find("storage.operation.time")
                .tag("operation", "download")
                .tag("provider", "local")
                .timer()

            assertThat(timer).isNotNull
            assertThat(timer!!.count()).isEqualTo(2L)
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0.0)
        }

        @Test
        fun `different operations produce separate timers`() {
            metrics.recordOperationLatency("upload", "s3", 1_000_000L)
            metrics.recordOperationLatency("delete", "s3", 2_000_000L)

            val uploadTimer = meterRegistry.find("storage.operation.time")
                .tag("operation", "upload")
                .timer()
            val deleteTimer = meterRegistry.find("storage.operation.time")
                .tag("operation", "delete")
                .timer()

            assertThat(uploadTimer).isNotNull
            assertThat(uploadTimer!!.count()).isEqualTo(1L)
            assertThat(deleteTimer).isNotNull
            assertThat(deleteTimer!!.count()).isEqualTo(1L)
        }
    }

    // ── recordError ─────────────────────────────────────────────────────

    @Nested
    inner class RecordError {

        @Test
        fun `records error counter with correct tags`() {
            metrics.recordError("upload", "s3", "err-bucket", "not_found")

            val counter = meterRegistry.find("storage.errors.total")
                .tag("operation", "upload")
                .tag("provider", "s3")
                .tag("bucket", "err-bucket")
                .tag("error_type", "not_found")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `accumulates errors of same type`() {
            metrics.recordError("download", "local", "b1", "timeout")
            metrics.recordError("download", "local", "b1", "timeout")

            val counter = meterRegistry.find("storage.errors.total")
                .tag("operation", "download")
                .tag("provider", "local")
                .tag("bucket", "b1")
                .tag("error_type", "timeout")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(2.0)
        }

        @Test
        fun `different error types produce separate counters`() {
            metrics.recordError("upload", "s3", "b1", "security")
            metrics.recordError("upload", "s3", "b1", "service")

            val securityCounter = meterRegistry.find("storage.errors.total")
                .tag("error_type", "security")
                .counter()
            val serviceCounter = meterRegistry.find("storage.errors.total")
                .tag("error_type", "service")
                .counter()

            assertThat(securityCounter).isNotNull
            assertThat(securityCounter!!.count()).isEqualTo(1.0)
            assertThat(serviceCounter).isNotNull
            assertThat(serviceCounter!!.count()).isEqualTo(1.0)
        }
    }

    // ── recordPresignedUrlGenerated ─────────────────────────────────────

    @Nested
    inner class RecordPresignedUrlGenerated {

        @Test
        fun `records presigned URL generation success`() {
            metrics.recordPresignedUrlGenerated("s3", success = true)

            val counter = meterRegistry.find("storage.presigned.urls.generated")
                .tag("provider", "s3")
                .tag("result", "success")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `records presigned URL generation failure`() {
            metrics.recordPresignedUrlGenerated("local", success = false)

            val counter = meterRegistry.find("storage.presigned.urls.generated")
                .tag("provider", "local")
                .tag("result", "failure")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `accumulates presigned URL generations`() {
            metrics.recordPresignedUrlGenerated("s3", success = true)
            metrics.recordPresignedUrlGenerated("s3", success = true)
            metrics.recordPresignedUrlGenerated("s3", success = false)

            val successCounter = meterRegistry.find("storage.presigned.urls.generated")
                .tag("result", "success")
                .counter()
            val failureCounter = meterRegistry.find("storage.presigned.urls.generated")
                .tag("result", "failure")
                .counter()

            assertThat(successCounter).isNotNull
            assertThat(successCounter!!.count()).isEqualTo(2.0)
            assertThat(failureCounter).isNotNull
            assertThat(failureCounter!!.count()).isEqualTo(1.0)
        }
    }

    // ── recordOperationTime ─────────────────────────────────────────────

    @Nested
    inner class RecordOperationTime {

        @Test
        fun `returns action result`() = runTest {
            val result = metrics.recordOperationTime("upload", "local") {
                "file.txt"
            }

            assertThat(result).isEqualTo("file.txt")
        }

        @Test
        fun `records timer on successful action`() = runTest {
            metrics.recordOperationTime("download", "s3") {
                42
            }

            val timer = meterRegistry.find("storage.operation.time")
                .tag("operation", "download")
                .tag("provider", "s3")
                .timer()

            assertThat(timer).isNotNull
            assertThat(timer!!.count()).isEqualTo(1L)
        }

        @Test
        fun `records timer even when action throws`() = runTest {
            val exception = runCatching {
                metrics.recordOperationTime("delete", "s3") {
                    throw IllegalStateException("disk full")
                }
            }.exceptionOrNull()

            assertThat(exception).isNotNull

            val timer = meterRegistry.find("storage.operation.time")
                .tag("operation", "delete")
                .tag("provider", "s3")
                .timer()

            assertThat(timer).isNotNull
            assertThat(timer!!.count()).isEqualTo(1L)
        }

        @Test
        fun `accumulates multiple invocations`() = runTest {
            metrics.recordOperationTime("upload", "local") { "a" }
            metrics.recordOperationTime("upload", "local") { "b" }

            val timer = meterRegistry.find("storage.operation.time")
                .tag("operation", "upload")
                .tag("provider", "local")
                .timer()

            assertThat(timer).isNotNull
            assertThat(timer!!.count()).isEqualTo(2L)
        }

        @Test
        fun `different operations produce separate timers`() = runTest {
            metrics.recordOperationTime("upload", "s3") { 1 }
            metrics.recordOperationTime("list", "s3") { 2 }

            val uploadTimer = meterRegistry.find("storage.operation.time")
                .tag("operation", "upload")
                .timer()
            val listTimer = meterRegistry.find("storage.operation.time")
                .tag("operation", "list")
                .timer()

            assertThat(uploadTimer).isNotNull
            assertThat(uploadTimer!!.count()).isEqualTo(1L)
            assertThat(listTimer).isNotNull
            assertThat(listTimer!!.count()).isEqualTo(1L)
        }
    }

    // ── sanitizeBucketTag (indirect via bucket tags) ────────────────────

    @Nested
    inner class SanitizeBucketTag {

        @Test
        fun `bucket name at exactly 30 chars is used as-is`() {
            val bucketName = "a".repeat(30) // exactly 30
            metrics.recordOperation("upload", "local", bucketName, success = true)

            val counter = meterRegistry.find("storage.operations.total")
                .tag("bucket", bucketName)
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `bucket name under 30 chars is used as-is`() {
            val bucketName = "short-bucket"
            metrics.recordOperation("upload", "local", bucketName, success = true)

            val counter = meterRegistry.find("storage.operations.total")
                .tag("bucket", bucketName)
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `bucket name over 30 chars is truncated to 27 plus ellipsis`() {
            val longBucket = "a".repeat(40) // 40 chars
            metrics.recordOperation("upload", "local", longBucket, success = true)

            val sanitized = "a".repeat(27) + "..." // 30 chars total

            val counter = meterRegistry.find("storage.operations.total")
                .tag("bucket", sanitized)
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `bucket name at 31 chars triggers truncation`() {
            val bucket31 = "b".repeat(31)
            metrics.recordBytesUploaded(100L, "s3", bucket31)

            val sanitized = "b".repeat(27) + "..."

            val counter = meterRegistry.find("storage.bytes.uploaded.total")
                .tag("bucket", sanitized)
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(100.0)
        }

        @Test
        fun `truncated bucket names with different long names converge to same tag`() {
            val bucket1 = "x".repeat(27) + "AAAAA" // 32 chars, first 27 are "x"
            val bucket2 = "x".repeat(27) + "BBBBB" // 32 chars, first 27 are "x"
            metrics.recordError("upload", "s3", bucket1, "service")
            metrics.recordError("upload", "s3", bucket2, "service")

            val counter = meterRegistry.find("storage.errors.total")
                .tag("bucket", "x".repeat(27) + "...")
                .tag("error_type", "service")
                .counter()

            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(2.0)
        }
    }

    // ── Static objects ──────────────────────────────────────────────────

    @Nested
    class OperationsConstants {

        @Test
        fun `has correct operation values`() {
            assertThat(StorageMetrics.Operations.UPLOAD).isEqualTo("upload")
            assertThat(StorageMetrics.Operations.DOWNLOAD).isEqualTo("download")
            assertThat(StorageMetrics.Operations.DELETE).isEqualTo("delete")
            assertThat(StorageMetrics.Operations.LIST).isEqualTo("list")
            assertThat(StorageMetrics.Operations.PRESIGN).isEqualTo("presign")
        }
    }

    @Nested
    class ErrorTypesConstants {

        @Test
        fun `has correct error type values`() {
            assertThat(StorageMetrics.ErrorTypes.NOT_FOUND).isEqualTo("not_found")
            assertThat(StorageMetrics.ErrorTypes.SECURITY).isEqualTo("security")
            assertThat(StorageMetrics.ErrorTypes.SERVICE).isEqualTo("service")
            assertThat(StorageMetrics.ErrorTypes.TIMEOUT).isEqualTo("timeout")
            assertThat(StorageMetrics.ErrorTypes.RATE_LIMITED).isEqualTo("rate_limited")
        }
    }

    @Nested
    class ProvidersConstants {

        @Test
        fun `has correct provider values`() {
            assertThat(StorageMetrics.Providers.LOCAL).isEqualTo("local")
            assertThat(StorageMetrics.Providers.S3).isEqualTo("s3")
            assertThat(StorageMetrics.Providers.S2).isEqualTo("s2")
        }
    }

    // ── Integration: cross-method metric isolation ──────────────────────

    @Nested
    inner class MetricIsolation {

        @Test
        fun `different metric types are independent`() {
            metrics.recordOperation("upload", "s3", "b1", success = true)
            metrics.recordBytesUploaded(1024L, "s3", "b1")
            metrics.recordBytesDownloaded(2048L, "s3", "b1")
            metrics.recordError("upload", "s3", "b1", "timeout")
            metrics.recordPresignedUrlGenerated("s3", success = true)

            assertThat(
                meterRegistry.find("storage.operations.total")
                    .tag("operation", "upload")
                    .counter()?.count(),
            ).isEqualTo(1.0)
            assertThat(
                meterRegistry.find("storage.bytes.uploaded.total")
                    .tag("provider", "s3")
                    .counter()?.count(),
            ).isEqualTo(1024.0)
            assertThat(
                meterRegistry.find("storage.bytes.downloaded.total")
                    .tag("provider", "s3")
                    .counter()?.count(),
            ).isEqualTo(2048.0)
            assertThat(
                meterRegistry.find("storage.errors.total")
                    .tag("error_type", "timeout")
                    .counter()?.count(),
            ).isEqualTo(1.0)
            assertThat(
                meterRegistry.find("storage.presigned.urls.generated")
                    .tag("result", "success")
                    .counter()?.count(),
            ).isEqualTo(1.0)
        }

        @Test
        fun `meterRegistry starts empty`() {
            val freshRegistry = SimpleMeterRegistry()
            StorageMetrics(freshRegistry)

            assertThat(freshRegistry.meters.size).isEqualTo(0)
        }

        @Test
        fun `meter count grows as new metric combinations are recorded`() {
            metrics.recordOperation("upload", "s3", "b1", success = true)
            metrics.recordOperation("download", "s3", "b1", success = true)

            assertThat(meterRegistry.meters.size).isGreaterThan(0)
        }
    }
}
