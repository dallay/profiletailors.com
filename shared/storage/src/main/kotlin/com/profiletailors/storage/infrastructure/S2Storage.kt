package com.profiletailors.storage.infrastructure

import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Cloudflare R2 (S2) provider. 
 * Since it is S3-compatible, it reuses the S3Storage implementation.
 */
class S2Storage(client: S3AsyncClient, bucketName: String, presigner: S3Presigner) : 
    S3Storage(client, bucketName, presigner)
