package com.profiletailors.storage

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.reduce

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
            storage.download("local", "../secret.txt").toList()
        }
    }
}
