package com.profiletailors.storage

import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Abstract contract tests for [Storage] implementations.
 *
 * This class defines the behavioral contract that ALL [Storage] implementations
 * must satisfy. Each provider extends this class and implements [createStorage]
 * to provide their concrete [Storage] instance.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyStorageContractTest : StorageContractTest() {
 *     override fun createStorage(tempDir: Path): Storage {
 *         return MyStorageAdapter(tempDir)
 *     }
 * }
 * ```
 *
 * ## For PresignableStorage Providers
 *
 * If your provider implements [PresignableStorage], also extend [PresignableStorageContractTest]
 * to test presigned URL generation.
 *
 * ## Adding New Providers
 *
 * To add a new provider (e.g., MinIO, GCS, Azure Blob):
 * 1. Create your storage adapter implementing [Storage]
 * 2. Create a test class extending [StorageContractTest]
 * 3. Implement [createStorage] to return your adapter
 * 4. If your provider supports presigning, also extend [PresignableStorageContractTest]
 * 5. Run the tests - they define what "correct" means for your provider
 *
 * @see PresignableStorageContractTest for providers with presigned URL support
 */
abstract class StorageContractTest {

    companion object {
        const val TEST_BUCKET = "test-bucket"
        const val TEST_KEY = "test-file.txt"
        const val TEST_CONTENT = "Hello, Storage Contract!"
    }

    /**
     * Creates a [Storage] instance for testing.
     *
     * Implement this method in your test class to provide the concrete
     * [Storage] implementation you want to test.
     *
     * @param tempDir JUnit's temporary directory for test isolation
     * @return A fresh [Storage] instance configured for testing
     */
    protected abstract fun createStorage(tempDir: Path): Storage

    private lateinit var storage: Storage

    @BeforeEach
    fun setUp(tempDir: Path) {
        storage = createStorage(tempDir)
    }

    @Nested
    @DisplayName("upload operations")
    inner class UploadOperations {

        @Test
        fun `should upload content and return without error`() = runTest {
            val content = flowOf(TEST_CONTENT.toByteArray())

            storage.upload(TEST_BUCKET, TEST_KEY, content)

            // Upload succeeded without exception - that's the contract
        }

        @Test
        fun `should upload content with metadata`() = runTest {
            val metadata = mapOf("content-type" to "text/plain", "author" to "test")
            val content = flowOf(TEST_CONTENT.toByteArray())

            // Upload with metadata should not throw
            storage.upload(TEST_BUCKET, TEST_KEY, content, metadata)

            // Metadata is provider-specific; we just verify it doesn't crash
        }

        @Test
        fun `should overwrite existing object`() = runTest {
            val content1 = flowOf("first".toByteArray())
            val content2 = flowOf("second".toByteArray())

            storage.upload(TEST_BUCKET, TEST_KEY, content1)
            storage.upload(TEST_BUCKET, TEST_KEY, content2)

            // Second upload should overwrite without error
            val downloaded = collectBytes(storage.download(TEST_BUCKET, TEST_KEY))
            assertEquals("second", String(downloaded))
        }

        @Test
        fun `should handle large content in chunks`() = runTest {
            val chunkSize = 1024
            val totalChunks = 100
            val chunkList = (1..totalChunks).map { chunkNum ->
                ByteArray(chunkSize) { index -> (index + chunkNum).toByte() }
            }
            val content: Flow<ByteArray> = kotlinx.coroutines.flow.flowOf(*chunkList.toTypedArray())

            storage.upload(TEST_BUCKET, TEST_KEY, content)

            // Verify download works with chunked content and data integrity
            val downloaded = collectBytes(storage.download(TEST_BUCKET, TEST_KEY))
            val expected = chunkList.reduce { acc, bytes -> acc + bytes }
            assertEquals(expected.size, downloaded.size)
            assertArrayEquals(expected, downloaded)
        }
    }

    @Nested
    @DisplayName("download operations")
    inner class DownloadOperations {

        @Test
        fun `should download uploaded content`() = runTest {
            val content = flowOf(TEST_CONTENT.toByteArray())
            storage.upload(TEST_BUCKET, TEST_KEY, content)

            val downloaded = collectBytes(storage.download(TEST_BUCKET, TEST_KEY))

            assertArrayEquals(TEST_CONTENT.toByteArray(), downloaded)
        }

        @Test
        fun `should throw StorageObjectNotFoundException for non-existent object`() = runTest {
            val thrown = runCatching {
                // Collect the flow to trigger the download
                storage.download(TEST_BUCKET, "non-existent-key").collect { }
            }.exceptionOrNull()
            val target = thrown?.cause ?: thrown
            assertInstanceOf(
                StorageObjectNotFoundException::class.java,
                target
            )
        }

        @Test
        fun `should return content as Flow of ByteArray`() = runTest {
            val content = flowOf(TEST_CONTENT.toByteArray())
            storage.upload(TEST_BUCKET, TEST_KEY, content)

            val result = storage.download(TEST_BUCKET, TEST_KEY)

            assertInstanceOf(Flow::class.java, result)
        }
    }

    @Nested
    @DisplayName("delete operations")
    inner class DeleteOperations {

        @Test
        fun `should delete existing object`() = runTest {
            val content = flowOf(TEST_CONTENT.toByteArray())
            storage.upload(TEST_BUCKET, TEST_KEY, content)

            storage.delete(TEST_BUCKET, TEST_KEY)

            // Object should no longer exist
            assertInstanceOf(
                StorageObjectNotFoundException::class.java,
                runCatching {
                    storage.download(TEST_BUCKET, TEST_KEY).collect { }
                }.exceptionOrNull()?.cause
            )
        }

        @Test
        fun `should not throw when deleting non-existent object`() = runTest {
            // Deleting non-existent should not throw - idempotent operation
            storage.delete(TEST_BUCKET, "non-existent-key")
        }
    }

    @Nested
    @DisplayName("list operations")
    inner class ListOperations {

        @Test
        fun `should list objects in bucket`() = runTest {
            storage.upload(TEST_BUCKET, "file1.txt", flowOf("content1".toByteArray()))
            storage.upload(TEST_BUCKET, "file2.txt", flowOf("content2".toByteArray()))
            storage.upload(TEST_BUCKET, "subdir/file3.txt", flowOf("content3".toByteArray()))

            val keys = storage.list(TEST_BUCKET)

            assertTrue(keys.contains("file1.txt"))
            assertTrue(keys.contains("file2.txt"))
            assertTrue(keys.contains("subdir/file3.txt"))
        }

        @Test
        fun `should list objects with prefix`() = runTest {
            storage.upload(TEST_BUCKET, "file1.txt", flowOf("content1".toByteArray()))
            storage.upload(TEST_BUCKET, "file2.txt", flowOf("content2".toByteArray()))
            storage.upload(TEST_BUCKET, "subdir/file3.txt", flowOf("content3".toByteArray()))

            val keys = storage.list(TEST_BUCKET, "subdir/")

            assertEquals(1, keys.size)
            assertTrue(keys.contains("subdir/file3.txt"))
        }

        @Test
        fun `should return empty list for empty bucket`() = runTest {
            val keys = storage.list(TEST_BUCKET)

            assertTrue(keys.isEmpty())
        }

        @Test
        fun `should return empty list for non-matching prefix`() = runTest {
            storage.upload(TEST_BUCKET, "file1.txt", flowOf("content1".toByteArray()))

            val keys = storage.list(TEST_BUCKET, "non-matching-prefix/")

            assertTrue(keys.isEmpty())
        }
    }

    /**
     * Helper to collect all bytes from a Flow<ByteArray>.
     */
    private suspend fun collectBytes(flow: Flow<ByteArray>): ByteArray {
        val bytes = mutableListOf<ByteArray>()
        flow.collect { bytes.add(it) }
        return bytes.reduce { acc, bytes2 -> acc + bytes2 }
    }
}
