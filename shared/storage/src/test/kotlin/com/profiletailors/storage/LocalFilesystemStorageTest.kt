package com.profiletailors.storage

import com.profiletailors.storage.application.GeneratePresignedUrlUseCase
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import com.profiletailors.storage.infrastructure.LocalFilesystemStorage
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
}
