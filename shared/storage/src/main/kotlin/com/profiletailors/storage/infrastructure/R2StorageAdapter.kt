package com.profiletailors.storage.infrastructure

import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Cloudflare R2 storage adapter.
 *
 * All logic is inherited from [AbstractS3CompatibleStorage].
 * This class only adds R2-specific account ID validation.
 */
class R2StorageAdapter(
    client: S3AsyncClient,
    bucketName: String,
    presigner: S3Presigner,
    private val accountId: String
) : AbstractS3CompatibleStorage(client, bucketName, presigner) {

    init {
        require(accountId.isNotBlank()) { "accountId cannot be blank for R2" }
    }
}