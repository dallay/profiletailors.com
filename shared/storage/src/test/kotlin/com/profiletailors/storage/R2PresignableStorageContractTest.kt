package com.profiletailors.storage

import com.profiletailors.storage.domain.PresignableStorage
import com.profiletailors.storage.infrastructure.R2Storage
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.nio.file.Path

/**
 * Presignable storage contract tests for R2Storage using LocalStack.
 *
 * These tests verify that R2Storage satisfies the [PresignableStorageContractTest]
 * contract for presigned URL generation.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
class R2PresignableStorageContractTest : PresignableStorageContractTest() {

    companion object {
        const val TEST_ACCOUNT_ID = "testaccount123"

        @Container
        val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.0"))
            .withServices(LocalStackContainer.Service.S3)

        lateinit var r2Client: S3AsyncClient
        lateinit var r2Presigner: S3Presigner

        @JvmStatic
        @BeforeAll
        fun setup() {
            r2Client = S3AsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
                    ),
                )
                // LocalStack rejects "auto" as a region; use the LocalStack-configured region
                // (matches S3StorageIntegrationTest pattern). forcePathStyle(true) keeps the
                // behavior R2-compatible.
                .region(Region.of(localstack.region))
                .forcePathStyle(true)
                .build()

            r2Presigner = S3Presigner.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
                    ),
                )
                .region(Region.of(localstack.region))
                .build()

            r2Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build()).join()
        }
    }

    override fun createStorage(tempDir: Path): PresignableStorage =
        R2Storage(r2Client, TEST_BUCKET, r2Presigner, TEST_ACCOUNT_ID)
}
