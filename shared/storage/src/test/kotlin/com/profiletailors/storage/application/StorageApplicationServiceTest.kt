package com.profiletailors.storage.application

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.domain.FileDeletedEvent
import com.profiletailors.storage.domain.FileDownloadedEvent
import com.profiletailors.storage.domain.FileUploadedEvent
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageObservation
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// ─────────────────────────────────────────────────────────────
// Mock Implementations
// ─────────────────────────────────────────────────────────────

/**
 * In-memory [Storage] implementation for testing [StorageApplicationService].
 *
 * Uses a `"bucket:key"` string as internal key. Throws [StorageObjectNotFoundException]
 * when downloading, copying, or checking the existence of a missing key.
 * Delete is idempotent (no error on non-existent keys).
 */
open class MockStorage : Storage {
    protected val storage = mutableMapOf<String, ByteArray>()

    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        val internalKey = "$bucket:$key"
        val bytes = mutableListOf<ByteArray>()
        content.collect { bytes.add(it) }
        storage[internalKey] = if (bytes.isEmpty()) {
            ByteArray(0)
        } else {
            bytes.reduce { acc, b -> acc + b }
        }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> {
        val content = storage["$bucket:$key"]
            ?: throw StorageObjectNotFoundException(bucket, key)
        return flowOf(content)
    }

    override suspend fun delete(bucket: String, key: String) {
        storage.remove("$bucket:$key")
    }

    override suspend fun list(bucket: String, prefix: String): List<String> = storage.keys
        .filter { it.startsWith("$bucket:$prefix") }
        .map { it.removePrefix("$bucket:") }

    override suspend fun exists(bucket: String, key: String): Boolean = storage.containsKey("$bucket:$key")

    override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        val sourceInternal = "$bucket:$sourceKey"
        val data = storage[sourceInternal]
            ?: throw StorageObjectNotFoundException(bucket, sourceKey)
        storage["$bucket:$destKey"] = data
    }
}

/**
 * [EventPublisher] that captures all published events for assertions.
 *
 * Set [shouldThrowOnPublish] to `true` to simulate an infrastructure failure
 * in the event bus (the service should log the warning and continue).
 */
class MockEventPublisher : EventPublisher<BaseDomainEvent> {
    val publishedEvents = mutableListOf<BaseDomainEvent>()
    var shouldThrowOnPublish: Boolean = false

    override suspend fun publish(event: BaseDomainEvent) {
        if (shouldThrowOnPublish) {
            throw RuntimeException("Simulated event bus failure")
        }
        publishedEvents.add(event)
    }
}

/**
 * [StorageObservation] that records every call for metric-verification assertions.
 *
 * Every `record*` method appends a structured data-class entry to a public list so
 * tests can inspect operation order, arguments, and success/failure flags.
 */
class TrackingStorageMetrics : StorageObservation {
    data class OperationCall(val operation: String, val provider: String, val bucket: String, val success: Boolean)

    data class BytesCall(val bytes: Long, val provider: String, val bucket: String)

    data class LatencyCall(val operation: String, val provider: String, val durationNanos: Long)

    data class ErrorCall(val operation: String, val provider: String, val bucket: String, val errorType: String)

    data class PresignedCall(val provider: String, val success: Boolean)

    data class OperationTimeCall(val operation: String, val provider: String)

    val recordOperationCalls = mutableListOf<OperationCall>()
    val recordBytesUploadedCalls = mutableListOf<BytesCall>()
    val recordBytesDownloadedCalls = mutableListOf<BytesCall>()
    val recordOperationLatencyCalls = mutableListOf<LatencyCall>()
    val recordErrorCalls = mutableListOf<ErrorCall>()
    val recordPresignedUrlGeneratedCalls = mutableListOf<PresignedCall>()
    val recordOperationTimeCalls = mutableListOf<OperationTimeCall>()

