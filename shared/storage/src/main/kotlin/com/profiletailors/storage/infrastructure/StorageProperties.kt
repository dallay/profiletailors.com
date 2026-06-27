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
    var providers: Map<String, ProviderConfig> = emptyMap(),
)

/**
 * Configuration for a single storage provider.
 * Supports local filesystem, S3, S2 (Ceph), and R2 (Cloudflare R2) storage backends.
 */
data class ProviderConfig(
    /**
     * Storage provider type: "local", "s3", "s2" (deprecated), or "r2"
     */
    val type: String,

    /**
     * Bucket name for S3/S2/R2 providers.
     * Required for S3/S2/R2, ignored for local.
     */
    val bucket: String? = null,

    /**
     * AWS region for S3/S2/R2 providers.
     * Default is us-east-1.
     * For R2, use "auto" as the region.
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
     * For R2, the endpoint is automatically constructed from accountId.
     */
    val endpoint: String? = null,

    /**
     * Cloudflare R2 account ID.
     * Required when type is "r2".
     * The account ID is used to construct the R2 endpoint:
     * https://{accountId}.r2.cloudflarestorage.com
     */
    val accountId: String? = null,

    /**
     * Access key for S3-compatible providers.
     * Required when type is "r2" (Cloudflare R2 has no implicit credentials chain).
     * YAML key: `access-key-id`.
     */
    val accessKeyId: String? = null,

    /**
     * Secret access key for S3-compatible providers.
     * Required when type is "r2" (Cloudflare R2 has no implicit credentials chain).
     * YAML key: `secret-access-key`.
     */
    val secretAccessKey: String? = null,

    /**
     * Timeout for S3/R2 operations in seconds.
     * Default is 30 seconds. Must be positive.
     */
    val timeoutSeconds: Long = 30,
) {
    init {
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive, got $timeoutSeconds" }
    }
}
