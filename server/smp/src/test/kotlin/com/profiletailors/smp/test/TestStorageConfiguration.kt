package com.profiletailors.smp.test

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObjectNotFoundException
import com.profiletailors.storage.infrastructure.metrics.StorageMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Provides a [StorageApplicationService] stub for H2 test contexts where the
 * real storage infrastructure (Cloudflare R2, AWS S3) is not available.
 *
 * This configuration is imported by full-context tests that need the Spring
 * application context to start without real cloud storage.
 *
 * The stub uses an in-memory map as storage — uploads and deletes are recorded
 * but do not persist across test runs.
 */
@TestConfiguration
class TestStorageConfiguration {

    /**
     * In-memory [Storage] stub for tests.
     * Records upload/delete keys but performs no actual I/O.
     */
    @Bean
    @Primary
    fun inMemoryFakeStorage(): Storage = object : Storage {
        private val objects = mutableMapOf<String, ByteArray>()

        override suspend fun upload(
            bucket: String,
            key: String,
            content: Flow<ByteArray>,
            metadata: Map<String, String>,
        ) {
            val chunks = mutableListOf<ByteArray>()
            content.collect { chunks += it }
            objects["$bucket/$key"] = chunks.flatMap { it.toList() }.toByteArray()
        }

        override fun download(bucket: String, key: String): Flow<ByteArray> {
            val data = objects["$bucket/$key"]
                ?: throw StorageObjectNotFoundException(bucket, key)
            return flowOf(data)
        }

        override suspend fun delete(bucket: String, key: String) {
            objects.remove("$bucket/$key")
        }

        override suspend fun list(bucket: String, prefix: String): List<String> =
            objects.keys.filter { it.startsWith("$bucket/$prefix") }

        override suspend fun exists(bucket: String, key: String): Boolean =
            objects.containsKey("$bucket/$key")

        override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
            val data = objects["$bucket/$sourceKey"]
            if (data != null) {
                objects["$bucket/$destKey"] = data
            }
        }
    }

    /**
     * Bucket registry that resolves the standard `attachments` bucket to the in-memory storage.
     *
     * This keeps full-context tests (including Actuator health) aligned with production code,
     * where media readiness probes go through [BucketRegistry] rather than the application service.
     */
    @Bean
    @Primary
    fun testBucketRegistry(inMemoryFakeStorage: Storage): BucketRegistry = BucketRegistry { bucketName ->
        when (bucketName) {
            "attachments" -> inMemoryFakeStorage
            else -> inMemoryFakeStorage
        }
    }

    /**
     * No-op [EventPublisher] for tests — events are silently discarded.
     */
    @Bean
    fun noOpEventPublisher(): EventPublisher<BaseDomainEvent> = object : EventPublisher<BaseDomainEvent> {
        override suspend fun publish(event: BaseDomainEvent) {
            // discard
        }
    }

    /**
     * Provides a [StorageApplicationService] backed by the in-memory stub.
     * This bean satisfies [StaleAssetReconciler] and [UploadAssetHandler]
     * dependencies in full-context H2 tests.
     */
    @Bean
    @Primary
    fun storageApplicationService(
        inMemoryFakeStorage: Storage,
        noOpEventPublisher: EventPublisher<BaseDomainEvent>,
    ): StorageApplicationService = StorageApplicationService(
        storage = inMemoryFakeStorage,
        eventPublisher = noOpEventPublisher,
        metrics = StorageMetrics(SimpleMeterRegistry()),
    )
}
