package com.profiletailors.storage.domain

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

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
        require(bucket.isNotBlank()) { "Bucket cannot be blank" }
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(uploaderId.isNotBlank()) { "Uploader ID cannot be blank" }
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
        require(bucket.isNotBlank()) { "Bucket cannot be blank" }
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(downloaderId.isNotBlank()) { "Downloader ID cannot be blank" }
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
        require(bucket.isNotBlank()) { "Bucket cannot be blank" }
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(deleterId.isNotBlank()) { "Deleter ID cannot be blank" }
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
        require(bucket.isNotBlank()) { "Bucket cannot be blank" }
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(expectedSizeBytes > 0) { "Expected size must be positive" }
        require(uploaderId.isNotBlank()) { "Uploader ID cannot be blank" }
    }
}
