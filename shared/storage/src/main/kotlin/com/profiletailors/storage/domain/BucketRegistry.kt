package com.profiletailors.storage.domain

interface BucketRegistry {
    fun getStorage(bucketName: String): Storage
}