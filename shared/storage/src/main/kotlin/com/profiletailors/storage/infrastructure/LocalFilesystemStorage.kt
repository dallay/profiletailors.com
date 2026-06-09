package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class LocalFilesystemStorage(private val basePath: Path) : Storage {

    init {
        try {
            Files.createDirectories(basePath)
        } catch (e: IOException) {
            throw StorageServiceException("Failed to create base path: $basePath", e)
        }
    }

    private fun resolveSafe(bucket: String, key: String): Path {
        // Validate bucket path first
        val normalizedBucket = Path.of(bucket).normalize()
        if (normalizedBucket.isAbsolute) {
            throw StorageSecurityException("Absolute bucket path not allowed: $bucket")
        }
        val bucketPath = basePath.resolve(normalizedBucket).normalize()
        if (!bucketPath.startsWith(basePath)) {
            throw StorageSecurityException("Bucket path traversal detected: $bucket")
        }

        // Now validate key against the validated bucket path
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
        // Ensure parent directories exist
        withContext(Dispatchers.IO) {
            try {
                Files.createDirectories(target.parent)
            } catch (e: IOException) {
                throw StorageServiceException("Failed to create parent directories for: $key", e)
            }
        }

        val tmp = withContext(Dispatchers.IO) {
            try {
                Files.createTempFile(basePath, "upload", ".tmp")
            } catch (e: IOException) {
                throw StorageServiceException("Failed to create temp file for upload", e)
            }
        }
        // Open output stream in IO dispatcher and write chunks using withContext for each blocking write
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
            withContext(Dispatchers.IO) {
                try {
                    os.flush()
                    os.close()
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: IOException) {
                    throw StorageServiceException("Failed to finalize upload", e)
                }
            }
        } catch (ex: Throwable) {
            // Attempt to cleanup temp file
            withContext(Dispatchers.IO) {
                try {
                    os.close()
                } catch (_: Throwable) {
                }
                try {
                    Files.deleteIfExists(tmp)
                } catch (_: Throwable) {
                }
            }
            if (ex is StorageException) throw ex
            throw StorageServiceException("Upload failed", ex)
        }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> =
        channelFlow {
            val source = try {
                resolveSafe(bucket, key)
            } catch (e: StorageSecurityException) {
                throw e
            }
            if (!Files.exists(source)) throw StorageObjectNotFoundException(bucket, key)
            // Launch reading in IO dispatcher and send chunks to the channelFlow
            val producer = launch(Dispatchers.IO) {
                try {
                    Files.newInputStream(source).use { ins ->
                        val buffer = ByteArray(8192)
                        var read = ins.read(buffer)
                        while (read >= 0) {
                            val chunk =
                                if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                            send(chunk)
                            read = ins.read(buffer)
                        }
                    }
                } catch (e: IOException) {
                    throw StorageServiceException("Error reading file from disk", e)
                }
            }
            // ensure producer completes before closing
            producer.invokeOnCompletion { cause -> if (cause != null) close(cause) else close() }
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
            // Validate bucket with path traversal protection
            val bucketPath = try {
                resolveSafe(bucket, "")
            } catch (e: StorageSecurityException) {
                throw StorageSecurityException("Path traversal attempt in bucket: $bucket")
            }

            // Resolve the directory to list from
            val dir = if (prefix.isEmpty() || prefix == "." || prefix == "./") {
                // Special-case: "." and "./" map to bucket root (not its parent)
                bucketPath
            } else {
                try {
                    // For prefix "subdir/" or "subdir/nested/", we want to list from bucketPath/subdir
                    val safePrefix = resolveSafe(bucket, prefix)
                    // If prefix ends with /, the prefix itself is the directory
                    // Otherwise use the parent to get the containing directory
                    if (prefix.endsWith("/")) {
                        safePrefix
                    } else {
                        safePrefix.parent ?: bucketPath
                    }
                } catch (e: StorageSecurityException) {
                    throw StorageSecurityException("Path traversal attempt in prefix: $prefix")
                }
            }

            // Ensure dir is still contained within bucketPath (prevents prefix="." from escaping)
            val normalizedDir = dir.normalize()
            if (!normalizedDir.startsWith(bucketPath.normalize())) {
                throw StorageSecurityException("Path traversal attempt with prefix: $prefix")
            }

            if (!Files.exists(dir)) return@withContext emptyList()
            try {
                return@withContext Files.walk(dir).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { !it.fileName.toString().startsWith(".") } // Skip hidden files
                        .map { bucketPath.relativize(it).toString().replace('\\', '/') }
                        .filter { key -> prefix.isEmpty() || key.startsWith(prefix) }
                        .toList()
                }
            } catch (e: IOException) {
                throw StorageServiceException("Failed to list objects with prefix: $prefix", e)
            }
        }
}
