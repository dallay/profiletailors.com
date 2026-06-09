package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.BucketNotFoundException
import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import org.slf4j.LoggerFactory
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

    companion object {
        private val logger = LoggerFactory.getLogger(StorageAutoConfiguration::class.java)
        
        /**
         * R2 endpoint format: https://{accountId}.r2.cloudflarestorage.com
         */
        private fun r2Endpoint(accountId: String): URI = 
            URI.create("https://$accountId.r2.cloudflarestorage.com")
        
        /**
         * Extracts account ID from R2 endpoint if present.
         */
        private fun extractAccountId(endpoint: String?): String? {
            if (endpoint.isNullOrBlank()) return null
            val match = Regex("""https?://([^.]+)\.r2\.cloudflarestorage\.com""").find(endpoint)
            return match?.groupValues?.get(1)
        }
    }

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
            "s3" -> {
                // AWS S3 - use default endpoint
                val bucket = config.bucket 
                    ?: throw IllegalArgumentException("Bucket name is required for S3")
                val region = config.region ?: "us-east-1"

                val clientBuilder = S3AsyncClient.builder()
                    .region(Region.of(region))

                val presignerBuilder = S3Presigner.builder()
                    .region(Region.of(region))

                S3Storage(clientBuilder.build(), bucket, presignerBuilder.build())
            }
            "s2" -> {
                // s2 is deprecated alias for R2
                logger.warn("Storage type 's2' is deprecated. Use 'r2' for Cloudflare R2.")
                createR2Storage(config)
            }
            "r2" -> createR2Storage(config)
            else -> throw IllegalArgumentException("Unknown storage provider type: ${config.type}")
        }
    }

    /**
     * Creates an R2StorageAdapter for Cloudflare R2.
     */
    private fun createR2Storage(config: ProviderConfig): R2StorageAdapter {
        val bucket = config.bucket 
            ?: throw IllegalArgumentException("Bucket name is required for R2")
        
        // Get account ID from config or extract from endpoint
        val accountId = config.accountId ?: extractAccountId(config.endpoint)
            ?: throw IllegalArgumentException(
                "accountId is required for R2. " +
                "Provide it in config or include it in the endpoint URL: " +
                "https://{accountId}.r2.cloudflarestorage.com"
            )
        
        // R2 requires region "auto"
        val region = Region.of(config.region ?: "auto")
        
        // Build R2-specific client
        val clientBuilder = S3AsyncClient.builder()
            .region(region)
            .endpointOverride(r2Endpoint(accountId))
            .forcePathStyle(true)  // R2 requires path-style
        
        val presignerBuilder = S3Presigner.builder()
            .region(region)
            .endpointOverride(r2Endpoint(accountId))
        
        return R2StorageAdapter(
            clientBuilder.build(),
            bucket,
            presignerBuilder.build(),
            accountId
        )
    }
}

class InMemoryBucketRegistry(private val providers: Map<String, Storage>) : BucketRegistry {
    override fun getStorage(bucketName: String): Storage = providers[bucketName]
        ?: throw BucketNotFoundException("Bucket not found: $bucketName")
}
