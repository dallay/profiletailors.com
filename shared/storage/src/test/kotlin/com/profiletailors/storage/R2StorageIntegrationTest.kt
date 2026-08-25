package com.profiletailors.storage

import com.profiletailors.storage.infrastructure.R2Storage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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

/**
 * Integration tests for R2Storage using LocalStack.
 *
 * Note: LocalStack does not fully emulate R2's behavior, but we can test
 * the S3-compatible operations using LocalStack in path-style mode.
 * R2-specific behavior (account ID validation, endpoint format) is
 * tested in R2StorageUnitTests.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
@DisplayName("R2Storage Integration Tests")
class R2StorageIntegrationTest {

    companion object {
        // Use a fake account ID for LocalStack - in real R2 this would be the actual account ID
        const val TEST_ACCOUNT_ID = "testaccount123"
        const val BUCKET_NAME = "r2-test-bucket"

        @Container
        val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.0"))
            .withServices(LocalStackContainer.Service.S3)

        lateinit var r2Client: S3AsyncClient
        lateinit var r2Presigner: S3Presigner

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Configure client similar to R2: path-style, localstack region.
            // LocalStack rejects "auto" as a region name, so we use the LocalStack-configured
            // region here while keeping forcePathStyle(true) for R2-compatible behavior.
            r2Client = S3AsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
                    ),
                )
                .region(Region.of(localstack.region))
                .forcePathStyle(true) // R2 requires path-style
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

            r2Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()).join()
        }
    }

    @Test
    fun `end-to-end upload and download`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)
        val key = "test/upload-download.txt"
        val content = "R2 integration test content".toByteArray()

        storage.upload(BUCKET_NAME, key, flowOf(content))

        val downloaded = storage.download(BUCKET_NAME, key).toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals("R2 integration test content", String(downloaded))
    }

    @Test
    fun `exists returns true for existing object`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)
        val key = "test/exists-true.txt"

        storage.upload(BUCKET_NAME, key, flowOf("exists".toByteArray()))

        assertTrue(storage.exists(BUCKET_NAME, key))
    }

    @Test
    fun `exists returns false for non-existent object`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)

        assertFalse(storage.exists(BUCKET_NAME, "non-existent-key.txt"))
    }

    @Test
    fun `delete removes object and exists returns false`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)
        val key = "test/delete-verify.txt"

        storage.upload(BUCKET_NAME, key, flowOf("to-be-deleted".toByteArray()))
        assertTrue(storage.exists(BUCKET_NAME, key))

        storage.delete(BUCKET_NAME, key)

        assertFalse(storage.exists(BUCKET_NAME, key))
    }

    @Test
    fun `list returns all objects with prefix`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)

        storage.upload(BUCKET_NAME, "list-prefix/file1.txt", flowOf("1".toByteArray()))
        storage.upload(BUCKET_NAME, "list-prefix/file2.txt", flowOf("2".toByteArray()))
        storage.upload(BUCKET_NAME, "other/file3.txt", flowOf("3".toByteArray()))

        val result = storage.list(BUCKET_NAME, "list-prefix/")

        assertEquals(2, result.size)
        assertTrue(result.contains("list-prefix/file1.txt"))
        assertTrue(result.contains("list-prefix/file2.txt"))
    }

    @Test
    fun `presigned URL generation`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)

        // Upload a file first
        val key = "test/presigned.txt"
        storage.upload(BUCKET_NAME, key, flowOf("presigned content".toByteArray()))

        // Generate presigned URL
        val url = storage.presignGet(BUCKET_NAME, key, 300)

        assertTrue(url.isNotEmpty())
        assertTrue(url.contains(BUCKET_NAME))
        assertTrue(url.contains(key))
    }

    @Test
    fun `upload with metadata`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)
        val key = "test/metadata.txt"
        val metadata = mapOf("content-type" to "text/plain", "author" to "test")

        storage.upload(BUCKET_NAME, key, flowOf("metadata content".toByteArray()), metadata)

        // Verify download works
        val downloaded = storage.download(BUCKET_NAME, key).toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals("metadata content", String(downloaded))
    }

    @Test
    fun `concurrent uploads`() = runBlocking {
        val storage = R2Storage(r2Client, BUCKET_NAME, r2Presigner, TEST_ACCOUNT_ID)

        // Upload multiple files concurrently
        val keys = (1..5).map { "concurrent/file$it.txt" }
        coroutineScope {
            keys.map { key ->
                async {
                    storage.upload(BUCKET_NAME, key, flowOf("content of $key".toByteArray()))
                }
            }.awaitAll()
        }

        // Verify all exist
        keys.forEach { key ->
            assertTrue(storage.exists(BUCKET_NAME, key), "Expected $key to exist")
        }
    }
}
