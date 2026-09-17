package com.profiletailors.smp.platformadmin.application

import com.profiletailors.smp.platformadmin.application.contracts.UserControlTelemetry
import java.util.UUID

data class UserControlIdempotencyRecord(
    val operatorPrincipalId: UUID,
    val operation: String,
    val targetPrincipalId: String,
    val idempotencyKey: String,
    val responseJson: String? = null,
)

interface UserControlIdempotencyStore {
    suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): UserControlIdempotencyRecord?
    suspend fun claim(record: UserControlIdempotencyRecord): Boolean
    suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String)
    suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String)
}

interface UserControlIdempotencyCodec {
    fun encode(value: Any): String
    fun <T : Any> decode(responseJson: String, responseType: Class<T>): T
}

class UserControlIdempotencyConflictException :
    RuntimeException("Idempotency key was already used for another user control request.")

class UserControlIdempotencyInProgressException :
    RuntimeException("The user control request with this idempotency key is still in progress.")

class UserControlIdempotencyService(
    private val store: UserControlIdempotencyStore,
    private val codec: UserControlIdempotencyCodec,
    private val telemetry: UserControlTelemetry = UserControlTelemetry.noop(),
) {
    suspend fun <T : Any> execute(
        operatorPrincipalId: UUID,
        operation: String,
        targetPrincipalId: String,
        idempotencyKey: String,
        responseType: Class<T>,
        action: suspend () -> T,
    ): T {
        val existing = store.find(operatorPrincipalId, idempotencyKey)
        if (existing != null) {
            validateScope(existing, operation, targetPrincipalId)
            val responseJson = existing.responseJson
                ?: throw UserControlIdempotencyInProgressException()
            telemetry.recordIdempotencyReplay()
            return codec.decode(responseJson, responseType)
        }

        val claimed = store.claim(
            UserControlIdempotencyRecord(
                operatorPrincipalId,
                operation,
                targetPrincipalId,
                idempotencyKey,
            ),
        )
        if (!claimed) {
            return replayOrInProgress(
                store.find(operatorPrincipalId, idempotencyKey),
                operation,
                targetPrincipalId,
                responseType,
            )
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
        concurrent: UserControlIdempotencyRecord?,
        operation: String,
        targetPrincipalId: String,
        responseType: Class<T>,
    ): T {
        val record = concurrent ?: throw UserControlIdempotencyInProgressException()
        validateScope(record, operation, targetPrincipalId)
        val responseJson = record.responseJson ?: throw UserControlIdempotencyInProgressException()
        telemetry.recordIdempotencyReplay()
        return codec.decode(responseJson, responseType)
    }

    private fun validateScope(record: UserControlIdempotencyRecord, operation: String, targetPrincipalId: String) {
        if (record.operation != operation || record.targetPrincipalId != targetPrincipalId) {
            throw UserControlIdempotencyConflictException()
        }
    }
}
