package com.profiletailors.storage.infrastructure

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.BucketNotFoundException
import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.infrastructure.metrics.StorageMetrics
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
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
            try {
                map[name] = createProvider(config)
            } catch (e: Exception) {
                logger.warn(
                    "Failed to create storage provider '$name' (type={}): {}. " +
                    "This provider will not be available.",
                    config.type,
                    e.message,
                )
            }
        }

        // Validate default provider exists; if validation fails, surface a clear error
        val defaultName = storageProperties.default
        if (defaultName.isNotBlank() && !map.containsKey(defaultName)) {
            val available = map.keys.joinToString(", ")
            throw IllegalStateException(
                "Configured default storage provider '$defaultName' not found. " +
                "Available providers: ${if (available.isNotBlank()) available else "(none — all providers failed to initialize)"}. " +
                "Ensure platform.storage.default references a valid, initialized provider."
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

                S3Storage(clientBuilder.build(), bucket, presignerBuilder.build(), config.timeoutSeconds)
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
     *
     * R2 has no AWS credentials chain — the access/secret keys MUST be supplied
     * explicitly via [ProviderConfig.accessKeyId] / [ProviderConfig.secretAccessKey].
     */
    internal fun createR2Storage(config: ProviderConfig): R2StorageAdapter {
        val bucket = config.bucket
            ?: throw IllegalArgumentException("Bucket name is required for R2")

        // Get account ID from config or extract from endpoint
        val accountId = config.accountId ?: extractAccountId(config.endpoint)
            ?: throw IllegalArgumentException(
                "accountId is required for R2. " +
                "Provide it in config or include it in the endpoint URL: " +
                "https://{accountId}.r2.cloudflarestorage.com"
            )

        // R2 has no implicit credentials chain; both keys are mandatory.
        val accessKeyId = config.accessKeyId
        val secretAccessKey = config.secretAccessKey
        require(!(accessKeyId.isNullOrBlank() || secretAccessKey.isNullOrBlank())) {
            "R2 requires both accessKeyId and secretAccessKey in provider config. " +
            "Configure them under platform.storage.providers.{name}.access-key-id " +
            "and platform.storage.providers.{name}.secret-access-key."
        }
        val credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        )

        // R2 requires region "auto"
        val region = Region.of(config.region ?: "auto")

        // Build R2-specific client with explicit credentials
        val clientBuilder = S3AsyncClient.builder()
            .region(region)
            .endpointOverride(r2Endpoint(accountId))
            .credentialsProvider(credentialsProvider)
            .forcePathStyle(true)  // R2 requires path-style

        val presignerBuilder = S3Presigner.builder()
            .region(region)
            .endpointOverride(r2Endpoint(accountId))
            .credentialsProvider(credentialsProvider)

        return R2StorageAdapter(
            clientBuilder.build(),
            bucket,
            presignerBuilder.build(),
            accountId,
            config.timeoutSeconds
        )
    }
}

class InMemoryBucketRegistry(private val providers: Map<String, Storage>) : BucketRegistry {
    override fun getStorage(bucketName: String): Storage = providers[bucketName]
        ?: throw BucketNotFoundException("Bucket not found: $bucketName")
}
