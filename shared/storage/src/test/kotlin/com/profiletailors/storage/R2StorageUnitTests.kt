package com.profiletailors.storage

import com.profiletailors.storage.domain.StorageAccessDeniedException
import com.profiletailors.storage.domain.StorageConnectionException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.infrastructure.R2StorageAdapter
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.ResponsePublisher
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for R2StorageAdapter.
 *
 * These tests verify R2-specific behavior:
 * - R2 endpoint format: https://{accountId}.r2.cloudflarestorage.com
 * - accountId is required for R2 configuration
 * - Standard storage operations (upload, download, delete, list, presignGet)
 */
@DisplayName("R2StorageAdapter")
class R2StorageUnitTests {

    companion object {
        const val TEST_ACCOUNT_ID = "abc123"
        const val TEST_BUCKET = "user-images"
        const val TEST_KEY = "profile/avatar.png"
    }

    @Nested
    @DisplayName("initialization")
    inner class Initialization {

        @Test
        fun `should accept valid accountId and bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()

            // Should not throw
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertTrue(storage is R2StorageAdapter)
        }

        @Test
        fun `should reject blank bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()

            assertFailsWith<IllegalArgumentException> {
                R2StorageAdapter(client, "", presigner, TEST_ACCOUNT_ID)
            }
        }

        @Test
        fun `should reject blank accountId`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()

