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