    override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) {
        recordOperationCalls.add(OperationCall(operation, provider, bucket, success))
    }

    override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) {
        recordBytesUploadedCalls.add(BytesCall(bytes, provider, bucket))
    }

    override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) {
        recordBytesDownloadedCalls.add(BytesCall(bytes, provider, bucket))
    }

    override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) {
        recordOperationLatencyCalls.add(LatencyCall(operation, provider, durationNanos))
    }

    override fun recordError(operation: String, provider: String, bucket: String, errorType: String) {
        recordErrorCalls.add(ErrorCall(operation, provider, bucket, errorType))
    }

    override fun recordPresignedUrlGenerated(provider: String, success: Boolean) {
        recordPresignedUrlGeneratedCalls.add(PresignedCall(provider, success))
    }

    override suspend fun <T : Any> recordOperationTime(
        operation: String,
        provider: String,
        action: suspend () -> T,
    ): T {
        recordOperationTimeCalls.add(OperationTimeCall(operation, provider))
        return action()
    }

    /** Convenience: reset all recorded calls (useful between test scenarios). */
    fun reset() {
        recordOperationCalls.clear()
        recordBytesUploadedCalls.clear()
        recordBytesDownloadedCalls.clear()
        recordOperationLatencyCalls.clear()
        recordErrorCalls.clear()
        recordPresignedUrlGeneratedCalls.clear()
        recordOperationTimeCalls.clear()
    }
}

// ─────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────

@DisplayName("StorageApplicationService")
@Suppress("ClassOrdering")
internal class StorageApplicationServiceTest {

    companion object {
        private val BUCKET = "test-bucket"
        private val KEY = "test-file.txt"
        private val CONTENT = "Hello, World!".toByteArray()
        private val UPLOADER_ID = "user-uploader-1"
        private val DOWNLOADER_ID = "user-downloader-1"
        private val DELETER_ID = "user-deleter-1"
        private val PROVIDER = StorageObservation.Providers.LOCAL
    }

    private lateinit var storage: MockStorage
    private lateinit var eventPublisher: MockEventPublisher
    private lateinit var metrics: TrackingStorageMetrics
    private lateinit var service: StorageApplicationService

    @BeforeEach
    fun setUp() {
        storage = MockStorage()
        eventPublisher = MockEventPublisher()
        metrics = TrackingStorageMetrics()
        service = StorageApplicationService(storage, eventPublisher, metrics, PROVIDER)
    }

    // ─────────────────────────────────────────────────────────
    // upload()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload()")
    inner class Upload {

        @Test
        fun `should store content and record metrics on success`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            // Content persisted
            val stored = storage.download(BUCKET, KEY).toList()
            assertThat(stored).hasSize(1)
            assertThat(stored.first()).isEqualTo(CONTENT)

            // recordOperationTime called with UPLOAD
            assertThat(metrics.recordOperationTimeCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.UPLOAD)
                    assertThat(call.provider).isEqualTo(PROVIDER)
                }

            // recordBytesUploaded called with total byte count
            assertThat(metrics.recordBytesUploadedCalls)
                .anySatisfy { call ->
                    assertThat(call.bytes).isEqualTo(CONTENT.size.toLong())
                    assertThat(call.provider).isEqualTo(PROVIDER)
                    assertThat(call.bucket).isEqualTo(BUCKET)
                }

