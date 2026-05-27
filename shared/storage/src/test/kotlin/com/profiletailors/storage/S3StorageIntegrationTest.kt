package com.profiletailors.storage

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@Testcontainers
@EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
class S3StorageIntegrationTest {

    companion object {
        @Container
        val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.0"))
            .withServices(LocalStackContainer.Service.S3)

        lateinit var s3Client: S3AsyncClient
        lateinit var s3Presigner: S3Presigner
        const val BUCKET_NAME = "test-bucket"

        @JvmStatic
        @BeforeAll
        fun setup() {
            s3Client = S3AsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)
                    )
                )
                .region(Region.of(localstack.region))
                .build()

            s3Presigner = S3Presigner.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)
                    )
                )
                .region(Region.of(localstack.region))
                .build()

            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()).join()
        }
    }

    @Test
    fun `upload and download from localstack`() = runBlocking {
        val storage = S3Storage(s3Client, BUCKET_NAME, s3Presigner)
        val key = "test/streaming.txt"
        val data = "streamed content from localstack".toByteArray()
        
        storage.upload(BUCKET_NAME, key, flowOf(data))
        
        val downloaded = storage.download(BUCKET_NAME, key).toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals("streamed content from localstack", String(downloaded))
    }

    @Test
    fun `list objects in bucket`() = runBlocking {
        val storage = S3Storage(s3Client, BUCKET_NAME, s3Presigner)
        storage.upload(BUCKET_NAME, "list/1.txt", flowOf("1".toByteArray()))
        storage.upload(BUCKET_NAME, "list/2.txt", flowOf("2".toByteArray()))
        
        val list = storage.list(BUCKET_NAME, "list/")
        assertTrue(list.contains("list/1.txt"))
        assertTrue(list.contains("list/2.txt"))
    }

    @Test
    fun `presigned url generation`() = runBlocking {
        val storage = S3Storage(s3Client, BUCKET_NAME, s3Presigner)
        val url = storage.presignGet(BUCKET_NAME, "any.txt", 300)
        assertTrue(url.contains(BUCKET_NAME))
        assertTrue(url.contains("X-Amz-Signature"))
    }
}
