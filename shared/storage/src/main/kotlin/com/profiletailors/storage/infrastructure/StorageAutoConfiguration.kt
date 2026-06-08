package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.BucketNotFoundException
import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
open class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    open fun defaultStorage(storageProperties: StorageProperties, bucketRegistry: BucketRegistry): Storage {
        return bucketRegistry.getStorage(storageProperties.default)
    }

    @Bean
    open fun bucketRegistry(storageProperties: StorageProperties): BucketRegistry {
        val map = mutableMapOf<String, Storage>()
        storageProperties.providers.forEach { (name, config) ->
            map[name] = createProvider(config)
        }

        // Validate default provider exists
        val defaultName = storageProperties.default
        if (defaultName.isNotBlank() && !map.containsKey(defaultName)) {
            throw IllegalStateException(
                "Configured default storage provider '$defaultName' not found. " +
                "Available providers: ${map.keys.joinToString(", ")}"
            )
        }

        return InMemoryBucketRegistry(map)
    }

    private fun createProvider(config: ProviderConfig): Storage {
        return when (config.type) {
            "local" -> {
                val basePath = config.basePath ?: System.getProperty("java.io.tmpdir")
                LocalFilesystemStorage(Path.of(basePath))
            }
            "s3", "s2" -> {
                val bucket = config.bucket 
                    ?: throw IllegalArgumentException("Bucket name is required for S3/S2")
                val region = config.region ?: "us-east-1"
                val endpoint = config.endpoint

                // Use AWS SDK default credentials chain (IAM roles, env vars, etc.)
                // This is more secure than explicit credentials in configuration
                val clientBuilder = S3AsyncClient.builder()
                    .region(Region.of(region))

                val presignerBuilder = S3Presigner.builder()
                    .region(Region.of(region))

                // Only override endpoint if explicitly configured (for S2/MinIO/etc)
                if (!endpoint.isNullOrBlank()) {
                    val uri = URI.create(endpoint)
                    clientBuilder.endpointOverride(uri)
                    presignerBuilder.endpointOverride(uri)
                }

                if (config.type == "s2") {
                    S2Storage(clientBuilder.build(), bucket, presignerBuilder.build())
                } else {
                    S3Storage(clientBuilder.build(), bucket, presignerBuilder.build())
                }
            }
            else -> throw IllegalArgumentException("Unknown storage provider type: ${config.type}")
        }
    }
}

class InMemoryBucketRegistry(private val providers: Map<String, Storage>) : BucketRegistry {
    override fun getStorage(bucketName: String): Storage = providers[bucketName]
        ?: throw BucketNotFoundException("Bucket not found: $bucketName")
}
