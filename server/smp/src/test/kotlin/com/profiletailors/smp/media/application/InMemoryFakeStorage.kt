package com.profiletailors.smp.media.application

import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [Storage] for tests.
 */
class InMemoryFakeStorage(
    private val failUpload: Boolean = false,
    private val failDeleteAll: Boolean = false,
    private val failDeleteKeys: Set<String> = emptySet(),
) : Storage {
    val uploadedKeys = mutableListOf<String>()
    val deletedKeys = mutableListOf<String>()
    val uploadedMetadata = mutableMapOf<String, Map<String, String>>()
    private val objects = mutableMapOf<String, ByteArray>()

    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        if (failUpload) throw StorageServiceException("Simulated upload failure")
        uploadedKeys += key
        uploadedMetadata[key] = metadata

        val chunks = mutableListOf<ByteArray>()
        content.collect { chunks += it }
        objects["$bucket/$key"] = chunks.flatMap { it.toList() }.toByteArray()
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> {
        val data = objects["$bucket/$key"] ?: throw StorageObjectNotFoundException(bucket, key)
        return flowOf(data)
    }

    override suspend fun delete(bucket: String, key: String) {
        deletedKeys += key
        if (failDeleteAll || key in failDeleteKeys) {
            throw StorageServiceException("Simulated delete failure for key: $key")
        }
        objects.remove("$bucket/$key")
    }

    override suspend fun list(bucket: String, prefix: String): List<String> = objects.keys.filter {
        it.startsWith("$bucket/$prefix")
    }

    override suspend fun exists(bucket: String, key: String): Boolean = objects.containsKey("$bucket/$key")

    override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        val sourcePath = "$bucket/$sourceKey"
        val data = objects[sourcePath]
            ?: throw IllegalStateException("copyObject: source not found: $sourceKey")
        objects["$bucket/$destKey"] = data
    }
}
