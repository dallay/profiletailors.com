package com.profiletailors.storage.domain

fun interface BucketRegistry {
    fun getStorage(bucketName: String): Storage
}
