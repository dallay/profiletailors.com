package com.profiletailors.storage

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.application.GeneratePresignedUrlUseCase
import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.RateLimitExceededException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageObservation
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimiter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.runBlocking

/**
 * Test implementation of StorageObservation that avoids infrastructure coupling.
 */
class TestStorageMetrics : StorageObservation {
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

/**
 * Mock [PresignableStorage] for testing [GeneratePresignedUrlUseCase].
 *
 * This mock allows us to test the use case without depending on a specific
 * cloud storage provider implementation.
 */
class MockPresignableStorage : PresignableStorage {
    private val storage = mutableMapOf<String, ByteArray>()

    override suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String>
    ) {
        val key_ = "$bucket:$key"
        val bytes = mutableListOf<ByteArray>()
        content.collect { bytes.add(it) }
        storage[key_] = if (bytes.isEmpty()) ByteArray(0) else bytes.reduce { acc, b -> acc + b }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> {
        val key_ = "$bucket:$key"
        val content = storage[key_]
            ?: throw StorageObjectNotFoundException(bucket, key)
        return kotlinx.coroutines.flow.flowOf(content)
    }

    override suspend fun delete(bucket: String, key: String) {
        storage.remove("$bucket:$key")
    }

    override suspend fun list(bucket: String, prefix: String): List<String> {
        val prefix_ = "$bucket:$prefix"
        return storage.keys
            .filter { it.startsWith(prefix_) }
            .map { it.removePrefix("$bucket:") }
    }

    override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String {
        val key_ = "$bucket:$key"
        if (!storage.containsKey(key_)) {
            throw StorageObjectNotFoundException(bucket, key)
        }
        return "https://mock-storage.example.com/$bucket/$key?expiry=$expirySeconds&signature=mock"
    }

    override suspend fun exists(bucket: String, key: String): Boolean {
        return storage.containsKey("$bucket:$key")
    }
}

/**
 * Mock [RateLimiter] that always allows requests (no rate limiting in tests).
 */
class MockRateLimiter : RateLimiter {
    override suspend fun consumeToken(identifier: String): RateLimitResult =
        RateLimitResult.Allowed(remainingTokens = 100, limitCapacity = 100, resetTime = java.time.Instant.now().plusSeconds(3600))

    override suspend fun consumeToken(identifier: String, strategy: com.profiletailors.ratelimit.domain.RateLimitStrategy): RateLimitResult =
        RateLimitResult.Allowed(remainingTokens = 100, limitCapacity = 100, resetTime = java.time.Instant.now().plusSeconds(3600))
}

/**
 * Mock [RateLimiter] that always denies with rate limit exceeded.
 */
class MockRateLimiterDenied : RateLimiter {
    override suspend fun consumeToken(identifier: String): RateLimitResult =
        RateLimitResult.Denied(retryAfter = java.time.Duration.ofSeconds(30), limitCapacity = 100, windowDuration = java.time.Duration.ofMinutes(1))

    override suspend fun consumeToken(identifier: String, strategy: com.profiletailors.ratelimit.domain.RateLimitStrategy): RateLimitResult =
        RateLimitResult.Denied(retryAfter = java.time.Duration.ofSeconds(30), limitCapacity = 100, windowDuration = java.time.Duration.ofMinutes(1))
}

class GeneratePresignedUrlUseCaseTest {

    private fun createMockEventPublisher(): EventPublisher<BaseDomainEvent> = object : EventPublisher<BaseDomainEvent> {
        override suspend fun publish(event: BaseDomainEvent) {}
    }

    @Test
    fun `generate presigned URL with valid expiry`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        val bucket = "test-bucket"
        val key = "test.txt"
        storage.upload(bucket, key, kotlinx.coroutines.flow.flowOf("test content".toByteArray()))

        val url = useCase.execute(bucket, key, 3600, "user-123")
        assertTrue(url.isNotEmpty())
        assertTrue(url.contains("https://"))
        assertTrue(url.contains("expiry=3600"))
    }

    @Test
    fun `reject presigned URL with expiry exceeding maximum`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", 7200, "user-123")
            }
        }
    }

    @Test
    fun `reject presigned URL with zero expiry`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", 0, "user-123")
            }
        }
    }

    @Test
    fun `reject presigned URL with negative expiry`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase.execute("bucket", "key", -100, "user-123")
            }
        }
    }

    @Test
    fun `generate presigned URL with default expiry`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        val bucket = "test-bucket"
        val key = "test.txt"
        storage.upload(bucket, key, kotlinx.coroutines.flow.flowOf("test content".toByteArray()))

        val url = useCase.execute(bucket, key, "user-123")
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `reject presigned URL for non-existent object`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiter(), maxExpirySeconds = 3600)

        val exception = assertThrows<Exception> {
            runBlocking {
                useCase.execute("non-existent-bucket", "non-existent-key", 3600, "user-123")
            }
        }
        // The use case wraps StorageObjectNotFoundException in StorageServiceException
        assertTrue(exception is com.profiletailors.storage.domain.StorageServiceException) {
            "Expected StorageServiceException but got ${exception::class.simpleName}"
        }
        assertTrue(exception.cause is StorageObjectNotFoundException) {
            "Expected StorageObjectNotFoundException as cause but got ${exception.cause?.let { it::class.simpleName }}"
        }
        assertTrue(exception.message?.contains("non-existent-key") == true)
    }

    @Test
    fun `throw RateLimitExceededException when rate limit is exceeded`() = runTest {
        val storage = MockPresignableStorage()
        val eventPublisher = createMockEventPublisher()
        val metrics = TestStorageMetrics()
        val useCase = GeneratePresignedUrlUseCase(storage, eventPublisher, metrics, MockRateLimiterDenied(), maxExpirySeconds = 3600)

        val exception = assertThrows<RateLimitExceededException> {
            runBlocking {
                useCase.execute("test-bucket", "test-key", 3600, "user-123")
            }
        }
        assertTrue(exception.retryAfterSeconds == 30L) {
            "Expected retryAfterSeconds of 30 but got ${exception.retryAfterSeconds}"
        }
    }
}
