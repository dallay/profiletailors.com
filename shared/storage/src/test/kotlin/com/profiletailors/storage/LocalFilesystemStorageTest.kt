package com.profiletailors.storage

import com.profiletailors.storage.application.GeneratePresignedUrlUseCase
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import com.profiletailors.storage.infrastructure.LocalFilesystemStorage
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.runBlocking

class LocalFilesystemStorageTest {

    @Test
    fun `upload and download file`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val bucket = "local"
        val key = "foo/bar.txt"
        val data = "hello world".toByteArray()
        val flow = flow {
            emit(data)
        }
        storage.upload(bucket, key, flow)
        val downloaded = storage.download(bucket, key)
            .toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals("hello world", String(downloaded))
        val listed = storage.list(bucket, "foo")
        assertTrue(listed.any { it.endsWith("foo/bar.txt") || it == "foo/bar.txt" })
    }

    @Test
    fun `upload allows valid bucket and key when base path is relative`() = runTest {
        val relativeBasePath = Path.of("./tmp/profiletailors-storage-test")
        Files.createDirectories(relativeBasePath)
        try {
            val storage = LocalFilesystemStorage(relativeBasePath)
            val flow = flowOf("data".toByteArray())

            storage.upload("attachments", "assets/dev-workspace-001/asset-123", flow)

            assertTrue(
                Files.exists(
                    relativeBasePath
                        .resolve("attachments")
                        .resolve("assets/dev-workspace-001/asset-123")
                        .normalize(),
                ),
            )
        } finally {
            relativeBasePath.toFile().deleteRecursively()
        }
    }

    @Test
    fun `prevent path traversal on upload`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val flow = flow { emit("data".toByteArray()) }
        
        assertThrows<StorageSecurityException> {
            runBlocking {
                storage.upload("local", "../secret.txt", flow)
            }
        }
    }

    @Test
    fun `prevent path traversal on download`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)

        assertThrows<StorageSecurityException> {
            runBlocking {
                storage.download("local", "../secret.txt").toList()
            }
        }
    }

    @Test
    fun `prevent path traversal on list with bucket`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)

        assertThrows<StorageSecurityException> {
            runBlocking {
                storage.list("../etc", "passwd")
            }
        }
    }

    @Test
    fun `prevent path traversal on list with prefix`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)

        assertThrows<StorageSecurityException> {
            runBlocking {
                storage.list("local", "../../../etc/passwd")
            }
        }
    }

    @Test
    fun `list files with valid path`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val bucket = "test-bucket"
        val key = "files/test.txt"
        val data = "test content".toByteArray()
        
        // Upload a file
        storage.upload(bucket, key, flowOf(data))
        
        // List should work with valid paths
        val listed = storage.list(bucket, "")
        assertTrue(listed.any { it.contains("test.txt") })
    }

    @Test
    fun `list files with nested directory traversal attempt fails`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val bucket = "test-bucket"
        
        // Attempt to access parent directory
        assertThrows<StorageSecurityException> {
            runBlocking {
                storage.list(bucket, "../secret")
            }
        }
    }

    @Test
    fun `delete succeeds with valid bucket and key without triggering path traversal false positive`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val bucket = "attachments"
        val key = "assets/dev-workspace-001/asset-456"

        storage.upload(bucket, key, flowOf("delete-me".toByteArray()))
        assertTrue(Files.exists(tempDir.resolve(bucket).resolve(key)))

        storage.delete(bucket, key)
        assertTrue(!Files.exists(tempDir.resolve(bucket).resolve(key)))
    }

    @Test
    fun `bucket named attachments does not trigger path traversal false positive`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)

        storage.upload("attachments", "assets/ws-001/file-1", flowOf("data".toByteArray()))

        val downloaded = storage.download("attachments", "assets/ws-001/file-1")
            .toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals("data", String(downloaded))

        storage.delete("attachments", "assets/ws-001/file-1")
    }

    @Test
    fun `download throws StorageServiceException on IO error`(@TempDir tempDir: Path) = runTest {
        val storage = LocalFilesystemStorage(tempDir)
        val bucket = "local"
        // Create a directory where a file would be expected — Files.newInputStream on
        // a directory throws IOException, exercising the catch block in readFileToChannel.
        Files.createDirectories(tempDir.resolve(bucket).resolve("dangling-dir"))

        val ex = assertThrows<StorageServiceException> {
            runBlocking { storage.download(bucket, "dangling-dir").toList() }
        }
        assertTrue(ex.message!!.contains("Error reading file from disk", ignoreCase = true))
    }
}
