package com.profiletailors.storage.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Shared validation messages to avoid duplicated string literals.
 */
private object ValidationMessages {
    const val BUCKET_BLANK = "Bucket cannot be blank"
    const val KEY_BLANK = "Key cannot be blank"
    const val UPLOADER_BLANK = "Uploader ID cannot be blank"
    const val DOWNLOADER_BLANK = "Downloader ID cannot be blank"
    const val DELETER_BLANK = "Deleter ID cannot be blank"
}

/**
 * Event published when a file is successfully uploaded to storage.
 * Used for auditing, quota tracking, and analytics.
 */
data class FileUploadedEvent(
    val bucket: String,
    val key: String,
    val sizeBytes: Long,
    val uploaderId: String,
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(bucket.isNotBlank()) { ValidationMessages.BUCKET_BLANK }
        require(key.isNotBlank()) { ValidationMessages.KEY_BLANK }
        require(uploaderId.isNotBlank()) { ValidationMessages.UPLOADER_BLANK }
    }
}

/**
 * Event published when a file is downloaded from storage.
 * Used for auditing, analytics, and detecting suspicious access patterns.
 */
data class FileDownloadedEvent(
    val bucket: String,
    val key: String,
    val downloaderId: String,
    val timestamp: Instant = Instant.now()
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(bucket.isNotBlank()) { ValidationMessages.BUCKET_BLANK }
        require(key.isNotBlank()) { ValidationMessages.KEY_BLANK }
        require(downloaderId.isNotBlank()) { ValidationMessages.DOWNLOADER_BLANK }
    }
}

/**
 * Event published when a file is deleted from storage.
 * Used for auditing and compliance tracking.
 */
data class FileDeletedEvent(
    val bucket: String,
    val key: String,
    val deleterId: String,
    val timestamp: Instant = Instant.now()
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(bucket.isNotBlank()) { ValidationMessages.BUCKET_BLANK }
        require(key.isNotBlank()) { ValidationMessages.KEY_BLANK }
        require(deleterId.isNotBlank()) { ValidationMessages.DELETER_BLANK }
    }
}

/**
 * Event published when a large file upload starts (>100MB).
 * Used for monitoring and alerting on long-running operations.
 */
data class LargeFileUploadStartedEvent(
    val bucket: String,
    val key: String,
    val expectedSizeBytes: Long,
    val uploaderId: String,
    val timestamp: Instant = Instant.now()
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(bucket.isNotBlank()) { ValidationMessages.BUCKET_BLANK }
        require(key.isNotBlank()) { ValidationMessages.KEY_BLANK }
        require(expectedSizeBytes > 0) { "Expected size must be positive" }
        require(uploaderId.isNotBlank()) { ValidationMessages.UPLOADER_BLANK }
    }
}
