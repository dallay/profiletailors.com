package com.profiletailors.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.nio.ByteBuffer
import java.time.Duration
import org.reactivestreams.Publisher

import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.core.exception.SdkException

open class S3Storage(private val client: S3AsyncClient, private val bucketName: String, private val presigner: S3Presigner) : Storage {

    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        try {
            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .build()

            val publisher: Publisher<ByteBuffer> = content
                .map { ByteBuffer.wrap(it) }
                .asPublisher()

            val body = AsyncRequestBody.fromPublisher(publisher)

            client.putObject(request, body).await()
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to upload '$key' to bucket '$bucketName'", e)
        }
    }

    override fun download(bucket: String, key: String): Flow<ByteArray> = channelFlow {
        try {
            val request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val transformer = AsyncResponseTransformer.toPublisher<GetObjectResponse>()
            val future = client.getObject(request, transformer)

            val responsePublisher = future.await()

            responsePublisher.asFlow().collect { byteBuffer ->
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                send(bytes)
            }
        } catch (e: NoSuchKeyException) {
            throw StorageObjectNotFoundException(bucketName, key)
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to download '$key' from bucket '$bucketName'", e)
        }
    }

    override suspend fun delete(bucket: String, key: String) {
        try {
            val req = software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            client.deleteObject(req).await()
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to delete '$key' from bucket '$bucketName'", e)
        }
    }

    override suspend fun list(bucket: String, prefix: String): List<String> {
        try {
            val results = mutableListOf<String>()
            var isTruncated: Boolean
            var continuationToken: String? = null

            do {
                val reqBuilder = software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)

                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken)
                }

                val resp = client.listObjectsV2(reqBuilder.build()).await()
                results.addAll(resp.contents().map { it.key() })
                isTruncated = resp.isTruncated
                continuationToken = resp.nextContinuationToken()
            } while (isTruncated)

            return results
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to list objects in bucket '$bucketName' with prefix '$prefix'", e)
        }
    }

    override suspend fun presignGet(bucket: String, key: String, expirySeconds: Long): String {
        try {
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build()

            val presigned = presigner.presignGetObject(presignRequest)
            return presigned.url().toString()
        } catch (e: S3Exception) {
            throw StorageServiceException("Failed to generate presigned URL for '$key' in bucket '$bucketName'", e)
        } catch (e: SdkException) {
            throw StorageServiceException("Failed to generate presigned URL for '$key' in bucket '$bucketName'", e)
        }
    }
}




