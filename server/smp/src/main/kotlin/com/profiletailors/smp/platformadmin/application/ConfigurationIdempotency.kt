package com.profiletailors.smp.platformadmin.application

import java.util.UUID

data class ConfigurationIdempotencyRecord(
    val operatorPrincipalId: UUID,
    val operation: String,
    val idempotencyKey: String,
    val responseJson: String? = null,
)

interface ConfigurationIdempotencyStore {
    suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord?
    suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean
    suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String)
    suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String)
}

interface ConfigurationIdempotencyCodec {
    fun encode(value: Any): String
    fun <T : Any> decode(responseJson: String, responseType: Class<T>): T
}

class ConfigurationIdempotencyConflictException :
    RuntimeException("Idempotency key was already used for another configuration request.")

class ConfigurationIdempotencyInProgressException :
    RuntimeException("The configuration request with this idempotency key is still in progress.")

class ConfigurationIdempotencyService(
    private val store: ConfigurationIdempotencyStore,
    private val codec: ConfigurationIdempotencyCodec,
) {
    suspend fun <T : Any> execute(
        operatorPrincipalId: UUID,
        operation: String,
        idempotencyKey: String,
        responseType: Class<T>,
        action: suspend () -> T,
    ): T {
        val existing = store.find(operatorPrincipalId, idempotencyKey)
        if (existing != null) {
            validateScope(existing, operation)
            val responseJson = existing.responseJson ?: throw ConfigurationIdempotencyInProgressException()
            return codec.decode(responseJson, responseType)
        }

        val claimed = store.claim(ConfigurationIdempotencyRecord(operatorPrincipalId, operation, idempotencyKey))
        if (!claimed) {
            return replayOrInProgress(store.find(operatorPrincipalId, idempotencyKey), operation, responseType)
        }

        return try {
            val result = action()
            store.complete(operatorPrincipalId, idempotencyKey, codec.encode(result))
            result
        } catch (error: Throwable) {
            store.remove(operatorPrincipalId, idempotencyKey)
            throw error
        }
    }

    private suspend fun <T : Any> replayOrInProgress(
        concurrent: ConfigurationIdempotencyRecord?,
        operation: String,
        responseType: Class<T>,
    ): T {
        val record = concurrent ?: throw ConfigurationIdempotencyInProgressException()
        validateScope(record, operation)
        val responseJson = record.responseJson ?: throw ConfigurationIdempotencyInProgressException()
        return codec.decode(responseJson, responseType)
    }

    private fun validateScope(record: ConfigurationIdempotencyRecord, operation: String) {
        if (record.operation != operation) throw ConfigurationIdempotencyConflictException()
    }
}
