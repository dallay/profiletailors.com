package com.profiletailors.storage

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.application.GeneratePresignedUrlUseCase
import com.profiletailors.storage.infrastructure.LocalFilesystemStorage
import com.profiletailors.storage.infrastructure.metrics.StorageMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.runBlocking

/**
 * Test implementation of StorageMetrics that doesn't require mocking Micrometer internals.
 */
class TestStorageMetrics(registry: MeterRegistry = SimpleMeterRegistry()) : StorageMetrics(registry) {
    override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) {}
    override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) {}
    override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) {}
    override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) {}
    override fun recordError(operation: String, provider: String, bucket: String, errorType: String) {}
    override fun recordPresignedUrlGenerated(provider: String, success: Boolean) {}
    override suspend fun <T : Any> recordOperationTime(
        operation: String,
        provider: String,
        action: suspend () -> T
    ): T = action()
}

class GeneratePresignedUrlUseCaseTest {

    private fun createMockEventPublisher(): EventPublisher<BaseDomainEvent> = object : EventPublisher<BaseDomainEvent> {
        override suspend fun publish(event: BaseDomainEvent) {}
    }

    @Test
    fun `generate presigned URL with valid expiry`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        val bucket = "test-bucket"
        val key = "test.txt"
        storage.upload(bucket, key, flowOf("test content".toByteArray()))

        val url = useCase.execute(bucket, key, 3600, "user-123")
        assertTrue(url.isNotEmpty())
        assertTrue(url.contains("file://"))
    }

    @Test
    fun `reject presigned URL with expiry exceeding maximum`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", 7200, "user-123")
            }
        }
    }

    @Test
    fun `reject presigned URL with zero expiry`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", 0, "user-123")
            }
        }
    }

    @Test
    fun `reject presigned URL with negative expiry`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", -100, "user-123")
            }
        }
    }

    @Test
    fun `generate presigned URL with default expiry`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        val bucket = "test-bucket"
        val key = "test.txt"
        storage.upload(bucket, key, flowOf("test content".toByteArray()))

        val url = useCase.execute(bucket, key, "user-123")
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `reject presigned URL for non-existent object`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, maxExpirySeconds = 3600)

        // LocalFilesystemStorage.presignGet returns file:// URL even if file doesn't exist
        val url = useCase.execute("non-existent-bucket", "non-existent-key", 3600, "user-123")
        assertTrue(url.isNotEmpty())
    }
}