            // recordOperation called with success=true
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.UPLOAD)
                    assertThat(call.provider).isEqualTo(PROVIDER)
                    assertThat(call.bucket).isEqualTo(BUCKET)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should publish FileUploadedEvent on success`() = runTest {
            val metadata = mapOf("content-type" to "text/plain", "source" to "test")
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID, metadata)

            val event = eventPublisher.publishedEvents.filterIsInstance<FileUploadedEvent>()
            assertThat(event).hasSize(1)

            with(event.first()) {
                assertThat(bucket).isEqualTo(BUCKET)
                assertThat(key).isEqualTo(KEY)
                assertThat(sizeBytes).isEqualTo(CONTENT.size.toLong())
                assertThat(uploaderId).isEqualTo(UPLOADER_ID)
                assertThat(metadata).isEqualTo(metadata)
            }
        }

        @Test
        fun `should store content with empty metadata`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            val stored = storage.download(BUCKET, KEY).toList()
            assertThat(stored.first()).isEqualTo(CONTENT)
        }

        @Test
        fun `should reject path traversal in bucket`() = runTest {
            val maliciousBucket = "bucket/../etc"

            val thrown = runCatching {
                service.upload(maliciousBucket, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")

            // No event published (validation fails before event publishing)
            assertThat(eventPublisher.publishedEvents).isEmpty()

            // NOTE: validateBucketAndKey throws before the try-catch block,
            // so no metrics (recordError/recordOperation) are recorded
            // for path traversal violations.
        }

        @Test
        fun `should reject path traversal in key`() = runTest {
            val maliciousKey = "../../etc/passwd"

            val thrown = runCatching {
                service.upload(BUCKET, maliciousKey, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should propagate storage exception with SERVICE error type`() = runTest {
            val storageWithError = object : MockStorage() {
                override suspend fun upload(
                    bucket: String,
                    key: String,
                    content: Flow<ByteArray>,
                    metadata: Map<String, String>,
                ): Unit = throw StorageServiceException("Upload failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val thrown = runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageServiceException::class.java)
                .hasMessageContaining("Upload failed")

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should continue when event publishing fails`() = runTest {
            eventPublisher.shouldThrowOnPublish = true

            // Should NOT throw — event failure is logged as warning
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            // Content still persisted
            val stored = storage.download(BUCKET, KEY).toList()
            assertThat(stored.first()).isEqualTo(CONTENT)

            // Metrics still recorded success
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.success).isTrue
                }
        }
    }

    // ─────────────────────────────────────────────────────────
    // download()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("download()")
    inner class Download {

        @BeforeEach
        fun uploadContent() {
            runTest {
                storage.upload(BUCKET, KEY, flowOf(CONTENT))
            }
        }

        @Test
        fun `should return stored content and record metrics on success`() = runTest {
            val result = service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(CONTENT)

            // recordBytesDownloaded called
            assertThat(metrics.recordBytesDownloadedCalls)
                .anySatisfy { call ->
                    assertThat(call.bytes).isEqualTo(CONTENT.size.toLong())
                    assertThat(call.provider).isEqualTo(PROVIDER)
                    assertThat(call.bucket).isEqualTo(BUCKET)
                }

            // recordOperation called with success=true
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.DOWNLOAD)
                    assertThat(call.provider).isEqualTo(PROVIDER)
                    assertThat(call.bucket).isEqualTo(BUCKET)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should publish FileDownloadedEvent`() = runTest {
            service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            val event = eventPublisher.publishedEvents.filterIsInstance<FileDownloadedEvent>()
            assertThat(event).hasSize(1)

            with(event.first()) {
                assertThat(bucket).isEqualTo(BUCKET)
                assertThat(key).isEqualTo(KEY)
                assertThat(downloaderId).isEqualTo(DOWNLOADER_ID)
            }
        }

        @Test
        fun `should reject path traversal in bucket immediately`() {
            // download() is NOT a suspend function, so assertThatThrownBy works directly
            org.junit.jupiter.api.assertThrows<StorageSecurityException> {
                @Suppress("IgnoredReturnValue")
                service.download("bucket/../etc", KEY, DOWNLOADER_ID)
            }
        }

        @Test
        fun `should reject path traversal in key immediately`() {
            org.junit.jupiter.api.assertThrows<StorageSecurityException> {
                @Suppress("IgnoredReturnValue")
                service.download(BUCKET, "../../etc/passwd", DOWNLOADER_ID)
            }
        }

        @Test
        fun `should propagate storage exception and record error metrics when collecting`() = runTest {
            val storageWithError = object : MockStorage() {
                override fun download(bucket: String, key: String): Flow<ByteArray> =
                    throw StorageServiceException("Download failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val flow = failingService.download(BUCKET, KEY, DOWNLOADER_ID)

            val thrown = runCatching {
                flow.toList()
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageServiceException::class.java)
                .hasMessageContaining("Download failed")

            // Error metrics recorded
            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
        }

        @Test
        fun `should publish event even when download then fails`() = runTest {
            val storageWithError = object : MockStorage() {
                override fun download(bucket: String, key: String): Flow<ByteArray> =
                    throw StorageServiceException("Download failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val flow = failingService.download(BUCKET, KEY, DOWNLOADER_ID)
            runCatching { flow.toList() }

            // Event was still published (happens before download in the flow)
            assertThat(eventPublisher.publishedEvents)
                .anyMatch { it is FileDownloadedEvent }
        }

        @Test
        fun `should continue when event publishing fails during download`() = runTest {
            eventPublisher.shouldThrowOnPublish = true

            val result = service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            // Content still delivered
            assertThat(result.first()).isEqualTo(CONTENT)
        }

        @Test
        fun `should download content with zero bytes`() = runTest {
            storage.upload(BUCKET, "empty.txt", flowOf(ByteArray(0)))

            val result = service.download(BUCKET, "empty.txt", DOWNLOADER_ID).toList()

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEmpty()
        }
    }

    // ─────────────────────────────────────────────────────────
    // delete()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    inner class Delete {

        @BeforeEach
        fun uploadContent() {
            runTest {
                storage.upload(BUCKET, KEY, flowOf(CONTENT))
            }
        }

        @Test
        fun `should remove object and record metrics on success`() = runTest {
            assertThat(storage.exists(BUCKET, KEY)).isTrue

            service.delete(BUCKET, KEY, DELETER_ID)

            // Object removed
            assertThat(storage.exists(BUCKET, KEY)).isFalse

            // recordOperationTime called with DELETE
            assertThat(metrics.recordOperationTimeCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.DELETE)
                }

            // recordOperation called with success=true
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.DELETE)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should publish FileDeletedEvent on success`() = runTest {
            service.delete(BUCKET, KEY, DELETER_ID)

            val event = eventPublisher.publishedEvents.filterIsInstance<FileDeletedEvent>()
            assertThat(event).hasSize(1)

            with(event.first()) {
                assertThat(bucket).isEqualTo(BUCKET)
                assertThat(key).isEqualTo(KEY)
                assertThat(deleterId).isEqualTo(DELETER_ID)
            }
        }

        @Test
        fun `should reject path traversal in bucket`() = runTest {
            val thrown = runCatching {
                service.delete("bucket/../etc", KEY, DELETER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")

            // No event published (validation fails before event publishing)
            assertThat(eventPublisher.publishedEvents).isEmpty()

            // NOTE: validateBucketAndKey throws before the try-catch block,
            // so no metrics (recordError/recordOperation) are recorded
            // for path traversal violations.
        }

        @Test
        fun `should reject path traversal in key`() = runTest {
            val thrown = runCatching {
                service.delete(BUCKET, "../../etc/passwd", DELETER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should propagate storage exception with SERVICE error type`() = runTest {
            val storageWithError = object : MockStorage() {
                override suspend fun delete(bucket: String, key: String): Unit =
                    throw StorageServiceException("Delete failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val thrown = runCatching {
                failingService.delete(BUCKET, KEY, DELETER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageServiceException::class.java)
                .hasMessageContaining("Delete failed")

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should succeed when deleting non-existent object`() = runTest {
            // Delete is idempotent
            service.delete(BUCKET, "non-existent-key", DELETER_ID)

            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.DELETE)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should continue when event publishing fails during delete`() = runTest {
            eventPublisher.shouldThrowOnPublish = true

            service.delete(BUCKET, KEY, DELETER_ID)

            // Object still deleted
            assertThat(storage.exists(BUCKET, KEY)).isFalse
            // Metrics still record success
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isTrue }
        }
    }

    // ─────────────────────────────────────────────────────────
    // copyObject()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("copyObject()")
    inner class CopyObject {

        private val sourceKey = "source.txt"
        private val destKey = "dest.txt"

        @BeforeEach
        fun uploadSource() {
            runTest {
                storage.upload(BUCKET, sourceKey, flowOf(CONTENT))
            }
        }

        @Test
        fun `should copy object and record metrics on success`() = runTest {
            service.copyObject(BUCKET, sourceKey, destKey)

            // Both keys now exist
            assertThat(storage.exists(BUCKET, sourceKey)).isTrue
            assertThat(storage.exists(BUCKET, destKey)).isTrue

            // Content matches
            val sourceContent = storage.download(BUCKET, sourceKey).toList()
            val destContent = storage.download(BUCKET, destKey).toList()
            assertThat(destContent.first()).isEqualTo(sourceContent.first())

            // recordOperationTime called with COPY
            assertThat(metrics.recordOperationTimeCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.COPY)
                }

            // recordOperation called with success=true
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.COPY)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should reject path traversal in source key`() = runTest {
            val thrown = runCatching {
                service.copyObject(BUCKET, "../../etc/passwd", destKey)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")
        }

        @Test
        fun `should reject path traversal in destination key`() = runTest {
            val thrown = runCatching {
                service.copyObject(BUCKET, sourceKey, "../../etc/passwd")
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")
        }

        @Test
        fun `should reject path traversal in bucket`() = runTest {
            val thrown = runCatching {
                service.copyObject("bucket/../etc", sourceKey, destKey)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
                .hasMessageContaining("path traversal")
        }

        @Test
        fun `should propagate NOT_FOUND when source does not exist`() = runTest {
            val thrown = runCatching {
                service.copyObject(BUCKET, "non-existent-key", destKey)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageObjectNotFoundException::class.java)

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.NOT_FOUND)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
        }

        @Test
        fun `should propagate storage exception with SERVICE error type`() = runTest {
            val storageWithError = object : MockStorage() {
                override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String): Unit =
                    throw StorageServiceException("Copy failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val thrown = runCatching {
                failingService.copyObject(BUCKET, sourceKey, destKey)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageServiceException::class.java)
                .hasMessageContaining("Copy failed")

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
        }
    }

    // ─────────────────────────────────────────────────────────
    // list()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("list()")
    inner class ListObjects {

        @BeforeEach
        fun uploadFiles() {
            runTest {
                storage.upload(BUCKET, "file1.txt", flowOf("one".toByteArray()))
                storage.upload(BUCKET, "file2.txt", flowOf("two".toByteArray()))
                storage.upload(BUCKET, "subdir/file3.txt", flowOf("three".toByteArray()))
            }
        }

        @Test
        fun `should list all objects and record metrics`() = runTest {
            val keys = service.list(BUCKET)

            assertThat(keys).containsExactlyInAnyOrder("file1.txt", "file2.txt", "subdir/file3.txt")

            // recordOperationTime called with LIST
            assertThat(metrics.recordOperationTimeCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.LIST)
                }

            // recordOperation called with success=true
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.LIST)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should list objects matching prefix`() = runTest {
            val keys = service.list(BUCKET, "subdir/")

            assertThat(keys).containsExactly("subdir/file3.txt")
        }

        @Test
        fun `should return empty list for non-matching prefix`() = runTest {
            val keys = service.list(BUCKET, "nonexistent/")

            assertThat(keys).isEmpty()
        }

        @Test
        fun `should return empty list for empty bucket`() = runTest {
            val emptyBucket = "empty-bucket"

            val keys = service.list(emptyBucket)

            assertThat(keys).isEmpty()
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call ->
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.LIST)
                    assertThat(call.success).isTrue
                }
        }

        @Test
        fun `should propagate storage exception with SERVICE error type`() = runTest {
            val storageWithError = object : MockStorage() {
                override suspend fun list(bucket: String, prefix: String): List<String> =
                    throw StorageServiceException("List failed")
            }
            val failingService = StorageApplicationService(
                storageWithError,
                eventPublisher,
                metrics,
                PROVIDER,
            )

            val thrown = runCatching {
                failingService.list(BUCKET)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageServiceException::class.java)
                .hasMessageContaining("List failed")

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Error Classification
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("error classification")
    inner class ErrorClassification {

        /** Helper: create a service with a storage that always throws the given [exception]. */
        private fun serviceThatFailsWith(exception: Exception): StorageApplicationService {
            val failingStorage = object : Storage {
                override suspend fun upload(
                    bucket: String,
                    key: String,
                    content: Flow<ByteArray>,
                    metadata: Map<String, String>,
                ) = throw exception

                override fun download(bucket: String, key: String): Flow<ByteArray> = throw exception

                override suspend fun delete(bucket: String, key: String) = throw exception

                override suspend fun list(bucket: String, prefix: String): List<String> = throw exception

                override suspend fun exists(bucket: String, key: String): Boolean = throw exception

                override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) = throw exception
            }
            return StorageApplicationService(failingStorage, eventPublisher, metrics, PROVIDER)
        }

        @Test
        fun `should classify StorageSecurityException as SECURITY`() = runTest {
            val failingService = serviceThatFailsWith(StorageSecurityException("Bad bucket"))

            val thrown = runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(StorageSecurityException::class.java)

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SECURITY)
                }
        }

        @Test
        fun `should classify StorageObjectNotFoundException as NOT_FOUND`() = runTest {
            val failingService = serviceThatFailsWith(StorageObjectNotFoundException(BUCKET, KEY))

            val thrown = runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(StorageObjectNotFoundException::class.java)

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.NOT_FOUND)
                }
        }

        @Test
        fun `should classify generic exception as SERVICE`() = runTest {
            val failingService = serviceThatFailsWith(RuntimeException("Unexpected error"))

            val thrown = runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(RuntimeException::class.java)

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
        }

        @Test
        fun `should classify StorageServiceException as SERVICE`() = runTest {
            val failingService = serviceThatFailsWith(StorageServiceException("Service error"))

            val thrown = runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(StorageServiceException::class.java)

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SERVICE)
                }
        }

        /** Verify that ALL operation types use consistent error classification. */
        @Test
        fun `should classify SECURITY error across all operations`() = runTest {
            val exception = StorageSecurityException("traversal")
            val failingService = serviceThatFailsWith(exception)

            // Each operation that reaches the storage layer should get SECURITY error
            runCatching { failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID) }
            runCatching { failingService.delete(BUCKET, KEY, DELETER_ID) }
            runCatching { failingService.list(BUCKET) }
            runCatching { failingService.copyObject(BUCKET, KEY, "dest") }

            assertThat(metrics.recordErrorCalls)
                .allSatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SECURITY)
                }
            assertThat(metrics.recordErrorCalls).hasSize(4)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("metrics recording")
    inner class MetricsRecording {

        @Test
        fun `should record recordOperationTime with correct operation and provider`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            assertThat(metrics.recordOperationTimeCalls).isNotEmpty
            assertThat(metrics.recordOperationTimeCalls.first())
                .matches { it.operation == StorageObservation.Operations.UPLOAD }
                .matches { it.provider == PROVIDER }
        }

        @Test
        fun `should record recordBytesUploaded after successful upload`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            assertThat(metrics.recordBytesUploadedCalls).isNotEmpty
            assertThat(metrics.recordBytesUploadedCalls.first().bytes)
                .isEqualTo(CONTENT.size.toLong())
        }

        @Test
        fun `should record recordBytesDownloaded after successful download`() = runTest {
            storage.upload(BUCKET, KEY, flowOf(CONTENT))
            service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            assertThat(metrics.recordBytesDownloadedCalls).isNotEmpty
            assertThat(metrics.recordBytesDownloadedCalls.first().bytes)
                .isEqualTo(CONTENT.size.toLong())
        }

        @Test
        fun `should record recordOperation with success=true on success`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isTrue }
        }

        @Test
        fun `should record recordOperation with success=false on failure`() = runTest {
            val failingService = StorageApplicationService(
                object : MockStorage() {
                    override suspend fun upload(
                        bucket: String,
                        key: String,
                        content: Flow<ByteArray>,
                        metadata: Map<String, String>,
                    ) = throw StorageServiceException("fail")
                },
                eventPublisher,
                metrics,
                PROVIDER,
            )

            runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }

            assertThat(metrics.recordOperationCalls)
                .anySatisfy { call -> assertThat(call.success).isFalse }
        }

        @Test
        fun `should record recordError with correct error type on failure`() = runTest {
            val failingService = StorageApplicationService(
                object : MockStorage() {
                    override suspend fun upload(
                        bucket: String,
                        key: String,
                        content: Flow<ByteArray>,
                        metadata: Map<String, String>,
                    ) = throw StorageSecurityException("Bad bucket")
                },
                eventPublisher,
                metrics,
                PROVIDER,
            )

            runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }

            assertThat(metrics.recordErrorCalls)
                .anySatisfy { call ->
                    assertThat(call.errorType).isEqualTo(StorageObservation.ErrorTypes.SECURITY)
                    assertThat(call.operation).isEqualTo(StorageObservation.Operations.UPLOAD)
                    assertThat(call.bucket).isEqualTo(BUCKET)
                }
        }

        @Test
        fun `should call recordError and recordOperation for failed operations`() = runTest {
            val failingService = StorageApplicationService(
                object : MockStorage() {
                    override suspend fun upload(
                        bucket: String,
                        key: String,
                        content: Flow<ByteArray>,
                        metadata: Map<String, String>,
                    ) = throw StorageServiceException("fail")
                },
                eventPublisher,
                metrics,
                PROVIDER,
            )

            runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }

            // Both should have been called
            assertThat(metrics.recordErrorCalls).isNotEmpty
            assertThat(metrics.recordOperationCalls).isNotEmpty
        }
    }

    // ─────────────────────────────────────────────────────────
    // Event Publishing
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("event publishing")
    inner class EventPublishing {

        @Test
        fun `should publish FileUploadedEvent with correct details after upload`() = runTest {
            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            assertThat(eventPublisher.publishedEvents)
                .anyMatch { event ->
                    event is FileUploadedEvent &&
                        event.bucket == BUCKET &&
                        event.key == KEY &&
                        event.sizeBytes == CONTENT.size.toLong() &&
                        event.uploaderId == UPLOADER_ID
                }
        }

        @Test
        fun `should publish FileDownloadedEvent with correct details after download`() = runTest {
            storage.upload(BUCKET, KEY, flowOf(CONTENT))
            service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            assertThat(eventPublisher.publishedEvents)
                .anyMatch { event ->
                    event is FileDownloadedEvent &&
                        event.bucket == BUCKET &&
                        event.key == KEY &&
                        event.downloaderId == DOWNLOADER_ID
                }
        }

        @Test
        fun `should publish FileDeletedEvent with correct details after delete`() = runTest {
            storage.upload(BUCKET, KEY, flowOf(CONTENT))
            service.delete(BUCKET, KEY, DELETER_ID)

            assertThat(eventPublisher.publishedEvents)
                .anyMatch { event ->
                    event is FileDeletedEvent &&
                        event.bucket == BUCKET &&
                        event.key == KEY &&
                        event.deleterId == DELETER_ID
                }
        }

        @Test
        fun `should not publish any event when upload fails security validation`() = runTest {
            runCatching {
                service.upload("bucket/..", KEY, flowOf(CONTENT), UPLOADER_ID)
            }

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should not publish any event when delete fails security validation`() = runTest {
            runCatching {
                service.delete(BUCKET, "../../etc/passwd", DELETER_ID)
            }

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should not publish FileUploadedEvent when storage upload fails`() = runTest {
            val failingService = StorageApplicationService(
                object : MockStorage() {
                    override suspend fun upload(
                        bucket: String,
                        key: String,
                        content: Flow<ByteArray>,
                        metadata: Map<String, String>,
                    ) = throw StorageServiceException("fail")
                },
                eventPublisher,
                metrics,
                PROVIDER,
            )

            runCatching {
                failingService.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)
            }

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should not publish FileDeletedEvent when storage delete fails`() = runTest {
            val failingService = StorageApplicationService(
                object : MockStorage() {
                    override suspend fun delete(bucket: String, key: String): Unit =
                        throw StorageServiceException("fail")
                },
                eventPublisher,
                metrics,
                PROVIDER,
            )

            runCatching {
                failingService.delete(BUCKET, KEY, DELETER_ID)
            }

            assertThat(eventPublisher.publishedEvents).isEmpty()
        }

        @Test
        fun `should not fail the overall operation when event publishing fails for upload`() = runTest {
            eventPublisher.shouldThrowOnPublish = true

            service.upload(BUCKET, KEY, flowOf(CONTENT), UPLOADER_ID)

            // Content persisted despite event failure
            assertThat(storage.download(BUCKET, KEY).toList().first()).isEqualTo(CONTENT)
        }

        @Test
        fun `should not fail the overall operation when event publishing fails for download`() = runTest {
            storage.upload(BUCKET, KEY, flowOf(CONTENT))
            eventPublisher.shouldThrowOnPublish = true

            val result = service.download(BUCKET, KEY, DOWNLOADER_ID).toList()

            assertThat(result.first()).isEqualTo(CONTENT)
        }

        @Test
        fun `should not fail the overall operation when event publishing fails for delete`() = runTest {
            storage.upload(BUCKET, KEY, flowOf(CONTENT))
            eventPublisher.shouldThrowOnPublish = true

            service.delete(BUCKET, KEY, DELETER_ID)

            // Object still deleted
            assertThat(storage.exists(BUCKET, KEY)).isFalse
        }
    }

    // ─────────────────────────────────────────────────────────
    // Edge Cases
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    inner class EdgeCases {

        @Test
        fun `should handle empty content upload`() = runTest {
            service.upload(BUCKET, "empty.txt", flowOf(ByteArray(0)), UPLOADER_ID)

            val stored = storage.download(BUCKET, "empty.txt").toList()
            assertThat(stored.first()).isEmpty()
            assertThat(metrics.recordBytesUploadedCalls.first().bytes).isZero()
        }

        @Test
        fun `should handle large content in multiple chunks`() = runTest {
            val chunkCount = 10
            val chunkSize = 1024
            val chunks = List(chunkCount) { ByteArray(chunkSize) { idx -> idx.toByte() } }
            val flow = kotlinx.coroutines.flow.flow {
                chunks.forEach { emit(it) }
            }

            service.upload(BUCKET, "large.bin", flow, UPLOADER_ID)

            val stored = storage.download(BUCKET, "large.bin").toList()
            val totalSize = chunks.sumOf { it.size.toLong() }
            assertThat(stored.first().size.toLong()).isEqualTo(totalSize)
            assertThat(metrics.recordBytesUploadedCalls.first().bytes).isEqualTo(totalSize)
        }

        @Test
        fun `should handle special characters in keys`() = runTest {
            val specialKey = "my dir/file (1).txt"

            service.upload(BUCKET, specialKey, flowOf(CONTENT), UPLOADER_ID)

            val result = service.download(BUCKET, specialKey, DOWNLOADER_ID).toList()
            assertThat(result.first()).isEqualTo(CONTENT)
        }

        @Test
        fun `should handle multiple consecutive operations`() = runTest {
            service.upload(BUCKET, "a.txt", flowOf("alpha".toByteArray()), UPLOADER_ID)
            service.upload(BUCKET, "b.txt", flowOf("beta".toByteArray()), UPLOADER_ID)

            val listResult = service.list(BUCKET)
            assertThat(listResult).containsExactlyInAnyOrder("a.txt", "b.txt")

            val downloadA = service.download(BUCKET, "a.txt", DOWNLOADER_ID).toList()
            assertThat(String(downloadA.first())).isEqualTo("alpha")

            service.delete(BUCKET, "a.txt", DELETER_ID)
            assertThat(storage.exists(BUCKET, "a.txt")).isFalse
            assertThat(storage.exists(BUCKET, "b.txt")).isTrue
        }

        @Test
        fun `should validate bucket with double-dot inside but not at start`() = runTest {
            val thrown = runCatching {
                service.upload("bucket..name", KEY, flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
        }

        @Test
        fun `should validate key with double-dot multiple times`() = runTest {
            val thrown = runCatching {
                service.upload(BUCKET, "../../../etc/passwd", flowOf(CONTENT), UPLOADER_ID)
            }.exceptionOrNull()

            assertThat(thrown)
                .isInstanceOf(StorageSecurityException::class.java)
        }
    }
}
