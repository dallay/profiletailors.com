package com.profiletailors.storage.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.observability.NoOpOperationalEventSink
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import com.profiletailors.observability.emit
import com.profiletailors.storage.domain.FileDeletedEvent
import com.profiletailors.storage.domain.FileDownloadedEvent
import com.profiletailors.storage.domain.FileUploadedEvent
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageObservation
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Main application service for file storage operations.
 * Orchestrates storage operations with security validation, auditing, and metrics.
 *
 * This service acts as the boundary between the domain (Storage port)
 * and infrastructure adapters (S3, local filesystem, etc.), following
 * the hexagonal architecture pattern used in @shared/shield/ratelimit/.
 */
@Service
class StorageApplicationService(
    private val storage: Storage,
    private val eventPublisher: EventPublisher<BaseDomainEvent>,
    private val metrics: StorageObservation,
    private val provider: String = StorageObservation.Providers.LOCAL,
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) {

    /**
     * Upload a file to storage with security validation, auditing, and metrics.
     *
     * @param bucket The bucket name to upload to
     * @param key The object key
     * @param content Flow of byte arrays containing the file content
     * @param uploaderId Identifier of who is uploading (for auditing)
     * @param metadata Optional metadata key-value pairs
     * @throws StorageSecurityException If path traversal is detected
     * @throws StorageServiceException If upload fails
     */
    suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        uploaderId: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        validateBucketAndKey(bucket, key)

        var totalSize = 0L
        val trackedContent = content.map { chunk ->
            totalSize += chunk.size.toLong()
            chunk
        }

        try {
            metrics.recordOperationTime(StorageObservation.Operations.UPLOAD, provider) {
                storage.upload(bucket, key, trackedContent, metadata)
            }
            metrics.recordBytesUploaded(totalSize, provider, bucket)
            metrics.recordOperation(StorageObservation.Operations.UPLOAD, provider, bucket, true)
            onUploadSuccess(bucket, key, totalSize, uploaderId, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorType = when (e) {
                is StorageSecurityException -> StorageObservation.ErrorTypes.SECURITY
                is StorageObjectNotFoundException -> StorageObservation.ErrorTypes.NOT_FOUND
                else -> StorageObservation.ErrorTypes.SERVICE
            }
            metrics.recordError(StorageObservation.Operations.UPLOAD, provider, bucket, errorType)
            metrics.recordOperation(StorageObservation.Operations.UPLOAD, provider, bucket, false)
            throw e
        }
    }

    /**
     * Publishes an event after a successful upload.
     *
     * @param bucket The bucket containing the uploaded file.
     * @param key The key of the uploaded file.
     * @param totalSize The uploaded file size in bytes.
     * @param uploaderId The identifier of the uploader.
     * @param metadata Metadata associated with the uploaded file.
     */
    private suspend fun onUploadSuccess(
        bucket: String,
        key: String,
        totalSize: Long,
        uploaderId: String,
        metadata: Map<String, String>,
    ) {
        try {
            eventPublisher.publish(
                FileUploadedEvent(
                    bucket = bucket,
                    key = key,
                    sizeBytes = totalSize,
                    uploaderId = uploaderId,
                    timestamp = Instant.now(),
                    metadata = metadata,
                ),
            )
        } catch (e: CancellationException) {
            throw e // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            emitPublishFailure(StorageObservation.Operations.UPLOAD, bucket, e)
        }
    }

    /**
     * Downloads an object from storage while recording metrics and publishing an audit event.
     *
     * @param bucket The name of the storage bucket.
     * @param key The object key.
     * @param downloaderId The identifier of the user or process performing the download.
     * @return A flow of byte arrays containing the object content.
     */
    fun download(bucket: String, key: String, downloaderId: String): Flow<ByteArray> {
        validateBucketAndKey(bucket, key)

        return channelFlow {
            // Audit download
            try {
                eventPublisher.publish(
                    FileDownloadedEvent(
                        bucket = bucket,
                        key = key,
                        downloaderId = downloaderId,
                        timestamp = Instant.now(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                emitPublishFailure(StorageObservation.Operations.DOWNLOAD, bucket, e)
            }

            var bytesDownloaded = 0L
            try {
                val downloadFlow = storage.download(bucket, key)
                downloadFlow.collect { chunk ->
                    bytesDownloaded += chunk.size.toLong()
                    send(chunk)
                }
                metrics.recordBytesDownloaded(bytesDownloaded, provider, bucket)
                metrics.recordOperation(StorageObservation.Operations.DOWNLOAD, provider, bucket, true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorType = when (e) {
                    is StorageSecurityException -> StorageObservation.ErrorTypes.SECURITY
                    is StorageObjectNotFoundException -> StorageObservation.ErrorTypes.NOT_FOUND
                    else -> StorageObservation.ErrorTypes.SERVICE
                }
                metrics.recordError(StorageObservation.Operations.DOWNLOAD, provider, bucket, errorType)
                metrics.recordOperation(StorageObservation.Operations.DOWNLOAD, provider, bucket, false)
                throw e
            }
        }
    }

    /**
     * Delete a file from storage with security validation, auditing, and metrics.
     *
     * @param bucket The bucket name
     * @param key The object key
     * @param deleterId Identifier of who is deleting (for auditing)
     * @throws StorageSecurityException If path traversal is detected
     * @throws StorageServiceException If deletion fails
     */
    suspend fun delete(bucket: String, key: String, deleterId: String) {
        validateBucketAndKey(bucket, key)

        try {
            metrics.recordOperationTime(StorageObservation.Operations.DELETE, provider) {
                storage.delete(bucket, key)
            }
            metrics.recordOperation(StorageObservation.Operations.DELETE, provider, bucket, true)
            onDeleteSuccess(bucket, key, deleterId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorType = when (e) {
                is StorageSecurityException -> StorageObservation.ErrorTypes.SECURITY
                is StorageObjectNotFoundException -> StorageObservation.ErrorTypes.NOT_FOUND
                else -> StorageObservation.ErrorTypes.SERVICE
            }
            metrics.recordError(StorageObservation.Operations.DELETE, provider, bucket, errorType)
            metrics.recordOperation(StorageObservation.Operations.DELETE, provider, bucket, false)
            throw e
        }
    }

    /**
     * Publishes the event for a successful file deletion.
     *
     * @param bucket The bucket containing the deleted file.
     * @param key The key of the deleted file.
     * @param deleterId The identifier of the user or process that deleted the file.
     */
    private suspend fun onDeleteSuccess(bucket: String, key: String, deleterId: String) {
        try {
            eventPublisher.publish(
                FileDeletedEvent(
                    bucket = bucket,
                    key = key,
                    deleterId = deleterId,
                    timestamp = Instant.now(),
                ),
            )
        } catch (e: CancellationException) {
            throw e // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            emitPublishFailure(StorageObservation.Operations.DELETE, bucket, e)
        }
    }

    /**
     * Copy an object from source to destination within the same bucket.
     *
     * @param bucket The bucket name
     * @param sourceKey The source object key
     * @param destKey The destination object key
     * @throws StorageSecurityException If path traversal is detected
     * @throws StorageServiceException If copy fails
     */
    suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        validateBucketAndKey(bucket, sourceKey)
        validateBucketAndKey(bucket, destKey)

        try {
            metrics.recordOperationTime(StorageObservation.Operations.COPY, provider) {
                storage.copyObject(bucket, sourceKey, destKey)
            }
            metrics.recordOperation(StorageObservation.Operations.COPY, provider, bucket, true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorType = when (e) {
                is StorageSecurityException -> StorageObservation.ErrorTypes.SECURITY
                is StorageObjectNotFoundException -> StorageObservation.ErrorTypes.NOT_FOUND
                else -> StorageObservation.ErrorTypes.SERVICE
            }
            metrics.recordError(StorageObservation.Operations.COPY, provider, bucket, errorType)
            metrics.recordOperation(StorageObservation.Operations.COPY, provider, bucket, false)
            throw e
        }
    }

    /**
     * Lists object keys in a bucket, optionally filtered by a prefix.
     *
     * @param bucket The bucket containing the objects.
     * @param prefix The optional prefix used to filter object keys.
     * @return The matching object keys.
     */
    suspend fun list(bucket: String, prefix: String = ""): List<String> = try {
        metrics.recordOperationTime(StorageObservation.Operations.LIST, provider) {
            storage.list(bucket, prefix)
        }.also {
            metrics.recordOperation(StorageObservation.Operations.LIST, provider, bucket, true)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val errorType = when (e) {
            is StorageSecurityException -> StorageObservation.ErrorTypes.SECURITY
            is StorageObjectNotFoundException -> StorageObservation.ErrorTypes.NOT_FOUND
            else -> StorageObservation.ErrorTypes.SERVICE
        }
        metrics.recordError(StorageObservation.Operations.LIST, provider, bucket, errorType)
        metrics.recordOperation(StorageObservation.Operations.LIST, provider, bucket, false)
        throw e
    }

    /**
     * Reports a domain-event publication failure as a warning operational event.
     *
     * @param operation The storage operation whose event could not be published.
     * @param bucket The affected bucket name, when available.
     * @param cause The failure that prevented event publication.
     */
    private fun emitPublishFailure(operation: String, bucket: String, cause: Throwable) {
        if (bucket.isBlank()) {
            operationalEvents.emit(
                severity = Severity.WARN,
                name = StorageOperationalEvents.PUBLISH_FAILED_EVENT,
                message = StorageOperationalEvents.PUBLISH_FAILED_MESSAGE,
                cause = cause,
                "operation" to operation,
                "provider" to provider,
            )
        } else {
            operationalEvents.emit(
                severity = Severity.WARN,
                name = StorageOperationalEvents.PUBLISH_FAILED_EVENT,
                message = StorageOperationalEvents.PUBLISH_FAILED_MESSAGE,
                cause = cause,
                "operation" to operation,
                "provider" to provider,
                "bucket" to bucket,
            )
        }
    }

    /**
     * Rejects bucket names and object keys containing path traversal sequences.
     *
     * @param bucket The bucket name to validate.
     * @param key The object key to validate.
     * @throws StorageSecurityException If the bucket name or key contains `..`.
     */
    private fun validateBucketAndKey(bucket: String, key: String) {
        if (bucket.contains("..")) {
            throw StorageSecurityException("Invalid bucket name: path traversal detected")
        }
        if (key.contains("..")) {
            throw StorageSecurityException("Invalid key: path traversal detected")
        }
    }

    private companion object
}
