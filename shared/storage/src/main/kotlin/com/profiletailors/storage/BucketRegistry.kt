package com.profiletailors.storage

interface BucketRegistry {
    fun getStorage(bucketName: String): Storage
}
