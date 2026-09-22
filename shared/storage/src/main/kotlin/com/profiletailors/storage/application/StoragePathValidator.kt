package com.profiletailors.storage.application

import com.profiletailors.storage.domain.StorageSecurityException

internal object StoragePathValidator {
    fun validateBucketAndKey(bucket: String, key: String) {
        requireValidBucket(bucket)
        requireValidKey(key)
    }

    private fun requireValidBucket(value: String) {
        val reason = bucketReason(value) ?: return
        throw StorageSecurityException("Invalid bucket name: $reason")
    }

    private fun requireValidKey(value: String) {
        val reason = keyReason(value) ?: return
        throw StorageSecurityException("Invalid key: $reason")
    }

    private fun bucketReason(value: String): String? {
        if (value.contains("..")) {
            return "path traversal detected"
        }
        return commonReason(value)
    }

    private fun keyReason(value: String): String? {
        if (value.split("/").any { it == ".." }) {
            return "path traversal detected"
        }
        return commonReason(value)
    }

    private fun commonReason(value: String): String? {
        if (value.contains('\u0000')) {
            return "malformed path"
        }
        if (value.startsWith("/")) {
            return "absolute path not allowed"
        }
        return null
    }
}
