package com.profiletailors.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
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

    @Bean
    @ConditionalOnMissingBean
    open fun defaultStorage(storageProperties: StorageProperties, bucketRegistry: BucketRegistry): Storage {
        return bucketRegistry.getStorage(storageProperties.default)
    }

    @Bean
    open fun bucketRegistry(storageProperties: StorageProperties): BucketRegistry {
        val map = mutableMapOf<String, Storage>()
        storageProperties.providers.forEach { (name, props) ->
            map[name] = createProvider(props)
        }

        // Ensure default storage is registered
        val defaultName = storageProperties.default
        if (defaultName.isNotBlank() && !map.containsKey(defaultName)) {
            // Create default local storage if not already registered
            map[defaultName] = LocalFilesystemStorage(Path.of(System.getProperty("java.io.tmpdir")))
        }

        return InMemoryBucketRegistry(map)
    }

    private fun createProvider(props: Map<String, String>): Storage {
        return when (val type = props["type"]) {
            "local" -> {
                val basePath = props["base-path"] ?: System.getProperty("java.io.tmpdir")
                LocalFilesystemStorage(Path.of(basePath))
            }
            "s3", "s2" -> {
                val bucket = props["bucket"] ?: throw IllegalArgumentException("Bucket name is required for S3/S2")
                val region = props["region"] ?: "us-east-1"
                val accessKey = props["access-key-id"]
                val secretKey = props["secret-access-key"]
                val endpoint = props["endpoint"]

                val clientBuilder = S3AsyncClient.builder()
                    .region(Region.of(region))

                val presignerBuilder = S3Presigner.builder()
                    .region(Region.of(region))

                // Only set credentials if both are provided
                if (!accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()) {
                    val credentialsProvider = StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                    )
                    clientBuilder.credentialsProvider(credentialsProvider)
                    presignerBuilder.credentialsProvider(credentialsProvider)
                }

                if (!endpoint.isNullOrBlank()) {
                    val uri = URI.create(endpoint)
                    clientBuilder.endpointOverride(uri)
                    presignerBuilder.endpointOverride(uri)
                }

                if (type == "s2") {
                    S2Storage(clientBuilder.build(), bucket, presignerBuilder.build())
                } else {
                    S3Storage(clientBuilder.build(), bucket, presignerBuilder.build())
                }
            }
            else -> throw IllegalArgumentException("Unknown storage provider type: $type")
        }
    }
}

class InMemoryBucketRegistry(private val providers: Map<String, Storage>) : BucketRegistry {
    override fun getStorage(bucketName: String): Storage = providers[bucketName]
        ?: throw StorageServiceException("Bucket not found: $bucketName")
}
