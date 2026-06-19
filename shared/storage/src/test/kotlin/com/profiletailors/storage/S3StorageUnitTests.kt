package com.profiletailors.storage

import com.profiletailors.storage.domain.StorageAccessDeniedException
import com.profiletailors.storage.domain.StorageConnectionException
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.domain.StorageSecurityException
import com.profiletailors.storage.domain.StorageServiceException
import com.profiletailors.storage.infrastructure.S3Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.ResponsePublisher
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class S3StorageUnitTests {

    @Test
    fun `presignGet returns presigned url`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "invoices/1.pdf"
        val storage = S3Storage(client, bucket, presigner)

        val presigned = mockk<PresignedGetObjectRequest>()
        io.mockk.every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } returns presigned
        io.mockk.every { presigned.url() } returns java.net.URL("https://example.com/download")

        val url = runBlocking { storage.presignGet(bucket, key, 600) }
        assertEquals("https://example.com/download", url)
        io.mockk.verify { presigner.presignGetObject(any<GetObjectPresignRequest>()) }
    }

    @Test
    fun `download uses client and returns bytes`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val responsePublisher = mockk<ResponsePublisher<GetObjectResponse>>()
        val future = java.util.concurrent.CompletableFuture.completedFuture(responsePublisher)
        
        io.mockk.every { client.getObject(any<GetObjectRequest>(), any<AsyncResponseTransformer<GetObjectResponse, ResponsePublisher<GetObjectResponse>>>()) } returns future
        
        // Mock publisher to emit one byte buffer
        val byteBuffer = ByteBuffer.wrap("hello".toByteArray())
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

        val emitted = runBlocking {
            storage.download(bucket, key).toList().fold(ByteArray(0)) { acc: ByteArray, bytes: ByteArray -> acc + bytes }
        }
        assertEquals("hello", String(emitted))
    }

    @Test
    fun `upload calls putObject`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val putResp = PutObjectResponse.builder().eTag("etag").build()
        val future = java.util.concurrent.CompletableFuture.completedFuture(putResp)
        io.mockk.coEvery { client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) } returns future

        runBlocking {
            storage.upload(bucket, key, kotlinx.coroutines.flow.flow { emit("hello".toByteArray()) })
        }

        io.mockk.coVerify { client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) }
    }

    // --- Exception mapping tests (exercises mapToStorageException) ---

    @Test
    fun `presignGet throws StorageAccessDeniedException on 403`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val s3Exception = S3Exception.builder().statusCode(403).message("Access Denied").build()
        every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } throws s3Exception

        val ex = assertThrows<StorageAccessDeniedException> {
            runBlocking { storage.presignGet(bucket, key, 600) }
        }
        kotlin.test.assertTrue(ex.message!!.contains("access denied", ignoreCase = true))
    }

    @Test
    fun `presignGet throws StorageConnectionException on 503`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val s3Exception = S3Exception.builder().statusCode(503).message("Service Unavailable").build()
        every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } throws s3Exception

        assertThrows<StorageConnectionException> {
            runBlocking { storage.presignGet(bucket, key, 600) }
        }
    }

    @Test
    fun `presignGet throws StorageServiceException on unknown S3Exception`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val s3Exception = S3Exception.builder().statusCode(500).message("Internal Error").build()
        every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } throws s3Exception

        assertThrows<StorageServiceException> {
            runBlocking { storage.presignGet(bucket, key, 600) }
        }
    }

    @Test
    fun `presignGet throws StorageServiceException on SdkException`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val sdkException: SdkException = SdkException.builder().message("Network error").build()
        every { presigner.presignGetObject(any<GetObjectPresignRequest>()) } throws sdkException

        assertThrows<StorageServiceException> {
            runBlocking { storage.presignGet(bucket, key, 600) }
        }
    }

    @Test
    fun `exists returns false on NoSuchKeyException`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "missing.txt"
        val storage = S3Storage(client, bucket, presigner)

        val noSuchKey = NoSuchKeyException.builder().message("The specified key does not exist").build()
        val future = CompletableFuture<HeadObjectResponse>()
        future.completeExceptionally(noSuchKey)
        every { client.headObject(any<HeadObjectRequest>()) } returns future

        val result = runBlocking { storage.exists(bucket, key) }
        kotlin.test.assertFalse(result)
    }

    @Test
    fun `exists throws StorageAccessDeniedException on 403`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "secret.txt"
        val storage = S3Storage(client, bucket, presigner)

        val s3Exception = S3Exception.builder().statusCode(403).message("Access Denied").build()
        val future = CompletableFuture<HeadObjectResponse>()
        future.completeExceptionally(s3Exception)
        every { client.headObject(any<HeadObjectRequest>()) } returns future

        assertThrows<StorageAccessDeniedException> {
            runBlocking { storage.exists(bucket, key) }
        }
    }

    @Test
    fun `exists throws StorageConnectionException on 503`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "unavailable.txt"
        val storage = S3Storage(client, bucket, presigner)

        val s3Exception = S3Exception.builder().statusCode(503).message("Service Unavailable").build()
        val future = CompletableFuture<HeadObjectResponse>()
        future.completeExceptionally(s3Exception)
        every { client.headObject(any<HeadObjectRequest>()) } returns future

        assertThrows<StorageConnectionException> {
            runBlocking { storage.exists(bucket, key) }
        }
    }

    // --- AbstractS3CompatibleStorage tests ---

    @Test
    fun `S3Storage init should throw IllegalArgumentException for blank bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()

        val exception = assertThrows<IllegalArgumentException> {
            S3Storage(client, "   ", presigner)
        }
        kotlin.test.assertTrue(exception.message!!.contains("bucketName cannot be blank"))
    }

    @Test
    fun `S3Storage init should throw IllegalArgumentException for zero timeout`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()

        val exception = assertThrows<IllegalArgumentException> {
            S3Storage(client, "bucket", presigner, 0)
        }
        kotlin.test.assertTrue(exception.message!!.contains("timeoutSeconds must be positive"))
    }

    @Test
    fun `S3Storage init should throw IllegalArgumentException for negative timeout`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()

        val exception = assertThrows<IllegalArgumentException> {
            S3Storage(client, "bucket", presigner, -1)
        }
        kotlin.test.assertTrue(exception.message!!.contains("timeoutSeconds must be positive"))
    }

    // --- Path traversal validation tests ---

    @Test
    fun `upload throws StorageSecurityException on path traversal with double dot forward slash`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.upload("bucket", "../etc/passwd", kotlinx.coroutines.flow.flowOf("data".toByteArray()))
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `upload throws StorageSecurityException on path traversal with double dot backslash`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.upload("bucket", "..\\etc\\passwd", kotlinx.coroutines.flow.flowOf("data".toByteArray()))
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `download throws StorageSecurityException on path traversal`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.download("bucket", "../../secrets").toList()
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `delete throws StorageSecurityException on path traversal`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.delete("bucket", "uploads/../../../etc/passwd")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `exists throws StorageSecurityException on path traversal`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.exists("bucket", "../secrets")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `list throws StorageSecurityException on path traversal in prefix`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.list("bucket", "../../secrets")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    @Test
    fun `presignGet throws StorageSecurityException on path traversal`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val exception = assertThrows<StorageSecurityException> {
            runBlocking {
                storage.presignGet("bucket", "uploads/../../../secrets", 600)
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("path traversal"))
    }

    // --- delete method tests ---

    @Test
    fun `delete calls deleteObject on client`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "file.txt"
        val storage = S3Storage(client, bucket, presigner)

        val deleteResp = DeleteObjectResponse.builder().build()
        val future = CompletableFuture.completedFuture(deleteResp)
        io.mockk.coEvery { client.deleteObject(any<DeleteObjectRequest>()) } returns future

        runBlocking {
            storage.delete(bucket, key)
        }

        io.mockk.coVerify { client.deleteObject(any<DeleteObjectRequest>()) }
    }

    @Test
    fun `delete throws StorageObjectNotFoundException on NoSuchKeyException`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val noSuchKey = NoSuchKeyException.builder().message("Key 'missing.txt' does not exist").build()
        val future = CompletableFuture<DeleteObjectResponse>()
        future.completeExceptionally(noSuchKey)
        io.mockk.every { client.deleteObject(any<DeleteObjectRequest>()) } returns future

        assertThrows<StorageObjectNotFoundException> {
            runBlocking { storage.delete("bucket", "missing.txt") }
        }
    }

    // --- list method tests ---

    @Test
    fun `list returns all keys without pagination`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val storage = S3Storage(client, bucket, presigner)

        val s3Object1 = S3Object.builder().key("file1.txt").lastModified(Instant.now()).size(100L).build()
        val s3Object2 = S3Object.builder().key("file2.txt").lastModified(Instant.now()).size(200L).build()

        val listResp = ListObjectsV2Response.builder()
            .contents(s3Object1, s3Object2)
            .isTruncated(false)
            .build()
        val future = CompletableFuture.completedFuture(listResp)
        every { client.listObjectsV2(any<ListObjectsV2Request>()) } returns future

        val result = runBlocking { storage.list(bucket, "uploads/") }

        assertEquals(listOf("file1.txt", "file2.txt"), result)
    }

    @Test
    fun `list handles pagination correctly`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val storage = S3Storage(client, bucket, presigner)

        val s3Object1 = S3Object.builder().key("file1.txt").lastModified(Instant.now()).size(100L).build()
        val s3Object2 = S3Object.builder().key("file2.txt").lastModified(Instant.now()).size(200L).build()

        // First page - truncated
        val listResp1 = ListObjectsV2Response.builder()
            .contents(s3Object1)
            .isTruncated(true)
            .nextContinuationToken("token123")
            .build()

        // Second page - not truncated
        val listResp2 = ListObjectsV2Response.builder()
            .contents(s3Object2)
            .isTruncated(false)
            .build()

        val future1: CompletableFuture<ListObjectsV2Response> = CompletableFuture.completedFuture(listResp1)
        val future2: CompletableFuture<ListObjectsV2Response> = CompletableFuture.completedFuture(listResp2)

        every { client.listObjectsV2(match<ListObjectsV2Request> { it.continuationToken() == null }) } returns future1
        every { client.listObjectsV2(match<ListObjectsV2Request> { it.continuationToken() == "token123" }) } returns future2

        val result = runBlocking { storage.list(bucket, "uploads/") }

        assertEquals(listOf("file1.txt", "file2.txt"), result)
    }

    @Test
    fun `list throws StorageObjectNotFoundException on NoSuchKeyException`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val noSuchKey = NoSuchKeyException.builder().message("Key 'prefix' does not exist").build()
        val future = CompletableFuture<ListObjectsV2Response>()
        future.completeExceptionally(noSuchKey)

        every { client.listObjectsV2(any<ListObjectsV2Request>()) } returns future

        assertThrows<StorageObjectNotFoundException> {
            runBlocking { storage.list("bucket", "prefix") }
        }
    }

    // --- exists method tests ---

    @Test
    fun `exists returns true when object exists`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "existing.txt"
        val storage = S3Storage(client, bucket, presigner)

        val headResp = HeadObjectResponse.builder().contentLength(100L).lastModified(Instant.now()).build()
        val future = CompletableFuture.completedFuture(headResp)
        every { client.headObject(any<HeadObjectRequest>()) } returns future

        val result = runBlocking { storage.exists(bucket, key) }
        kotlin.test.assertTrue(result)
    }

    // --- bucket mismatch tests ---

    @Test
    fun `upload throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.upload("other-bucket", "key", kotlinx.coroutines.flow.flowOf("data".toByteArray()))
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    @Test
    fun `download throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.download("other-bucket", "key").toList()
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    @Test
    fun `delete throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.delete("other-bucket", "key")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    @Test
    fun `exists throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.exists("other-bucket", "key")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    @Test
    fun `list throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.list("other-bucket", "prefix")
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    @Test
    fun `presignGet throws exception for wrong bucket`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "my-bucket", presigner)

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                storage.presignGet("other-bucket", "key", 600)
            }
        }
        kotlin.test.assertTrue(exception.message!!.contains("Bucket mismatch"))
    }

    // --- empty content upload test ---

    @Test
    fun `upload handles empty content`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val bucket = "attachments"
        val key = "empty.txt"
        val storage = S3Storage(client, bucket, presigner)

        val putResp = PutObjectResponse.builder().eTag("etag").build()
        val future = CompletableFuture.completedFuture(putResp)
        io.mockk.coEvery { client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) } returns future

        runBlocking {
            storage.upload(bucket, key, kotlinx.coroutines.flow.flowOf())
        }

        io.mockk.coVerify { client.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) }
    }

    // --- SdkException tests ---

    @Test
    fun `exists throws StorageServiceException on generic SdkException`() {
        val client = mockk<S3AsyncClient>()
        val presigner = mockk<S3Presigner>()
        val storage = S3Storage(client, "bucket", presigner)

        val sdkException: SdkException = SdkException.builder().message("Network error").build()
        val future = CompletableFuture<HeadObjectResponse>()
        future.completeExceptionally(sdkException)
        every { client.headObject(any<HeadObjectRequest>()) } returns future

        assertThrows<StorageServiceException> {
            runBlocking { storage.exists("bucket", "key") }
        }
    }
}
