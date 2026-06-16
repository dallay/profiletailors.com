package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.PresignableStorage
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Cloudflare R2 (S2) storage adapter.
 *
 * R2 is S3-compatible, so this adapter delegates to [S3Storage] with the
 * appropriate R2 endpoint configuration. Uses interface delegation via `by` to
 * avoid tight coupling to S3Storage implementation details.
 *
 * The R2-specific configuration (endpoint, region) should be applied when
 * building the [S3AsyncClient] and [S3Presigner] instances passed to this class.
 *
 * Example configuration:
 * ```kotlin
 * val client = S3AsyncClient.builder()
 *     .region(Region.US_EAST_1)
 *     .endpointOverride(URI.create("https://xxx.r2.cloudflarestorage.com"))
 *     .credentialsProvider(StaticCredentialsProvider.create(
 *         AwsBasicCredentials.create(accessKeyId, secretAccessKey)
 *     ))
 *     .build()
 * ```
 *
 * @param client The R2-configured S3 async client
 * @param bucketName The default R2 bucket name
 * @param presigner The R2-configured S3 presigner
 * @param timeoutSeconds Timeout for operations in seconds
 */
class S2Storage(
    client: S3AsyncClient,
    bucketName: String,
    presigner: S3Presigner,
    timeoutSeconds: Long = 30
) : PresignableStorage by S3Storage(client, bucketName, presigner, timeoutSeconds)
