package com.profiletailors.storage.domain

import kotlinx.coroutines.flow.Flow

interface Storage {
    suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        metadata: Map<String, String> = emptyMap()
    )

    fun download(bucket: String, key: String): Flow<ByteArray>
    suspend fun delete(bucket: String, key: String)
    suspend fun list(bucket: String, prefix: String = ""): List<String>
    suspend fun presignGet(bucket: String, key: String, expirySeconds: Long = 300): String
}