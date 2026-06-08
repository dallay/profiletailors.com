package com.profiletailors.storage.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "platform.storage")
class StorageProperties(
    var default: String = "local",
    
    /**
     * Map of storage provider configurations.
     * Key is the provider name, value is the configuration map.
     */
    @NestedConfigurationProperty
    var providers: Map<String, ProviderConfig> = emptyMap()
)

/**
 * Configuration for a single storage provider.
 * Supports local filesystem, S3, and S2 (Ceph) storage backends.
 */
data class ProviderConfig(
    /**
     * Storage provider type: "local", "s3", or "s2"
     */
    val type: String,
    
    /**
     * Bucket name for S3/S2 providers.
     * Required for S3/S2, ignored for local.
     */
    val bucket: String? = null,
    
    /**
     * AWS region for S3/S2 providers.
     * Default is us-east-1.
     */
    val region: String? = null,
    
    /**
     * Base path for local filesystem storage.
     * Default is system temporary directory.
     */
    val basePath: String? = null,
    
    /**
     * Custom endpoint for S3/S2 providers.
     * Useful for S2 (Ceph), MinIO, or other S3-compatible services.
     */
    val endpoint: String? = null
)
