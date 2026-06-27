package com.profiletailors.storage.domain

interface StorageObservation {
    fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean)

    fun recordBytesUploaded(bytes: Long, provider: String, bucket: String)

    fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String)

    fun recordOperationLatency(operation: String, provider: String, durationNanos: Long)

    fun recordError(operation: String, provider: String, bucket: String, errorType: String)

    fun recordPresignedUrlGenerated(provider: String, success: Boolean)

    suspend fun <T : Any> recordOperationTime(operation: String, provider: String, action: suspend () -> T): T

    object Operations {
        const val UPLOAD = "upload"
        const val DOWNLOAD = "download"
        const val DELETE = "delete"
        const val LIST = "list"
        const val PRESIGN = "presign"
        const val COPY = "copy"
    }

    object ErrorTypes {
        const val NOT_FOUND = "not_found"
        const val SECURITY = "security"
        const val SERVICE = "service"
        const val TIMEOUT = "timeout"
        const val RATE_LIMITED = "rate_limited"
    }

    object Providers {
        const val LOCAL = "local"
        const val S3 = "s3"
        const val S2 = "s2"
    }
}
