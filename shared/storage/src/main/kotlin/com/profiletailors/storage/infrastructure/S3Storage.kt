package com.profiletailors.storage.infrastructure

import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3-compatible object storage adapter for AWS S3.
 *
 * All logic is inherited from [AbstractS3CompatibleStorage].
 * This class only configures the S3 client and provides type-specific documentation.
 */
class S3Storage(
    client: S3AsyncClient,
    bucketName: String,
    presigner: S3Presigner
) : AbstractS3CompatibleStorage(client, bucketName, presigner)