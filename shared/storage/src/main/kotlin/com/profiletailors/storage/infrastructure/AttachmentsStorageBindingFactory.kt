package com.profiletailors.storage.infrastructure

import com.profiletailors.storage.domain.AttachmentsStorageBinding
import com.profiletailors.storage.domain.BucketRegistry

/**
 * Resolves the [AttachmentsStorageBinding] from a single source of truth:
 * the configured `platform.storage.providers.attachments.*` block.
 *
 * For local providers there is no real bucket — we keep the logical
 * [AttachmentsStorageBinding.providerName] as the bucket name to guarantee that
 * reads, uploads and previews all reference the same key prefix. For S3/R2 we
 * forward the configured physical bucket so callers that need it (e.g. AWS SDK
 * calls) keep working.
 */
object AttachmentsStorageBindingFactory {
    fun from(registry: BucketRegistry, properties: StorageProperties): AttachmentsStorageBinding {
        val providerName = properties.default
        val providerConfig = properties.providers[providerName]
        val storage = registry.getStorage(providerName)
        val physicalBucket = providerConfig?.bucket?.takeIf { it.isNotBlank() } ?: providerName
        return AttachmentsStorageBinding(
            providerName = providerName,
            bucketName = physicalBucket,
            storage = storage,
        )
    }
}
