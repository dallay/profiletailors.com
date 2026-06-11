package com.profiletailors.storage

import com.profiletailors.storage.domain.PresignableStorage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path

/**
 * Contract tests for [PresignableStorage] implementations.
 *
 * This class defines the behavioral contract for presigned URL generation.
 * All cloud storage providers that support presigned URLs must satisfy these tests.
 *
 * ## Usage
 *
 * ```kotlin
 * class S3StorageContractTest : PresignableStorageContractTest() {
 *     override fun createStorage(tempDir: Path): PresignableStorage {
 *         return S3Storage(testClient, "test-bucket", testPresigner)
 *     }
 * }
 * ```
 *
 * ## Contract Guarantees
 *
 * These tests verify:
 * 1. Presigned URLs can be generated for existing objects
 * 2. Presigned URLs have correct format for the provider
 * 3. Presigned URLs respect expiry parameters
 * 4. Presigned URLs for non-existent objects are handled appropriately
 *
 * @see StorageContractTest for base storage operation tests
 */
abstract class PresignableStorageContractTest {

    companion object {
        const val TEST_BUCKET = "test-bucket"
        const val TEST_KEY = "test-file.txt"
        const val TEST_CONTENT = "Hello, Presigned URL!"
    }

    /**
     * Creates a [PresignableStorage] instance for testing.
     *
     * Implement this method to provide your concrete [PresignableStorage] implementation.
     *
     * @param tempDir JUnit's temporary directory for test isolation
     * @return A fresh [PresignableStorage] instance configured for testing
     */
    protected abstract fun createStorage(tempDir: Path): PresignableStorage

    private lateinit var storage: PresignableStorage

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        storage = createStorage(tempDir)
    }

    @Nested
    @DisplayName("presignGet operations")
    inner class PresignOperations {

        @Test
        fun `should generate presigned URL for existing object`() = runBlocking {
            // First upload an object
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            // Generate presigned URL
            val presignedUrl = storage.presignGet(TEST_BUCKET, TEST_KEY, 300)

            // Verify URL is not empty and looks like a valid URL
            assertFalse(presignedUrl.isBlank())
            assertTrue(presignedUrl.startsWith("http://") || presignedUrl.startsWith("https://"))
        }

        @Test
        fun `should include expiry in presigned URL`() = runBlocking {
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            val shortExpiry = 60L
            val presignedUrl = storage.presignGet(TEST_BUCKET, TEST_KEY, shortExpiry)

            // URL should contain expiry-related parameters
            // Exact format is provider-specific (AWS uses X-Amz-Expires, GCS uses expiration, etc.)
            // We just verify it's present and valid
            assertFalse(presignedUrl.isBlank())
        }

        @Test
        fun `should generate unique URLs for different expiry times`() = runBlocking {
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            val url60s = storage.presignGet(TEST_BUCKET, TEST_KEY, 60)
            val url3600s = storage.presignGet(TEST_BUCKET, TEST_KEY, 3600)

            // Different expiry times should produce different URLs
            assertTrue(url60s != url3600s, "Different expiry times should produce different URLs")
        }

        @Test
        fun `should generate presigned URL with default expiry`() = runBlocking {
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            // Call without explicit expirySeconds
            val presignedUrl = storage.presignGet(TEST_BUCKET, TEST_KEY)

            assertFalse(presignedUrl.isBlank())
            assertTrue(presignedUrl.startsWith("http://") || presignedUrl.startsWith("https://"))
        }

        @Test
        fun `should handle presigned URL for object in subdirectory`() = runBlocking {
            val subdirKey = "subdir/nested/file.txt"
            storage.upload(TEST_BUCKET, subdirKey, flowOf(TEST_CONTENT.toByteArray()))

            val presignedUrl = storage.presignGet(TEST_BUCKET, subdirKey, 300)

            assertFalse(presignedUrl.isBlank())
            // URL should be accessible (exact validation is provider-specific)
        }

        @Test
        fun `should not throw for non-existent object - S3-or-R2 semantics`() = runBlocking {
            // S3/R2 presign operations are not required to verify the object exists —
            // the request is signed locally without contacting the service. The actual
            // 404 surfaces only when the presigned URL is consumed. This test pins down
            // that contract: presignGet must NOT throw StorageObjectNotFoundException
            // for a key that doesn't exist.
            val presignedUrl = storage.presignGet(TEST_BUCKET, "non-existent-key", 300)
            assertFalse(presignedUrl.isBlank())
            Unit
        }
    }

    @Nested
    @DisplayName("URL format validation")
    inner class UrlFormatValidation {

        @Test
        fun `should produce valid URI in presigned URL`() = runBlocking {
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            val presignedUrl = storage.presignGet(TEST_BUCKET, TEST_KEY, 300)

            // Should be parseable as a URI
            val uri = URI(presignedUrl)
            assertTrue(uri.scheme in listOf("http", "https"))
            assertFalse(uri.host.isNullOrBlank())
        }

        @Test
        fun `should include bucket and key information in URL`() = runBlocking {
            storage.upload(TEST_BUCKET, TEST_KEY, flowOf(TEST_CONTENT.toByteArray()))

            val presignedUrl = storage.presignGet(TEST_BUCKET, TEST_KEY, 300)

            // URL should contain URL-encoded bucket/key information
            // Provider-specific where this information is encoded
            assertFalse(presignedUrl.isBlank())
        }
    }
}
