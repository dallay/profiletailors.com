package com.profiletailors.storage.domain

sealed class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class StorageObjectNotFoundException(bucket: String, key: String) : 
    StorageException("Object '$key' not found in bucket '$bucket'")

class StorageSecurityException(message: String) : 
    StorageException(message)

class StorageServiceException(message: String, cause: Throwable? = null) : 
    StorageException(message, cause)

class BucketNotFoundException(message: String) :
    StorageException(message)

/**
 * Exception thrown when rate limit is exceeded during storage operations.
 * Carries HTTP 429 semantics with retry information.
 *
 * @param retryAfterSeconds Seconds until the rate limit resets
 * @param message Descriptive message
 */
class RateLimitExceededException(
    val retryAfterSeconds: Long,
    message: String = "Rate limit exceeded. Retry after $retryAfterSeconds seconds"
) : StorageException(message)

/**
 * Exception thrown when access to a storage resource is denied.
 * Maps to AWS SDK `AccessDeniedException`.
 */
class StorageAccessDeniedException(message: String) :
    StorageException(message)

/**
 * Exception thrown when a connection to storage cannot be established.
 * Maps to AWS SDK `ServiceUnavailable` and other network-related errors.
 */
class StorageConnectionException(message: String, cause: Throwable? = null) :
    StorageException(message, cause)
