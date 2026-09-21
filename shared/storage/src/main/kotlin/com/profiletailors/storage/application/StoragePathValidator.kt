package com.profiletailors.storage.application

import com.profiletailors.storage.domain.StorageSecurityException

internal object StoragePathValidator {
    fun validateBucketAndKey(bucket: String, key: String) {
        requireValid(bucket, "bucket name")
        requireValid(key, "key")
    }

    private fun requireValid(value: String, kind: String) {
        val reason = validationReason(value) ?: return
        throw StorageSecurityException("Invalid $kind: $reason")
    }

    private fun validationReason(value: String): String? {
        if (value.contains("..")) {
            return "path traversal detected"
        }
        if (value.contains('\u0000')) {
            return "malformed path"
        }
        if (value.startsWith("/")) {
            return "absolute path not allowed"
        }
        return null
    }
}
