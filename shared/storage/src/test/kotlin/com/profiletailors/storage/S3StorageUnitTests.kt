package com.profiletailors.storage

import com.profiletailors.storage.infrastructure.S3Storage
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.ByteBuffer
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import kotlin.test.assertEquals

import software.amazon.awssdk.core.async.ResponsePublisher
import software.amazon.awssdk.services.s3.model.GetObjectResponse

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
}
