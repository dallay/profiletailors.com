package com.profiletailors.smp.mediaprovider.unsplash

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.ImportExternalAssetCommand
import com.profiletailors.smp.media.application.ImportExternalAssetHandler
import com.profiletailors.smp.media.application.ImportTestBlobRepository
import com.profiletailors.smp.media.application.ImportTestMediaAssetRepository
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.application.NoopMediaProvider
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class UnsplashWireMockIntegrationTest {

    @Test
    fun `wiremock-backed import reuses canonical asset id on reimport`() = runTest {
        val photoId = "photo-42"
        stubPhoto(photoId)
        stubDownload(photoId, JPEG_BYTES)

        val properties = UnsplashProperties(
            enabled = true,
            accessKey = "test-unsplash-key",
            baseUrl = wireMock.baseUrl(),
            timeout = Duration.ofSeconds(2),
            pageSize = 20,
        )
        val config = MediaProviderConfig()
        val webClient = config.unsplashWebClient(properties)
        val client = config.unsplashClient(webClient, properties)
        val provider = UnsplashAdapter(client)

        val assetRepo = ImportTestMediaAssetRepository()
        val blobRepo = ImportTestBlobRepository()
        val storage = RecordingStorage()
        val handler = ImportExternalAssetHandler(
            mediaAssetRepository = assetRepo,
            workspaceFileBlobRepository = blobRepo,
            storageApplicationService = StorageApplicationService(
                storage = storage,
                eventPublisher = NoopEventPublisher(),
                metrics = NoopStorageObservation(),
            ),
            mediaProvider = NoopMediaProvider(),
            uploadSettings = MediaUploadSettings(5, 200, "bucket"),
            transactionRunner = NoopAtomicTransactionRunner,
        )

        val firstExternal = provider.import("ws-1", ProviderExternalId("unsplash:$photoId"))
        val firstBytes = firstExternal.bytes.toList().flattenToByteArray()
        assertArrayEquals(JPEG_BYTES, firstBytes)
        val firstResult = handler.handle(
            ImportExternalAssetCommand(
                workspaceId = "ws-1",
                externalAsset = firstExternal.copy(bytes = flowOf(firstBytes)),
            ),
        )

        val secondExternal = provider.import("ws-1", ProviderExternalId("unsplash:$photoId"))
        val secondBytes = secondExternal.bytes.toList().flattenToByteArray()
        assertArrayEquals(JPEG_BYTES, secondBytes)
        val secondResult = handler.handle(
            ImportExternalAssetCommand(
                workspaceId = "ws-1",
                externalAsset = secondExternal.copy(bytes = flowOf(secondBytes)),
            ),
        )

        assertFalse(firstResult.deduped)
        assertTrue(secondResult.deduped)
        assertEquals(firstResult.assetId, secondResult.assetId)
        wireMock.verify(2, getRequestedFor(urlEqualTo("/photos/$photoId")))
        wireMock.verify(2, getRequestedFor(urlEqualTo("/downloads/$photoId")))
    }

    private fun stubPhoto(photoId: String) {
        val payload = """
            {
              "id": "$photoId",
              "width": 1200,
              "height": 800,
              "color": "#112233",
              "altDescription": "mountain lake",
              "urls": {
                "thumb": "${wireMock.baseUrl()}/images/$photoId-thumb.jpg",
                "full": "${wireMock.baseUrl()}/images/$photoId-full.jpg"
              },
              "links": {
                "html": "${wireMock.baseUrl()}/photos/$photoId",
                "download": "${wireMock.baseUrl()}/downloads/$photoId"
              },
              "user": {
                "name": "Jane Creator",
                "links": {
                  "html": "${wireMock.baseUrl()}/@jane"
                }
              },
              "tags": [
                { "title": "nature" }
              ]
            }
        """.trimIndent()

        wireMock.stubFor(
            get(urlEqualTo("/photos/$photoId"))
                .willReturn(okJson(payload)),
        )
    }

    private fun stubDownload(photoId: String, bytes: ByteArray) {
        wireMock.stubFor(
            get(urlEqualTo("/downloads/$photoId"))
                .willReturn(
                    ok()
                        .withHeader("Content-Type", "image/jpeg")
                        .withHeader("Content-Length", bytes.size.toString())
                        .withBody(bytes),
                ),
        )
    }

    private class RecordingStorage : Storage {
        private val objects = linkedMapOf<String, ByteArray>()

        override suspend fun upload(
            bucket: String,
            key: String,
            content: Flow<ByteArray>,
            metadata: Map<String, String>,
        ) {
            val chunks = mutableListOf<ByteArray>()
            content.collect { chunks += it }
            objects[key] = chunks.flatMap { it.toList() }.toByteArray()
        }

        override fun download(bucket: String, key: String): Flow<ByteArray> = flowOf(objects.getValue(key))

        override suspend fun delete(bucket: String, key: String) {
            objects.remove(key)
        }

        override suspend fun list(bucket: String, prefix: String): List<String> =
            objects.keys.filter { it.startsWith(prefix) }

        override suspend fun exists(bucket: String, key: String): Boolean = objects.containsKey(key)

        override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
            objects[destKey] = objects.getValue(sourceKey)
        }
    }

    private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    private class NoopEventPublisher : EventPublisher<BaseDomainEvent> {
        override suspend fun publish(event: BaseDomainEvent) = Unit
        override suspend fun publish(events: List<BaseDomainEvent>) = Unit
    }

    private class NoopStorageObservation : StorageObservation {
        override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) = Unit
        override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) = Unit
        override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) = Unit
        override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) = Unit
        override fun recordError(operation: String, provider: String, bucket: String, errorType: String) = Unit
        override fun recordPresignedUrlGenerated(provider: String, success: Boolean) = Unit
        override suspend fun <T : Any> recordOperationTime(
            operation: String,
            provider: String,
            action: suspend () -> T,
        ): T = action()
    }

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build()

        private val JPEG_BYTES = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xE0.toByte(),
            0x01,
            0x02,
            0x03,
            0x04,
        )
    }
}

private fun List<ByteArray>.flattenToByteArray(): ByteArray = flatMap { it.toList() }.toByteArray()
