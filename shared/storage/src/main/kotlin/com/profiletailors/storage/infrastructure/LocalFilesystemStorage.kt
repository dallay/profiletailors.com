package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class LocalFilesystemStorage(basePath: Path) : Storage {

    private val basePath: Path = basePath.toAbsolutePath().normalize()

    init {
        try {
            Files.createDirectories(basePath)
        } catch (e: IOException) {
            throw StorageServiceException("Failed to create base path: $basePath", e)
        }
    }

    private fun resolveSafe(bucket: String, key: String): Path {
        val normalizedBucket = Path.of(bucket).normalize()
        if (normalizedBucket.isAbsolute) {
            throw StorageSecurityException("Absolute bucket path not allowed: $bucket")
        }
        val bucketPath = basePath.resolve(normalizedBucket).normalize()
        if (!bucketPath.startsWith(basePath)) {
            throw StorageSecurityException("Bucket path traversal detected: $bucket")
        }

        val normalized = Path.of(key).normalize()
        val resolved = bucketPath.resolve(normalized).normalize()
        if (!resolved.startsWith(bucketPath)) {
            throw StorageSecurityException("Path traversal detected for key: $key")
        }
        return resolved
    }

    override suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String>
    ) {
        val target = resolveSafe(bucket, key)
        ensureParentDirectories(target)
        val tmp = createTempFile()
        writeContent(target, tmp, content)
    }

    private suspend fun ensureParentDirectories(target: Path) {
        withContext(Dispatchers.IO) {
            try {
                Files.createDirectories(target.parent)
            } catch (e: IOException) {
                throw StorageServiceException("Failed to create parent directories for: ${target.fileName}", e)
            }
        }
    }

    private suspend fun createTempFile(): Path {
        return withContext(Dispatchers.IO) {
            try {
                Files.createTempFile(basePath, "upload", ".tmp")
            } catch (e: IOException) {
                throw StorageServiceException("Failed to create temp file for upload", e)
            }
        }
    }

    private suspend fun writeContent(target: Path, tmp: Path, content: Flow<ByteArray>) {
        val os = withContext(Dispatchers.IO) { tmp.toFile().outputStream() }
        try {
            content.collect { chunk ->
                withContext(Dispatchers.IO) {
                    try {
                        os.write(chunk)
                        os.flush()
                    } catch (e: IOException) {
                        throw StorageServiceException("Error writing chunk to disk", e)
                    }
                }
            }
            // Attempt finalize - if this fails, temp file will be cleaned below
            try {
                finalizeUpload(tmp, target, os)
            } catch (e: IOException) {
                // Rename failed - cleanup temp file before rethrowing
                cleanupTempFile(tmp, os)
                throw StorageServiceException("Failed to finalize upload", e)
            }
        } catch (ex: Throwable) {
            // For other errors (not from finalizeUpload), also cleanup
            cleanupTempFile(tmp, os)
            when (ex) {
                is StorageException -> throw ex
                else -> throw StorageServiceException("Upload failed", ex)
            }
        }
    }

    private fun cleanupTempFile(tmp: Path, os: java.io.OutputStream?) {
        runCatching { os?.close() }
        runCatching { Files.deleteIfExists(tmp) }
    }

    private suspend fun finalizeUpload(tmp: Path, target: Path, os: java.io.OutputStream) {
        withContext(Dispatchers.IO) {
            try {
                os.flush()
                os.close()
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw StorageServiceException("Failed to finalize upload", e)
            }
        }
    }

    

    override fun download(bucket: String, key: String): Flow<ByteArray> =
        channelFlow {
            val source = resolveSafe(bucket, key)
            if (!Files.exists(source)) throw StorageObjectNotFoundException(bucket, key)
            launch(Dispatchers.IO) {
                readFileToChannel(source, this@channelFlow)
            }.invokeOnCompletion { cause -> if (cause != null) close(cause) else close() }
        }

    private suspend fun readFileToChannel(source: Path, channel: SendChannel<ByteArray>) {
        try {
            Files.newInputStream(source).use { ins ->
                val buffer = ByteArray(8192)
                var read = ins.read(buffer)
                while (read >= 0) {
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    channel.send(chunk)
                    read = ins.read(buffer)
                }
            }
        } catch (e: IOException) {
            throw StorageServiceException("Error reading file from disk", e)
        }
    }

    override suspend fun delete(bucket: String, key: String) {
        val target = resolveSafe(bucket, key)
        withContext(Dispatchers.IO) {
            try {
                Files.deleteIfExists(target)
            } catch (e: IOException) {
                throw StorageServiceException("Failed to delete file: $key", e)
            }
        }
    }

    override suspend fun list(bucket: String, prefix: String): List<String> =
        withContext(Dispatchers.IO) {
            val bucketPath = resolveBucketPath(bucket)
            val dir = resolveListDirectory(bucket, prefix, bucketPath)
            validateDirectoryBounds(dir, bucketPath, prefix)
            walkDirectory(dir, bucketPath, prefix)
        }

    override suspend fun exists(bucket: String, key: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolveSafe(bucket, key)
            Files.exists(target)
        }

    private fun resolveBucketPath(bucket: String): Path {
        return try {
            resolveSafe(bucket, "")
        } catch (e: StorageSecurityException) {
            throw StorageSecurityException("Path traversal attempt in bucket: $bucket")
        }
    }

    private fun resolveListDirectory(bucket: String, prefix: String, bucketPath: Path): Path {
        if (prefix.isEmpty() || prefix == "." || prefix == "./") {
            return bucketPath
        }
        return try {
            val safePrefix = resolveSafe(bucket, prefix)
            if (prefix.endsWith("/")) safePrefix else safePrefix.parent ?: bucketPath
        } catch (e: StorageSecurityException) {
            throw StorageSecurityException("Path traversal attempt in prefix: $prefix")
        }
    }

    private fun validateDirectoryBounds(dir: Path, bucketPath: Path, prefix: String) {
        val normalizedDir = dir.normalize()
        if (!normalizedDir.startsWith(bucketPath.normalize())) {
            throw StorageSecurityException("Path traversal attempt with prefix: $prefix")
        }
    }

    private fun walkDirectory(dir: Path, bucketPath: Path, prefix: String): List<String> {
        if (!Files.exists(dir)) return emptyList()
        return try {
            Files.walk(dir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { !it.fileName.toString().startsWith(".") }
                    .map { bucketPath.relativize(it).toString().replace('\\', '/') }
                    .filter { key -> prefix.isEmpty() || key.startsWith(prefix) }
                    .toList()
            }
        } catch (e: IOException) {
            throw StorageServiceException("Failed to list objects with prefix: $prefix", e)
        }
    }
}