            assertFailsWith<IllegalArgumentException> {
                R2StorageAdapter(client, TEST_BUCKET, presigner, "")
            }
        }

        @Test
        fun `should reject null-like accountId`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()

            assertFailsWith<IllegalArgumentException> {
                R2StorageAdapter(client, TEST_BUCKET, presigner, "   ")
            }
        }
    }

    @Nested
    @DisplayName("presignGet")
    inner class PresignGet {

        @Test
        fun `should generate presigned URL targeting R2 endpoint`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val expectedDomain = "$TEST_ACCOUNT_ID.r2.cloudflarestorage.com"
            val presignedRequest = mockk<PresignedGetObjectRequest>()
            
            every { 
                presigner.presignGetObject(any<GetObjectPresignRequest>()) 
            } returns presignedRequest
            every { presignedRequest.url() } returns java.net.URL("https://$expectedDomain/$TEST_BUCKET/$TEST_KEY?token=abc")

            val url = runBlocking { storage.presignGet(TEST_BUCKET, TEST_KEY, 600) }

            assertTrue(url.contains(expectedDomain), "URL should contain R2 domain: $expectedDomain")
            assertTrue(url.contains(TEST_BUCKET), "URL should contain bucket: $TEST_BUCKET")
            assertTrue(url.contains(TEST_KEY), "URL should contain key: $TEST_KEY")
            
            verify { presigner.presignGetObject(any<GetObjectPresignRequest>()) }
        }

        @Test
        fun `should use configured expiry duration`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val presignedRequest = mockk<PresignedGetObjectRequest>()
            every { 
                presigner.presignGetObject(any<GetObjectPresignRequest>()) 
            } returns presignedRequest
            every { presignedRequest.url() } returns java.net.URL("https://example.com/url")

            runBlocking { storage.presignGet(TEST_BUCKET, TEST_KEY, 3600) }

            // Verify presign request was called with the correct parameters
            verify { 
                presigner.presignGetObject(
                    match<GetObjectPresignRequest> { req ->
                        req.signatureDuration().seconds == 3600L
                    }
                )
            }
        }

        @Test
        fun `should reject mismatched bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<IllegalArgumentException> {
                runBlocking { storage.presignGet("other-bucket", TEST_KEY, 600) }
            }
        }
    }

    @Nested
    @DisplayName("upload")
    inner class Upload {

        @Test
        fun `should call S3 client putObject with correct parameters`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val putResponse = PutObjectResponse.builder().eTag("etag123").build()
            val future = java.util.concurrent.CompletableFuture.completedFuture(putResponse)
            
            coEvery { 
                client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) 
            } returns future

            val content = flowOf("test content".toByteArray())
            runBlocking { 
                storage.upload(TEST_BUCKET, TEST_KEY, content, mapOf("content-type" to "image/png"))
            }

            coVerify { 
                client.putObject(
                    match<PutObjectRequest> { req ->
                        req.bucket() == TEST_BUCKET && 
                        req.key() == TEST_KEY &&
                        req.metadata()["content-type"] == "image/png"
                    },
                    any<AsyncRequestBody>()
                )
            }
        }

        @Test
        fun `should reject mismatched bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val content = flowOf("test".toByteArray())
            
            assertFailsWith<IllegalArgumentException> {
                runBlocking { storage.upload("wrong-bucket", TEST_KEY, content) }
            }
        }
    }

    @Nested
    @DisplayName("download")
    inner class Download {

        @Test
        fun `should download content from R2`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val responsePublisher = mockk<ResponsePublisher<GetObjectResponse>>()
            val future = java.util.concurrent.CompletableFuture.completedFuture(responsePublisher)
            
            every { 
                client.getObject(any<GetObjectRequest>(), any<AsyncResponseTransformer<GetObjectResponse, ResponsePublisher<GetObjectResponse>>>())
            } returns future

            // Mock the publisher to emit data
            val byteBuffer = ByteBuffer.wrap("test content".toByteArray())
            io.mockk.every { responsePublisher.subscribe(any<org.reactivestreams.Subscriber<in ByteBuffer>>()) } answers {
                val subscriber = it.invocation.args[0] as org.reactivestreams.Subscriber<ByteBuffer>
                subscriber.onSubscribe(object : org.reactivestreams.Subscription {
                    override fun request(n: Long) {
                        subscriber.onNext(byteBuffer)
                        subscriber.onComplete()
                    }
                    override fun cancel() {}
                })
            }

            val result = runBlocking {
                storage.download(TEST_BUCKET, TEST_KEY).toList().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
            }

            assertEquals("test content", String(result))
        }

        @Test
        fun `should reject mismatched bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<IllegalArgumentException> {
                runBlocking { storage.download("wrong-bucket", TEST_KEY).collect {} }
            }
        }
    }

    @Nested
    @DisplayName("delete")
    inner class Delete {

        @Test
        fun `should call S3 client deleteObject`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val future = java.util.concurrent.CompletableFuture.completedFuture(
                software.amazon.awssdk.services.s3.model.DeleteObjectResponse.builder().build()
            )
            coEvery { client.deleteObject(any<DeleteObjectRequest>()) } returns future

            runBlocking { storage.delete(TEST_BUCKET, TEST_KEY) }

            coVerify { 
                client.deleteObject(
                    match<DeleteObjectRequest> { req ->
                        req.bucket() == TEST_BUCKET && req.key() == TEST_KEY
                    }
                )
            }
        }

        @Test
        fun `should reject mismatched bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<IllegalArgumentException> {
                runBlocking { storage.delete("wrong-bucket", TEST_KEY) }
            }
        }
    }

    @Nested
    @DisplayName("list")
    inner class List {

        @Test
        fun `should list objects from R2 bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val listResponse = ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("file1.txt").size(100).lastModified(Instant.now()).build(),
                    S3Object.builder().key("file2.txt").size(200).lastModified(Instant.now()).build()
                )
                .isTruncated(false)
                .build()
            
            val future = java.util.concurrent.CompletableFuture.completedFuture(listResponse)
            coEvery { client.listObjectsV2(any<ListObjectsV2Request>()) } returns future

            val keys = runBlocking { storage.list(TEST_BUCKET, "prefix/") }

            assertEquals(2, keys.size)
            assertTrue(keys.contains("file1.txt"))
            assertTrue(keys.contains("file2.txt"))
        }

        @Test
        fun `should handle empty list`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val listResponse = ListObjectsV2Response.builder()
                .contents(emptyList())
                .isTruncated(false)
                .build()
            
            val future = java.util.concurrent.CompletableFuture.completedFuture(listResponse)
            coEvery { client.listObjectsV2(any<ListObjectsV2Request>()) } returns future

            val keys = runBlocking { storage.list(TEST_BUCKET) }

            assertTrue(keys.isEmpty())
        }

        @Test
        fun `should reject mismatched bucket`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<IllegalArgumentException> {
                runBlocking { storage.list("wrong-bucket") }
            }
        }
    }

    @Nested
    @DisplayName("exists")
    inner class Exists {

        @Test
        fun `should return true when object exists`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val headResponse = HeadObjectResponse.builder().contentLength(1024).build()
            val future = java.util.concurrent.CompletableFuture.completedFuture(headResponse)
            coEvery { client.headObject(any<HeadObjectRequest>()) } returns future

            val result = runBlocking { storage.exists(TEST_BUCKET, TEST_KEY) }

            assertTrue(result)
            coVerify { client.headObject(match<HeadObjectRequest> { req -> req.bucket() == TEST_BUCKET && req.key() == TEST_KEY }) }
        }

        @Test
        fun `should return false when object does not exist`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val future = java.util.concurrent.CompletableFuture.failedFuture<HeadObjectResponse>(
                NoSuchKeyException.builder().message("Not found").build()
            )
            coEvery { client.headObject(any<HeadObjectRequest>()) } returns future

            val result = runBlocking { storage.exists(TEST_BUCKET, TEST_KEY) }

            assertTrue(!result)
        }

        @Test
        fun `should throw StorageAccessDeniedException on access denied`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val accessDenied = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<HeadObjectResponse>(accessDenied)
            coEvery { client.headObject(any<HeadObjectRequest>()) } returns future

            assertFailsWith<StorageAccessDeniedException> {
                runBlocking { storage.exists(TEST_BUCKET, TEST_KEY) }
            }
        }

        @Test
        fun `should throw StorageConnectionException on service unavailable`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            // S3Exception with status code 503 represents ServiceUnavailable
            val serviceUnavailable = S3Exception.builder()
                .message("Service unavailable")
                .statusCode(503)
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<HeadObjectResponse>(serviceUnavailable)
            coEvery { client.headObject(any<HeadObjectRequest>()) } returns future

            assertFailsWith<StorageConnectionException> {
                runBlocking { storage.exists(TEST_BUCKET, TEST_KEY) }
            }
        }
    }

    @Nested
    @DisplayName("path traversal protection")
    inner class PathTraversalProtection {

        @Test
        fun `should reject key with forward path traversal`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.upload(TEST_BUCKET, "../../etc/passwd", flowOf("data".toByteArray())) }
            }
        }

        @Test
        fun `should reject key with backward path traversal`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.download(TEST_BUCKET, "..\\..\\etc\\passwd").collect {} }
            }
        }

        @Test
        fun `should reject path traversal in delete`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.delete(TEST_BUCKET, "../secrets.txt") }
            }
        }

        @Test
        fun `should reject path traversal in list`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.list(TEST_BUCKET, "../..") }
            }
        }

        @Test
        fun `should reject path traversal in presignGet`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val presignedRequest = mockk<PresignedGetObjectRequest>()
            every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } returns presignedRequest
            every { presignedRequest.url() } returns java.net.URL("https://example.com")

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.presignGet(TEST_BUCKET, "../../secret.txt", 600) }
            }
        }

        @Test
        fun `should reject path traversal in exists`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            assertFailsWith<StorageSecurityException> {
                runBlocking { storage.exists(TEST_BUCKET, "..\\..\\etc\\passwd") }
            }
        }
    }

    @Nested
    @DisplayName("error mapping")
    inner class ErrorMapping {

        @Test
        fun `should map AccessDeniedException on upload`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val accessDenied = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<PutObjectResponse>(accessDenied)
            coEvery { client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) } returns future

            assertFailsWith<StorageAccessDeniedException> {
                runBlocking { storage.upload(TEST_BUCKET, TEST_KEY, flowOf("test".toByteArray())) }
            }
        }

        @Test
        fun `should map S3Exception with 503 status to StorageConnectionException on download`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val responsePublisher = mockk<ResponsePublisher<GetObjectResponse>>()
            val future = java.util.concurrent.CompletableFuture.completedFuture(responsePublisher)
            every { client.getObject(any<GetObjectRequest>(), any<AsyncResponseTransformer<GetObjectResponse, ResponsePublisher<GetObjectResponse>>>()) } returns future

            // Mock subscriber to throw S3Exception with status code 503 (ServiceUnavailable)
            val serviceUnavailable = S3Exception.builder()
                .message("Service unavailable")
                .statusCode(503)
                .build()
            io.mockk.every { responsePublisher.subscribe(any<org.reactivestreams.Subscriber<in ByteBuffer>>()) } throws serviceUnavailable

            assertFailsWith<StorageConnectionException> {
                runBlocking { storage.download(TEST_BUCKET, TEST_KEY).collect {} }
            }
        }

        @Test
        fun `should map NoSuchKeyException to StorageObjectNotFoundException on download`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val noSuchKey = NoSuchKeyException.builder()
                .message("The specified key does not exist")
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<ResponsePublisher<GetObjectResponse>>(noSuchKey)
            every {
                client.getObject(any<GetObjectRequest>(), any<AsyncResponseTransformer<GetObjectResponse, ResponsePublisher<GetObjectResponse>>>())
            } returns future

            assertFailsWith<StorageObjectNotFoundException> {
                runBlocking { storage.download(TEST_BUCKET, TEST_KEY).collect {} }
            }
        }

        @Test
        fun `should map AccessDeniedException on delete`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val accessDenied = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<software.amazon.awssdk.services.s3.model.DeleteObjectResponse>(accessDenied)
            coEvery { client.deleteObject(any<DeleteObjectRequest>()) } returns future

            assertFailsWith<StorageAccessDeniedException> {
                runBlocking { storage.delete(TEST_BUCKET, TEST_KEY) }
            }
        }

        @Test
        fun `should map AccessDeniedException on list`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val accessDenied = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build()
            val future = java.util.concurrent.CompletableFuture.failedFuture<ListObjectsV2Response>(accessDenied)
            coEvery { client.listObjectsV2(any<ListObjectsV2Request>()) } returns future

            assertFailsWith<StorageAccessDeniedException> {
                runBlocking { storage.list(TEST_BUCKET, "prefix/") }
            }
        }

        @Test
        fun `should map AccessDeniedException on presignGet`() {
            val client = mockk<S3AsyncClient>()
            val presigner = mockk<S3Presigner>()
            val storage = R2StorageAdapter(client, TEST_BUCKET, presigner, TEST_ACCOUNT_ID)

            val accessDenied = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build()
            every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } throws accessDenied

            assertFailsWith<StorageAccessDeniedException> {
                runBlocking { storage.presignGet(TEST_BUCKET, TEST_KEY, 600) }
            }
        }
    }
}
